package com.zyrotech.vsolrouter.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyrotech.vsolrouter.data.Prefs
import com.zyrotech.vsolrouter.data.SavedLogin
import com.zyrotech.vsolrouter.net.Band
import com.zyrotech.vsolrouter.net.BandResult
import com.zyrotech.vsolrouter.net.BlockedEntry
import com.zyrotech.vsolrouter.net.Device
import com.zyrotech.vsolrouter.net.RouterApi
import com.zyrotech.vsolrouter.net.Section
import com.zyrotech.vsolrouter.net.SessionExpiredException
import com.zyrotech.vsolrouter.net.VsolClient
import com.zyrotech.vsolrouter.net.WifiBandInfo
import com.zyrotech.vsolrouter.net.WifiPlan
import com.zyrotech.vsolrouter.net.WebFormRunner
import com.zyrotech.vsolrouter.net.SmartConnectState
import com.zyrotech.vsolrouter.data.RouterProfile
import com.zyrotech.vsolrouter.net.DeviceSchedule
import com.zyrotech.vsolrouter.net.GuestWifi
import com.zyrotech.vsolrouter.net.LoginException
import com.zyrotech.vsolrouter.net.SpeedLimit
import com.zyrotech.vsolrouter.net.UrlFilterState
import com.zyrotech.vsolrouter.work.Background
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var login by mutableStateOf(prefs.load(defaultGateway(app) ?: "192.168.1.1"))
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    var dashboard by mutableStateOf<List<Section>>(emptyList())
        private set
    var wifi by mutableStateOf<List<WifiBandInfo>>(emptyList())
        private set
    var devices by mutableStateOf<List<Device>>(emptyList())
        private set
    var smart by mutableStateOf(SmartConnectState.UNSUPPORTED)
        private set
    var wifiLog by mutableStateOf<List<String>>(emptyList())
        private set
    var blocked by mutableStateOf<List<BlockedEntry>>(emptyList())
        private set
    var blockNote by mutableStateOf<String?>(null)
        private set
    var lastWifiResults by mutableStateOf<List<BandResult>>(emptyList())
        private set

    // v0.6 features
    var guest by mutableStateOf(GuestWifi.UNSUPPORTED)
        private set
    /** null = the router has no traffic shaping (or it could not be read). */
    var speedLimits by mutableStateOf<List<SpeedLimit>?>(null)
        private set
    /** null = the router has no family-group schedules (or they could not be read). */
    var schedules by mutableStateOf<Map<String, DeviceSchedule>?>(null)
        private set
    var sites by mutableStateOf<UrlFilterState?>(null)
        private set
    var loginTimeout by mutableStateOf<Int?>(null)
        private set
    var maxLogins by mutableStateOf<Int?>(null)
        private set
    var notifyNew by mutableStateOf(prefs.notifyNewDevices)
        private set

    /** Saved routers (several can be kept and switched between). */
    var routers by mutableStateOf(prefs.routers())
        private set
    var activeId by mutableStateOf(prefs.activeId)
        private set
    /** Name used when the current login is saved; defaults to the detected model. */
    var profileName by mutableStateOf(prefs.active()?.name ?: "")

    /** Seconds left while waiting for the router to drop an old login (0 = not waiting). */
    var loginWait by mutableIntStateOf(0)
        private set
    var loginWaitReason by mutableStateOf<String?>(null)
        private set
    private var waitJob: Job? = null

    private var api: RouterApi? = null

    fun updateLogin(l: SavedLogin) { login = l }

    /** Router found on the network: its address and the model from its login page. */
    data class FoundRouter(val host: String, val model: String?)

    /** null = not searched yet; model null = nothing answered at that address. */
    var found by mutableStateOf<FoundRouter?>(null)
        private set
    var probing by mutableStateOf(false)
        private set
    /** Ask for username/password (first login, or the saved ones stopped working). */
    var askCredentials by mutableStateOf(false)

    /** A saved login exists for the router found on this Wi-Fi: a tap logs straight in with it. */
    val hasSavedLogin: Boolean get() = profileFor(found?.host) != null

    private fun profileFor(host: String?): RouterProfile? {
        if (host == null) return null
        val list = routers.filter { it.host == host }
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    init { probe() }

    /** Look for the router at the Wi-Fi gateway (or the saved address) and read its model. */
    fun probe() {
        if (probing) return
        probing = true
        val gw = defaultGateway(getApplication<Application>())
        val candidates = listOfNotNull(gw, login.host, "192.168.1.1").distinct()
        viewModelScope.launch {
            var hit: FoundRouter? = null
            for (h in candidates) {
                val model = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { VsolClient.probeModel(h) }
                if (model != null) { hit = FoundRouter(h, model); break }
            }
            found = hit ?: FoundRouter(gw ?: login.host, null)
            hit?.let { h ->
                val p = profileFor(h.host)
                login = p?.toLogin() ?: login.copy(host = h.host)
                profileName = p?.name ?: h.model ?: "My router"
            }
            probing = false
        }
    }

    /** Tap on the detected router card: log in with its saved login, or ask for one the first time. */
    fun tapRouter() {
        if (busy || loginWait > 0) return
        val p = profileFor(found?.host)
        if (p != null) {
            login = p.toLogin(); profileName = p.name; doLogin()
        } else {
            profileName = found?.model ?: "My router"
            askCredentials = true
        }
    }

    /** Tap on a saved router in the list. */
    fun tapSaved(p: RouterProfile) {
        if (busy || loginWait > 0) return
        login = p.toLogin(); profileName = p.name
        doLogin()
    }

    fun newLogin() {
        login = SavedLogin(found?.host ?: login.host, "admin", "", true)
        profileName = found?.model ?: "My router"
        askCredentials = true
    }

    fun removeSaved(p: RouterProfile) {
        prefs.remove(p.id)
        routers = prefs.routers(); activeId = prefs.activeId
    }

    fun doLogin() {
        if (busy) return
        cancelLoginWait()
        launchBusy {
            try {
                connect()
            } catch (e: LoginException) {
                if (e.busy) { askCredentials = false; startLoginWait(e.message) }
                else {
                    // Wrong or changed password: ask again with the saved values filled in.
                    if (e.message?.contains("password", true) == true) askCredentials = true
                    throw e
                }
            }
        }
    }

    private suspend fun connect() {
        val client = VsolClient(login.host)
        client.restoreSession(prefs.session)
        client.onSessionChanged = { prefs.session = it }
        client.login(login.username, login.password)
        val a = RouterApi(client, WebFormRunner(getApplication<Application>()))
        api = a
        // Remember it: next time a tap on the router is enough.
        val model = found?.takeIf { it.host == login.host }?.model ?: prefs.active()?.model
        val saved = prefs.upsert(
            RouterProfile(
                id = routers.firstOrNull { it.name.equals(profileName, true) }?.id ?: "",
                name = profileName.ifBlank { model ?: "My router" },
                host = login.host, username = login.username, password = login.password, model = model,
            ),
        )
        prefs.activeId = saved.id
        routers = prefs.routers(); activeId = saved.id
        askCredentials = false
        loggedIn = true
        // The app lowered the router's login timeout before a Wi-Fi change: put it back.
        val restore = prefs.restoreTimeout
        if (restore > 0) {
            try { a.setLoginTimeout(restore); prefs.restoreTimeout = 0 } catch (_: Exception) {}
        }
        dashboard = emptyList(); wifi = emptyList(); devices = emptyList(); blocked = emptyList()
        speedLimits = null; schedules = null; sites = null; guest = GuestWifi.UNSUPPORTED
        refreshDashboardInner()
    }

    /**
     * The router allows one login at a time and keeps an old one (from a browser, or from this phone
     * before it reconnected to Wi-Fi with a new address) until it times out. Retry quietly until then.
     */
    private fun startLoginWait(reason: String?) {
        waitJob?.cancel()
        loginWaitReason = reason
        val deadline = System.currentTimeMillis() + 6 * 60_000L
        waitJob = viewModelScope.launch {
            while (true) {
                for (i in 0 until 15) {
                    loginWait = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(1)
                    delay(1000)
                }
                if (System.currentTimeMillis() > deadline) {
                    loginWait = 0
                    error = (reason ?: "The router is busy.") + "\n\nLog out of the router's web page in the browser, " +
                        "or in Tools allow 2 or more logins, then try again."
                    break
                }
                try {
                    busy = true
                    connect()
                    loginWait = 0
                    break
                } catch (e: LoginException) {
                    if (!e.busy) { loginWait = 0; error = e.message; break }
                } catch (e: Exception) {
                    // Not reachable yet (Wi-Fi still reconnecting): keep waiting.
                } finally {
                    busy = false
                }
            }
        }
    }

    fun cancelLoginWait() {
        waitJob?.cancel(); waitJob = null
        loginWait = 0
    }

    fun logout() {
        val a = api
        api = null
        loggedIn = false
        releaseJob?.cancel()
        dashboard = emptyList(); wifi = emptyList(); devices = emptyList(); blocked = emptyList()
        viewModelScope.launch { try { a?.client?.logout() } catch (_: Exception) {} }
    }

    private var releaseJob: kotlinx.coroutines.Job? = null

    /**
     * The router allows one admin login at a time and keeps it for ~5 minutes after the last use.
     * When the app goes to the background, give the login back after a short grace period so the
     * router's web panel (or the app itself later) can log in. The next request logs in again.
     */
    fun onAppBackground() {
        Background.appVisible = false
        val a = api ?: return
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            kotlinx.coroutines.delay(20_000)
            try { a.client.release() } catch (_: Exception) {}
        }
    }

    fun onAppForeground() {
        Background.appVisible = true
        releaseJob?.cancel(); releaseJob = null
    }

    fun refreshDashboard() = launchBusy { refreshDashboardInner() }

    /** Home tab: status first, then devices and Wi-Fi for the summary tiles (those may fail quietly). */
    fun refreshHome() = launchBusy {
        refreshDashboardInner()
        try { loadDevices() } catch (e: SessionExpiredException) { throw e } catch (e: LoginException) { throw e } catch (_: Exception) {}
        try { loadWifi() } catch (e: SessionExpiredException) { throw e } catch (e: LoginException) { throw e } catch (_: Exception) {}
    }
    private suspend fun refreshDashboardInner() {
        dashboard = requireApi().dashboard()
        saveSnapshot()
    }

    private fun saveSnapshot() {
        val ctx = getApplication<Application>()
        val model = found?.model ?: routers.firstOrNull { it.id == activeId }?.model ?: "VSOL Router"
        val old = prefs.snapshot
        Background.saveSnapshot(
            ctx, model,
            Background.rxFrom(dashboard) ?: old?.rx,
            if (devices.isNotEmpty()) devices.size else old?.devices ?: 0,
        )
    }

    fun refreshWifi() = launchBusy { loadWifi() }

    private suspend fun loadWifi() {
        val a = requireApi()
        smart = a.smartConnect()
        wifi = a.wifiInfo()
        guest = try { a.guestWifi() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { GuestWifi.UNSUPPORTED }
    }

    fun saveWifi(plan: WifiPlan) = launchBusy {
        val a = requireApi()
        val band = phoneBand(getApplication<Application>())
        if (band != null) {
            // The phone will drop off Wi-Fi and may come back with a new address. The router would then
            // keep the old login for its full timeout and refuse this phone. Shorten it to 1 minute for now;
            // it is put back after the next login.
            try {
                val t = a.loginTimeout()
                if (t != null && t > 1) { if (prefs.restoreTimeout <= 0) prefs.restoreTimeout = t; a.setLoginTimeout(1) }
            } catch (_: Exception) {}
        }
        // What this phone must use afterwards (its band's new name + password), to save it on the phone.
        val cur = wifi.firstOrNull { it.band == band }
        val target: Pair<String, String>? = if (band == null || cur == null) null else {
            val ssid = (if (plan.smartConnect) plan.sharedName else plan.names[band])?.ifBlank { null } ?: cur.ssid
            val pass = (if (plan.smartConnect) plan.sharedPassword else plan.passwords[band])?.ifEmpty { null }
                ?: (if (plan.smartConnect) smart.password.ifBlank { cur.password } else cur.password)
            if ((ssid != cur.ssid || pass != cur.password) && RouterApi.isValidPsk(pass)) ssid to pass else null
        }
        val log = try {
            a.saveWifi(plan, band)
        } catch (e: java.io.IOException) {
            listOf("The connection dropped while saving (Wi-Fi restarted). Check the result after reconnecting.")
        }
        wifiLog = log
        if (target != null) {
            wifiJoin = WifiJoin(target.first, target.second, log.joinToString("\n"))
        } else {
            info = log.joinToString("\n") + "\n\nDevices on Wi-Fi must reconnect with the new name/password. " +
                "If the app is logged out, reconnect this phone and tap the router: it retries until the router lets it in."
        }
        try { loadWifi() } catch (_: Exception) {}
    }

    /** New Wi-Fi details to save on this phone after a change (shown as a prompt). */
    data class WifiJoin(val ssid: String, val password: String, val log: String)
    var wifiJoin by mutableStateOf<WifiJoin?>(null)

    fun refreshGuest() = launchBusy {
        guest = try { requireApi().guestWifi() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { GuestWifi.UNSUPPORTED }
    }

    fun saveGuest(enable: Boolean, ssid: String, password: String) = launchBusy {
        val log = requireApi().setGuestWifi(enable, ssid, password)
        info = log.joinToString("\n")
        guest = try { requireApi().guestWifi() } catch (_: Exception) { guest }
    }

    fun refreshDevices() = launchBusy { loadDevices() }

    private suspend fun loadDevices() {
        val a = requireApi()
        devices = a.devices()
        prefs.activeId?.let { id ->
            Background.checkNewDevices(getApplication<Application>(), id, routers.firstOrNull { it.id == id }?.name ?: "router", devices)
        }
        saveSnapshot()
        try {
            val st = a.macFilter()
            blocked = st.entries
            blockNote = when {
                st.enabled && st.whitelist -> "Router MAC filter is in Whitelist mode. Blocking from the app is disabled."
                !st.enabled && st.entries.isNotEmpty() -> "MAC filter is off, so the list below is not active."
                else -> null
            }
        } catch (e: SessionExpiredException) {
            throw e
        } catch (_: Exception) {
            blocked = emptyList()
            blockNote = "Could not read the router's block list."
        }
        speedLimits = try { a.speedLimits() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }
        schedules = try { a.schedules() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }
    }

    fun block(d: Device) = launchBusy {
        val me = phoneIp(getApplication<Application>())
        if (me != null && d.ip == me) throw IllegalStateException("This is the phone running the app. It cannot block itself.")
        requireApi().block(d.mac, d.name)
        info = "${d.name ?: d.mac} is blocked. It can no longer use the internet through this router."
        loadDevices()
    }

    fun unblock(e: BlockedEntry) = launchBusy {
        requireApi().unblock(e.mac)
        info = "${e.name ?: e.mac} is unblocked."
        loadDevices()
    }

    /** Speed limit in kb/s (0 = none that way). */
    fun setSpeedLimit(d: Device, downKbps: Int, upKbps: Int) = launchBusy {
        val ip = d.ip ?: throw IllegalStateException("This device has no IP address right now.")
        requireApi().setSpeedLimit(ip, downKbps, upKbps)
        info = if (downKbps <= 0 && upKbps <= 0) "Speed limit removed for ${d.name ?: ip}."
            else "Speed limit saved for ${d.name ?: ip}."
        speedLimits = try { requireApi().speedLimits() } catch (_: Exception) { speedLimits }
    }

    fun setSchedule(d: Device, days: Set<Int>, fromMin: Int, toMin: Int) = launchBusy {
        requireApi().setSchedule(d.mac, days, fromMin, toMin)
        info = "Internet schedule saved for ${d.name ?: d.mac}."
        schedules = try { requireApi().schedules() } catch (_: Exception) { schedules }
    }

    fun removeSchedule(d: Device) = launchBusy {
        requireApi().removeSchedule(d.mac)
        info = "Internet schedule removed for ${d.name ?: d.mac}."
        schedules = try { requireApi().schedules() } catch (_: Exception) { schedules }
    }

    fun loadTools() = launchBusy {
        val a = requireApi()
        guest = try { a.guestWifi() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { GuestWifi.UNSUPPORTED }
        sites = try { a.urlFilter() } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }
        loginTimeout = a.loginTimeout()
        maxLogins = a.maxLogins()
    }

    fun blockSite(url: String) = launchBusy {
        requireApi().blockSite(url)
        sites = requireApi().urlFilter()
    }

    fun unblockSite(url: String) = launchBusy {
        requireApi().unblockSite(url)
        sites = requireApi().urlFilter()
    }

    fun saveLoginTimeout(min: Int) = launchBusy {
        requireApi().setLoginTimeout(min)
        prefs.restoreTimeout = 0
        loginTimeout = requireApi().loginTimeout()
        info = "Router login timeout is now ${loginTimeout ?: min} minute(s)."
    }

    fun saveMaxLogins(n: Int) = launchBusy {
        requireApi().setMaxLogins(n)
        info = "Saved. The router reboots to apply this (about 1-2 minutes), then sign in again."
        loggedIn = false
        api = null
    }

    fun updateNotifyNew(on: Boolean) {
        prefs.notifyNewDevices = on
        notifyNew = on
        Background.schedule(getApplication<Application>())
    }

    /**
     * One button, both bands. The band the phone is NOT using goes first, so the phone stays
     * connected long enough to send the second request.
     */
    fun setWifiPasswords(plan: Map<Band, String>) = launchBusy {
        val current = phoneBand(getApplication<Application>())
        val ordered = plan.keys.sortedBy { if (it == current) 1 else 0 }
        val results = ordered.flatMap { band -> requireApi().setWifiPassword(plan.getValue(band), listOf(band)) }
        lastWifiResults = results
        val failed = results.filter { !it.ok }
        info = if (failed.isEmpty()) {
            "Password changed on ${results.joinToString(" + ") { it.band.label }}. " +
                "Reconnect your devices with the new password."
        } else null
        if (failed.isNotEmpty()) {
            error = failed.joinToString("\n") { "${it.band.label}: ${it.message}" } +
                "\nReconnect to Wi-Fi, then tap Save again to retry."
        }
        try { wifi = requireApi().wifiInfo() } catch (_: Exception) {}
    }

    fun reboot() = launchBusy {
        requireApi().reboot()
        info = "Router is rebooting. It takes about 1-2 minutes."
        loggedIn = false
        api = null
    }

    fun changeAdminPassword(old: String, new: String) = launchBusy {
        val a = requireApi()
        a.changeAdminPassword(old, new)
        a.client.updatePassword(new)
        login = login.copy(password = new)
        routers.firstOrNull { it.id == activeId }?.let { prefs.upsert(it.copy(password = new)) }
        routers = prefs.routers()
        info = "Admin password changed. The app saved the new one."
    }

    private fun requireApi(): RouterApi = api ?: throw SessionExpiredException()

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: SessionExpiredException) {
                loggedIn = false
                api = null
                error = e.message
            } catch (e: LoginException) {
                if (e.busy && loginWait == 0) {
                    // Logged out underneath us (e.g. the phone reconnected with a new address): wait and sign in again.
                    loggedIn = false
                    api = null
                    startLoginWait(e.message)
                } else error = e.message
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        val a = api ?: return
        // Free the router's single admin login slot.
        Thread { try { kotlinx.coroutines.runBlocking { a.client.release() } } catch (_: Exception) {} }.start()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun defaultGateway(ctx: Context): String? = try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gw = wm.dhcpInfo?.gateway ?: 0
            if (gw == 0) null else
                "${gw and 0xff}.${gw shr 8 and 0xff}.${gw shr 16 and 0xff}.${gw shr 24 and 0xff}"
        } catch (_: Exception) { null }

        /** The phone's own IPv4 address on the active network, to stop it blocking itself. */
        fun phoneIp(ctx: Context): String? = try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            fun v4(n: android.net.Network?) = n?.let { cm.getLinkProperties(it) }?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is java.net.Inet4Address }?.hostAddress
            // Prefer the Wi-Fi network: the active one can be mobile data when Wi-Fi has no internet.
            @Suppress("DEPRECATION")
            val wifi = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            v4(wifi) ?: v4(cm.activeNetwork)
        } catch (_: Exception) { null }

        /** Band of the phone's current Wi-Fi connection, or null when unknown / not on Wi-Fi. */
        fun phoneBand(ctx: Context): Band? = try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps?.transportInfo as? WifiInfo)?.frequency
            } else {
                @Suppress("DEPRECATION")
                (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.frequency
            }
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true || freq == null || freq <= 0) null
            else if (freq > 4900) Band.GHZ_5 else Band.GHZ_2_4
        } catch (_: Exception) { null }
    }
}
