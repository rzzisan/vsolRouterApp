package com.zyrotech.vsolrouter.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** [busy] = the router is still holding another login; trying again in a little while can work. */
class LoginException(message: String, val busy: Boolean = false) : Exception(message)
class SessionExpiredException : Exception("Session expired, please log in again")
class RouterException(message: String) : Exception(message)

/**
 * Talks to the Realtek "Boa" web panel used by VSOL ONUs (e.g. V2802DAC).
 *
 * The panel has no JSON API: pages are server-rendered .asp files and settings are
 * changed by POSTing HTML forms to /boaform/... . Boa injects a hidden `csrftoken`
 * into every rendered form, so every write first loads its page and re-sends that form.
 *
 * The session is a `sessionid` cookie. Some builds mark it `Secure` even over plain
 * HTTP, which OkHttp's CookieJar would refuse to send back, so the cookie is handled
 * by hand in a network interceptor.
 *
 * Wi-Fi pages work on a "current band" kept in the router session
 * (switched with /boaform/formWlanRedirect), so all calls are serialised with a mutex.
 */
class VsolClient(host: String) {

    val baseUrl: String = normalise(host)

    /** Called whenever the router hands out (or drops) a session, so it can be kept across app restarts. */
    @Volatile var onSessionChanged: ((String?) -> Unit)? = null

    @Volatile var sessionCookie: String? = null
        private set(v) {
            if (field != v) { field = v; onSessionChanged?.invoke(v) }
        }
    private var username: String? = null
    private var password: String? = null
    private val lock = Mutex()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .addNetworkInterceptor(Interceptor { chain ->
            val reqBuilder = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
            sessionCookie?.let { reqBuilder.header("Cookie", "sessionid=$it") }
            val resp = chain.proceed(reqBuilder.build())
            for (h in resp.headers("Set-Cookie")) {
                val m = Regex("""(?i)sessionid=([^;\s]*)""").find(h)
                if (m != null) sessionCookie = m.groupValues[1].ifEmpty { null }
            }
            resp
        })
        .build()

    // ---------------------------------------------------------------- session

    suspend fun login(user: String, pass: String) = io {
        username = user
        password = pass
        doLogin()
    }

    /**
     * Pick up a session saved from an earlier run. If the app was closed without logging out,
     * the router still holds that login for this phone's IP and refuses a new one
     * ("you have logined! please logout at first"), so it is logged out before the next login.
     */
    fun restoreSession(cookie: String?) { if (sessionCookie == null) sessionCookie = cookie }

    /** Log out and forget the credentials (the user pressed Log out). */
    suspend fun logout() = io {
        doLogout()
        username = null
        password = null
    }

    /** The admin password was changed: use the new one for automatic re-logins. */
    fun updatePassword(pass: String) { password = pass }

    /**
     * POST to one of the newer AJAX endpoints (family groups etc.). Their forms are built in
     * JavaScript, which fetches the token from /boaform/csrfMask, so do the same.
     */
    suspend fun postAjax(path: String, fields: List<Pair<String, String>>): String = io {
        getLocked(CHECK_PAGE)
        val token = try { rawGet("/boaform/csrfMask").trim() } catch (_: Exception) { "" }
        val all = if (token.isNotEmpty() && token.length < 200 && !token.contains('<')) fields + ("csrftoken" to token) else fields
        val body = rawPost(path, all)
        Parsers.errorMessage(body)?.let { throw RouterException(it) }
        body
    }

    /** Free the router's login slot but keep the credentials, so the next request logs in again. */
    suspend fun release() = io { doLogout() }

    /**
     * The panel's Logout is a POST of the (otherwise empty) formLogout form, which Boa fills with a
     * csrftoken. Posting without that token is ignored, which is why the old logout left the
     * router thinking this phone was still logged in. Returns true when the router confirms.
     */
    private fun doLogout(): Boolean {
        var sent = false
        for (page in LOGOUT_PAGES) {
            val html = try { rawGet(page) } catch (_: Exception) { continue }
            if (Parsers.isLoginPage(html)) { sessionCookie = null; return true } // nothing to log out
            val form = Parsers.form(html, "formLogout") ?: continue
            try { rawPost(form.action, form.fields.toList()); sent = true } catch (_: Exception) {}
            break
        }
        if (!sent) {
            // Fall back to a bare POST (older builds accept it).
            try { rawPost("/boaform/admin/formLogout", emptyList()) } catch (_: Exception) {}
        }
        val out = try { Parsers.isLoginPage(rawGet(CHECK_PAGE)) } catch (_: Exception) { false }
        sessionCookie = null
        return out
    }

    private fun doLogin() {
        val user = username ?: throw LoginException("No username")
        val pass = password ?: throw LoginException("No password")
        // A session left over from before (app killed, Wi-Fi dropped) still blocks this phone: free it.
        if (sessionCookie != null) doLogout()
        var err = tryLogin(user, pass)
        if (err != null && err.contains("you have logined", true)) {
            // The router still lists this phone as logged in. Log that session out and try once more.
            doLogout()
            err = tryLogin(user, pass)
        }
        if (err != null) {
            val busy = err.contains("another user", true) || err.contains("you have logined", true)
            throw LoginException(
                when {
                    err.contains("another user", true) ->
                        "The router is still holding another login (a browser, or this phone from before it " +
                            "reconnected to Wi-Fi). Only one login is allowed at a time."
                    err.contains("you have logined", true) ->
                        "The router still holds an old login from this phone."
                    err.contains("thrice", true) || err.contains("three", true) ->
                        "Wrong username or password 3 times. The router locks login for about a minute."
                    else -> err
                },
                busy,
            )
        }
        // Confirm by opening a protected page.
        val check = rawGet(CHECK_PAGE)
        if (Parsers.isLoginPage(check)) {
            throw LoginException("Login failed. Check the username and password.")
        }
    }

    /** One login attempt. Returns the router's error text, or null on success. */
    private fun tryLogin(user: String, pass: String): String? {
        sessionCookie = null
        val loginPage = try {
            rawGet("/admin/login.asp")
        } catch (e: IOException) {
            throw LoginException("Cannot reach the router at $baseUrl. Is this phone on the router's Wi-Fi?")
        }
        val fields = mutableListOf(
            "username" to user,
            "psd" to pass,
            "sec_lang" to "0",
            "loginSelinit" to "",
            "ismobile" to "",
        )
        Parsers.csrfToken(loginPage)?.let { fields += "csrftoken" to it }
        val body = rawPost("/boaform/admin/formLogin", fields)
        return Parsers.errorMessage(body)
    }

    // ---------------------------------------------------------------- requests

    /** GET a page, logging in again once if the session has expired. */
    suspend fun get(path: String): String = io { getLocked(path) }

    private fun getLocked(path: String): String {
        val body = rawGet(path)
        if (!Parsers.isLoginPage(body)) return body
        if (username == null) throw SessionExpiredException()
        doLogin()
        val retry = rawGet(path)
        if (Parsers.isLoginPage(retry)) throw SessionExpiredException()
        return retry
    }

    /**
     * Load [pagePath], take the form whose action contains [formAction] with all its current
     * values (including csrftoken), apply [edit], and POST it. Returns the response body.
     */
    suspend fun submitPageForm(
        pagePath: String,
        formAction: String,
        edit: (page: String, fields: FormFields) -> Unit = { _, _ -> },
    ): String = io {
        val page = getLocked(pagePath)
        val form = Parsers.form(page, formAction)
            ?: throw RouterException("Form $formAction not found on $pagePath")
        edit(page, form.fields)
        val body = rawPost(form.action, form.fields.toList())
        Parsers.errorMessage(body)?.let { throw RouterException(it) }
        body
    }

    /** Run several steps under one lock (needed for band-switched Wi-Fi pages). */
    suspend fun <T> session(block: Session.() -> T): T = io { Session().block() }

    /**
     * Hold the router session for a multi-step job that also uses other channels (the WebView).
     * [check] runs first on IO to make sure the session is alive (re-logging in if needed).
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock {
        withContext(Dispatchers.IO) { getLocked(CHECK_PAGE) }
        block()
    }

    /** Like [get] but for use inside [exclusive] (the lock is already held). */
    suspend fun getUnlocked(path: String): String = withContext(Dispatchers.IO) { getLocked(path) }

    private suspend fun <T> io(block: () -> T): T =
        lock.withLock { withContext(Dispatchers.IO) { block() } }

    inner class Session {
        fun get(path: String): String = getLocked(path)
        fun post(path: String, fields: List<Pair<String, String>>): String {
            val body = rawPost(path, fields)
            Parsers.errorMessage(body)?.let { throw RouterException(it) }
            return body
        }
    }

    // ---------------------------------------------------------------- raw http

    private fun rawGet(path: String): String {
        val req = Request.Builder().url(baseUrl + path).get().build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw SessionExpiredException()
            if (!resp.isSuccessful) throw RouterException("HTTP ${resp.code} for $path")
            return resp.body?.string().orEmpty()
        }
    }

    private fun rawPost(path: String, fields: List<Pair<String, String>>): String {
        val form = FormBody.Builder()
        for ((k, v) in fields) form.add(k, v)
        val req = Request.Builder()
            .url(baseUrl + path)
            .header("Referer", "$baseUrl/")
            .post(form.build())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code !in 300..399) {
                throw RouterException("HTTP ${resp.code} for $path")
            }
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        /** Same UA for OkHttp and the WebView so the router sees one client. */
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) VsolRouterApp/0.6"
        private const val CHECK_PAGE = "/status_device_basic_info.asp"
        private val LOGOUT_PAGES = listOf("/top.asp", "/mobile/index_mobile.asp")

        /**
         * Find the router's model without logging in: the login page sets
         * `document.title = "<model>"`. Returns null when nothing answers at [host].
         * Blocking; call off the main thread.
         */
        fun probeModel(host: String): String? {
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(normalise(host) + "/admin/login.asp")
                .header("User-Agent", USER_AGENT).get().build()
            return try {
                client.newCall(req).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful && html.isEmpty()) return null
                    Regex("""document\.title\s*=\s*"([^"]*)"""").find(html)?.groupValues?.get(1)?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: Regex("""(?is)<title>(.*?)</title>""").find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "VSOL Router"
                }
            } catch (_: Exception) { null }
        }

        fun normalise(host: String): String {
            var h = host.trim().trimEnd('/')
            if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
            return h
        }
    }
}

/** Ordered, multi-valued form field list with set/remove helpers. */
class FormFields(initial: List<Pair<String, String>> = emptyList()) {
    private val items = initial.toMutableList()
    fun set(name: String, value: String) {
        items.removeAll { it.first == name }
        items += name to value
    }
    fun remove(name: String) { items.removeAll { it.first == name } }
    operator fun get(name: String): String? = items.firstOrNull { it.first == name }?.second
    fun toList(): List<Pair<String, String>> = items.toList()
}
