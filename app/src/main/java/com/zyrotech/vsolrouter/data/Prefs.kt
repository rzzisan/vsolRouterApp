package com.zyrotech.vsolrouter.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

data class SavedLogin(val host: String, val username: String, val password: String, val remember: Boolean)

/** A router the user has signed in to: [name] is what the list shows (the model by default). */
data class RouterProfile(
    val id: String,
    val name: String,
    val host: String,
    val username: String,
    val password: String,
    val model: String?,
) {
    fun toLogin() = SavedLogin(host, username, password, true)
}

/** Last numbers read from the router, for the home-screen widget. */
data class Snapshot(val model: String, val rx: String?, val devices: Int, val time: Long)

/** Saved routers and logins, with passwords encrypted by an Android Keystore key. */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "router_login",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        // Keystore unavailable/corrupt: fall back to not remembering the password.
        context.getSharedPreferences("router_login_plain", Context.MODE_PRIVATE)
    }

    init { migrate() }

    /** v0.5 kept one login; turn it into the first saved router. */
    private fun migrate() {
        if (prefs.contains("routers")) return
        val pass = prefs.getString("pass", null)
        val user = prefs.getString("user", null)
        val host = prefs.getString("host", null)
        val list = mutableListOf<RouterProfile>()
        if (!pass.isNullOrEmpty() && !user.isNullOrEmpty() && host != null) {
            val model = prefs.getString("model", null)
            list += RouterProfile("r1", model ?: "My router", host, user, pass, model)
            prefs.edit().putString("active", "r1").apply()
        }
        writeRouters(list)
    }

    // ---------------------------------------------------------------- saved routers

    fun routers(): List<RouterProfile> = try {
        val a = JSONArray(prefs.getString("routers", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            RouterProfile(
                o.getString("id"), o.optString("name"), o.optString("host"), o.optString("user"),
                o.optString("pass"), o.optString("model").ifEmpty { null },
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun writeRouters(list: List<RouterProfile>) {
        val a = JSONArray()
        for (r in list) a.put(
            JSONObject().put("id", r.id).put("name", r.name).put("host", r.host).put("user", r.username)
                .put("pass", r.password).put("model", r.model ?: ""),
        )
        prefs.edit().putString("routers", a.toString()).apply()
    }

    /** Add or update a router; the same name (or same id) replaces the old entry. Returns the saved profile. */
    fun upsert(p: RouterProfile): RouterProfile {
        val list = routers().toMutableList()
        val i = list.indexOfFirst { it.id == p.id || it.name.equals(p.name, true) }
        val saved = if (i >= 0) p.copy(id = list[i].id).also { list[i] = it }
            else p.copy(id = "r" + System.currentTimeMillis()).also { list += it }
        writeRouters(list)
        return saved
    }

    fun remove(id: String) {
        writeRouters(routers().filterNot { it.id == id })
        if (activeId == id) activeId = null
    }

    var activeId: String?
        get() = prefs.getString("active", null)
        set(v) { prefs.edit().putString("active", v).apply() }

    fun active(): RouterProfile? = routers().let { l -> l.firstOrNull { it.id == activeId } ?: l.firstOrNull() }

    fun load(defaultHost: String): SavedLogin =
        active()?.toLogin() ?: SavedLogin(defaultHost, "admin", "", true)

    fun hasLogin(): Boolean = routers().isNotEmpty()

    var model: String?
        get() = active()?.model
        set(v) { active()?.let { upsert(it.copy(model = v)) } }

    /** The router session id from the last run, so a session left open can be logged out. */
    var session: String?
        get() = prefs.getString("session", null)
        set(v) { prefs.edit().putString("session", v).apply() }

    /** Router login timeout to put back after the app lowered it for a Wi-Fi change (0 = none). */
    var restoreTimeout: Int
        get() = prefs.getInt("restoreTimeout", 0)
        set(v) { prefs.edit().putInt("restoreTimeout", v).apply() }

    // ---------------------------------------------------------------- new-device alerts + widget

    var notifyNewDevices: Boolean
        get() = prefs.getBoolean("notifyNew", false)
        set(v) { prefs.edit().putBoolean("notifyNew", v).apply() }

    /** MACs already seen on the active router (so only really new devices are announced). */
    fun knownMacs(routerId: String): Set<String> = prefs.getStringSet("known_$routerId", emptySet()) ?: emptySet()
    fun setKnownMacs(routerId: String, macs: Set<String>) { prefs.edit().putStringSet("known_$routerId", macs.toSet()).apply() }

    var snapshot: Snapshot?
        get() {
            val s = prefs.getString("snapshot", null) ?: return null
            return try {
                val o = JSONObject(s)
                Snapshot(o.optString("model"), o.optString("rx").ifEmpty { null }, o.optInt("devices"), o.optLong("time"))
            } catch (_: Exception) { null }
        }
        set(v) {
            prefs.edit().putString("snapshot", v?.let {
                JSONObject().put("model", it.model).put("rx", it.rx ?: "").put("devices", it.devices).put("time", it.time).toString()
            }).apply()
        }
}
