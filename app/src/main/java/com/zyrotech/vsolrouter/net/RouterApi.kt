package com.zyrotech.vsolrouter.net

/** On this firmware wlan_idx 0 is the 5 GHz radio and 1 is 2.4 GHz (from the mobile menu). */
enum class Band(val idx: Int, val label: String) {
    GHZ_5(0, "5 GHz"),
    GHZ_2_4(1, "2.4 GHz"),
}

data class WifiBandInfo(
    val band: Band,
    val ssid: String,
    val enabled: Boolean,
    val password: String,
    val securityMethod: Int,
)

data class Device(
    val mac: String,
    val ip: String?,
    val name: String?,
    val band: Band?,
    val rssi: Int?,
)

data class Section(val title: String, val rows: List<Pair<String, String>>)

data class BlockedEntry(val mac: String, val name: String?, val record: Map<String, String>)

data class MacFilterState(val enabled: Boolean, val whitelist: Boolean, val entries: List<BlockedEntry>)

data class SmartConnectState(val supported: Boolean, val enabled: Boolean, val ssid: String, val password: String) {
    companion object { val UNSUPPORTED = SmartConnectState(false, false, "", "") }
}

/** What the Wi-Fi screen asks for. Null/blank values mean "keep current". */
data class WifiPlan(
    val smartConnect: Boolean,
    val sharedName: String? = null,
    val sharedPassword: String? = null,
    val names: Map<Band, String> = emptyMap(),
    val passwords: Map<Band, String> = emptyMap(),
)

data class BandResult(val band: Band, val ok: Boolean, val message: String)

data class GuestWifi(val supported: Boolean, val enabled: Boolean, val ssid: String, val password: String) {
    companion object { val UNSUPPORTED = GuestWifi(false, false, "", "") }
}

/** One traffic-shaping rule for a device IP; [download] false = upload. */
data class SpeedLimit(val id: String, val ip: String, val download: Boolean, val kbps: Int)

data class UrlFilterState(val enabled: Boolean, val sites: List<String>)

/** [day] 0 = Monday … 6 = Sunday; times in seconds after midnight. */
data class ScheduleRule(val day: Int, val onSec: Int, val offSec: Int)

data class DeviceSchedule(val index: String, val name: String, val rules: List<ScheduleRule>)

/** High-level router features built on [VsolClient]. */
class RouterApi(val client: VsolClient, private val web: WebFormRunner) {

    private fun bandPage(page: String, band: Band) =
        "/boaform/formWlanRedirect?redirect-url=$page&wlan_idx=${band.idx}"

    // ---------------------------------------------------------------- status

    suspend fun dashboard(): List<Section> {
        val sections = mutableListOf<Section>()
        sections += Section("Device", safeKv("/status_device_basic_info.asp"))
        var pon = safeKv("/status_gpon.asp")
        if (pon.isEmpty()) pon = safeKv("/status_epon.asp")
        sections += Section("Optical (PON)", pon.filter { (k, _) ->
            listOf("power", "temperature", "voltage", "bias", "status", "state", "alarm", "onu")
                .any { k.contains(it, ignoreCase = true) }
        }.ifEmpty { pon })
        sections += Section("Internet (WAN)", wanRows())
        return sections.filter { it.rows.isNotEmpty() }
    }

    private suspend fun safeKv(path: String): List<Pair<String, String>> =
        try { Parsers.keyValues(client.get(path)) } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { emptyList() }

    private suspend fun wanRows(): List<Pair<String, String>> {
        val html = try { client.get("/status_net_connet_info.asp") } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { return emptyList() }
        val records = Parsers.itNr(html)
        if (records.isNotEmpty()) {
            val rows = mutableListOf<Pair<String, String>>()
            records.forEachIndexed { i, rec ->
                val r = rec.filterKeys { !it.startsWith("__") }
                val name = r.entries.firstOrNull { it.key.contains("name", true) }?.value ?: "WAN ${i + 1}"
                for ((k, v) in r) if (v.isNotBlank() && !k.startsWith("__") && !k.contains("name", true)) rows += "$name · $k" to v
            }
            return rows
        }
        val kv = Parsers.keyValues(html)
        if (kv.isNotEmpty()) return kv
        // Fall back to the first data table: header row + each data row joined.
        val d = Parsers.doc(html)
        val headers = d.select("tr").firstOrNull()?.select("th,td")?.map { it.text().trim() } ?: emptyList()
        return d.select("tr").drop(1).flatMapIndexed { i, tr ->
            tr.select("td").mapIndexedNotNull { c, td ->
                val v = td.text().trim()
                if (v.isEmpty()) null else "${headers.getOrNull(c) ?: "Col $c"} (${i + 1})" to v
            }
        }
    }

    // ---------------------------------------------------------------- wifi

    suspend fun wifiInfo(): List<WifiBandInfo> = Band.values().mapNotNull { band ->
        try {
            val html = client.get(bandPage("/wlwpa.asp", band))
            val info = parseWifi(html, band)
            val basic = try { client.get(bandPage("/wlbasic.asp", band)) } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }
            val name = basic?.let { ssidFromBasic(it) }
            if (name.isNullOrBlank()) info else info.copy(ssid = name)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun ssidFromBasic(html: String): String? =
        Parsers.doc(html).select("form[name=wlanSetup] input[name=ssid], input[name=ssid]").firstOrNull()?.attr("value")

    private fun parseWifi(html: String, band: Band): WifiBandInfo {
        val ssid = Parsers.selectOptions(html, "wpaSSID").firstOrNull()?.second
            ?.substringAfter("SSID:", "")?.ifBlank { null }
            ?: Parsers.selectOptions(html, "wpaSSID").firstOrNull()?.second ?: "?"
        val disabled = Parsers.form(html, "formWlEncrypt")?.fields?.get("wlanDisabled")
        return WifiBandInfo(
            band = band,
            ssid = ssid.trim(),
            enabled = !disabled.equals("ON", ignoreCase = true),
            password = Parsers.jsArray(html, "_wpaPSK")[0].orEmpty(),
            securityMethod = Parsers.jsArray(html, "_encrypt")[0]?.toIntOrNull() ?: -1,
        )
    }

    /**
     * Change the WPA passphrase of the main SSID on each band in [bands], in order.
     * Each band is a separate POST to /boaform/admin/formWlEncrypt with that page's own csrftoken.
     * Stops nothing on failure: every band reports its own result.
     */
    suspend fun setWifiPassword(newPassword: String, bands: List<Band>): List<BandResult> {
        require(isValidPsk(newPassword)) { "Password must be 8-63 characters" }
        val results = mutableListOf<BandResult>()
        for (band in bands) {
            results += try {
                client.session {
                    val page = get(bandPage("/wlwpa.asp", band))
                    val form = Parsers.form(page, "formWlEncrypt")
                        ?: throw RouterException("Wi-Fi security form not found")
                    fillSecurityFields(page, form.fields, newPassword)
                    post(form.action, form.fields.toList())
                }
                // Verify (best effort: the phone may have just been disconnected).
                val ok = try {
                    val after = client.get(bandPage("/wlwpa.asp", band))
                    Parsers.jsArray(after, "_wpaPSK")[0]?.let { it == newPassword }
                } catch (_: Exception) { null }
                when (ok) {
                    false -> BandResult(band, false, "Router did not keep the new password")
                    else -> BandResult(band, true, if (ok == true) "Changed" else "Sent (could not re-check)")
                }
            } catch (e: Exception) {
                BandResult(band, false, e.message ?: e.javaClass.simpleName)
            }
        }
        return results
    }

    /** Rebuild what the page's JS (SSIDSelected/postSecurity) would put in the form for SSID [index] (0 = main). */
    private fun fillSecurityFields(page: String, f: FormFields, psk: String, index: Int = 0) {
        fun arr(name: String) = Parsers.jsArray(page, name)[index]
        val encrypt = arr("_encrypt")?.toIntOrNull() ?: 4
        // 0 = open, 1 = WEP: switch to WPA2-PSK (4) so a passphrase is meaningful.
        val method = if (encrypt == 2 || encrypt == 4 || encrypt == 6) encrypt else 4
        val opt = Parsers.selectOptions(page, "wpaSSID").getOrNull(index)?.first
        if (opt != null) f.set("wpaSSID", opt)
        f.set("security_method", method.toString())
        f.set("wpaAuth", "psk")
        f.set("pskFormat", "0")
        f.set("pskValue", psk)
        arr("_wpaGroupRekeyTime")?.let { f.set("gk_rekey", it) }
        arr("_wlan_isNmode")?.let { f.set("isNmode", it) }

        val u = arr("_uCipher")?.toIntOrNull() ?: 2
        var u2 = arr("_wpa2uCipher")?.toIntOrNull() ?: 2
        if (method != encrypt || u2 == 0) u2 = 2 // AES
        f.remove("ciphersuite_t"); f.remove("ciphersuite_a")
        f.remove("wpa2ciphersuite_t"); f.remove("wpa2ciphersuite_a")
        if (u and 1 != 0) f.set("ciphersuite_t", "1")
        if (u and 2 != 0) f.set("ciphersuite_a", "1")
        if (u2 and 1 != 0) f.set("wpa2ciphersuite_t", "1")
        if (u2 and 2 != 0) f.set("wpa2ciphersuite_a", "1")

        f.remove("use1x")
        if (arr("_enable1X") == "1") f.set("use1x", "ON")
        arr("_dotIEEE80211W")?.let { f.set("dotIEEE80211W", it) }
        arr("_sha256")?.let { f.set("sha256", it) }
        arr("_wepAuth")?.toIntOrNull()?.let { f.set("auth_type", listOf("open", "shared", "both").getOrElse(it) { "open" }) }
        arr("_rsPort")?.let { f.set("radiusPort", it) }
        arr("_rsIpAddr")?.let { f.set("radiusIP", it) }
        arr("_rsPassword")?.let { f.set("radiusPass", it) }
        if (f["submit-url"] == null) f.set("submit-url", "/admin/wlwpa.asp")
        if (f["save"] == null) f.set("save", "Apply Changes")
    }

    // ---------------------------------------------------------------- smart connect / names

    /**
     * The router's "ONE-WiFi" page (/wlonewifi.asp) is its Smart Connect: one name and one
     * password for both bands, with band steering. [supported] is false when the page is missing.
     */
    suspend fun smartConnect(): SmartConnectState {
        val html = try { client.get("/wlonewifi.asp") } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { return SmartConnectState.UNSUPPORTED }
        if (Parsers.form(html, "formOneWifi") == null) return SmartConnectState.UNSUPPORTED
        val idx = Regex("""sta_control"?\)?\[(\d)\]\.checked\s*=\s*true""").find(html)?.groupValues?.get(1)
        val radioValues = Parsers.doc(html).select("input[name=sta_control]").map { it.attr("value") }
        val enabled = idx?.toIntOrNull()?.let { radioValues.getOrNull(it) == "1" }
            ?: (Parsers.jsVar(html, "wlan_sta_control") == "1")
        fun jsField(name: String) = Regex("""getElementsByName\("$name"\)\[0\]\.value\s*=\s*"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)
        val ssid = jsField("ssid") ?: Parsers.doc(html).select("input[name=ssid]").firstOrNull()?.attr("value").orEmpty()
        return SmartConnectState(true, enabled, ssid, jsField("pskValue").orEmpty())
    }

    private suspend fun runForm(
        path: String, form: String, fields: Map<String, String>, radios: Map<String, String> = emptyMap(),
        strict: Boolean = true, checks: Map<String, Boolean> = emptyMap(),
    ): WebFormResult =
        client.exclusive {
            web.submit(client.baseUrl, client.sessionCookie, path, form, fields, radios, strict, checks)
        }

    /**
     * Apply a Wi-Fi plan. With Smart Connect on: ONE-WiFi on, one name + password for both bands
     * (or, on routers without ONE-WiFi, the same name + password written to each band).
     * With it off: ONE-WiFi off, then each band's own name and password.
     * [phoneBand] goes last so the phone stays connected while the other band is changed.
     */
    suspend fun saveWifi(plan: WifiPlan, phoneBand: Band?): List<String> {
        val log = mutableListOf<String>()
        val sc = smartConnect()
        val bands = Band.values().sortedBy { if (it == phoneBand) 1 else 0 }

        if (plan.smartConnect && sc.supported) {
            val name = plan.sharedName ?: sc.ssid
            val pass = plan.sharedPassword ?: sc.password.ifBlank {
                wifiInfo().firstOrNull { it.band == Band.GHZ_2_4 && it.password.isNotBlank() }?.password
                    ?: wifiInfo().firstOrNull { it.password.isNotBlank() }?.password.orEmpty()
            }
            if (!isValidSsid(name)) throw RouterException("Invalid Wi-Fi name")
            if (!isValidPsk(pass)) throw RouterException("Current password could not be read; please type a new one")
            val r = runForm("/wlonewifi.asp", "onewifi",
                mapOf("ssid" to name, "pskFormat" to "0", "pskValue" to pass), mapOf("sta_control" to "1"), strict = false)
            log += if (r.ok) "Smart Connect on: name and password set for both bands" else "Smart Connect: ${r.message}"
            return log
        }

        if (sc.supported && sc.enabled && !plan.smartConnect) {
            val r = runForm("/wlonewifi.asp", "onewifi", emptyMap(), mapOf("sta_control" to "0"), strict = false)
            log += if (r.ok) "Smart Connect turned off" else "Could not turn Smart Connect off: ${r.message}"
            if (!r.ok) return log
        }

        for (band in bands) {
            val name = if (plan.smartConnect) plan.sharedName else plan.names[band]
            val pass = if (plan.smartConnect) plan.sharedPassword else plan.passwords[band]
            if (!name.isNullOrBlank()) {
                if (!isValidSsid(name)) { log += "${band.label}: invalid name"; continue }
                val r = runForm(bandPage("/wlbasic.asp", band), "wlanSetup", mapOf("ssid" to name))
                log += "${band.label} name: " + if (r.ok) "changed" else r.message
            }
            if (!pass.isNullOrEmpty()) {
                val res = setWifiPassword(pass, listOf(band)).first()
                log += "${band.label} password: ${res.message}"
            }
        }
        if (log.isEmpty()) log += "Nothing to change"
        return log
    }

    // ---------------------------------------------------------------- devices

    suspend fun devices(): List<Device> {
        // status_ethernet_info.asp carries the device (host) names; the uptime page is a fallback.
        var hosts: List<Parsers.LanHost> = try {
            Parsers.dhcpClients(client.get("/status_ethernet_info.asp"))
        } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { emptyList() }
        if (hosts.isEmpty()) hosts = try {
            Parsers.lanHosts(client.get("/status_dhcp_uptime.asp"))
        } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { emptyList() }
        val byMac = hosts.associateBy { it.mac }
        val wifi = mutableMapOf<String, Pair<Band, Int?>>()
        for (band in Band.values()) {
            try {
                val html = client.get(bandPage("/wlstatbl.asp", band))
                for (c in Parsers.wifiClients(html)) wifi[c.mac] = band to c.rssi
            } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { }
        }
        val macs = (byMac.keys + wifi.keys).distinct()
        return macs.map { mac ->
            val h = byMac[mac]
            val w = wifi[mac]
            Device(mac, h?.ip, h?.name, w?.first, w?.second)
        }.sortedWith(compareBy({ it.band == null }, { it.name ?: it.ip ?: it.mac }))
    }

    // ---------------------------------------------------------------- block (MAC filter)

    private val macPage = "/secu_macfilter_src.asp"

    /** Router "MAC Filtering" (Security menu): source-MAC filter on LAN/Wi-Fi traffic. */
    suspend fun macFilter(): MacFilterState {
        val html = client.get(macPage)
        val entries = Parsers.itNr(html).mapNotNull { r ->
            val mac = r["mac"] ?: r.entries.firstOrNull { !it.key.startsWith("__") && MAC_RE.matches(it.value.replace('-', ':')) }?.value
                ?: return@mapNotNull null
            BlockedEntry(mac.trim().replace('-', ':').lowercase(), r["devname"]?.takeIf { it.isNotBlank() }, r)
        }
        return MacFilterState(
            enabled = Parsers.cgiFlag(html, "macFilterEnble") ?: false,
            whitelist = Parsers.cgiFlag(html, "macFilterMode") ?: false,
            entries = entries,
        )
    }

    /**
     * Block [mac] by adding it to the blacklist. If the filter is off it is first switched on in
     * blacklist mode. A filter already running as a whitelist is never touched: adding to it would
     * allow the device instead, and changing its mode could cut everyone else off.
     */
    suspend fun block(mac: String, name: String?) {
        val m = mac.lowercase()
        var st = macFilter()
        if (st.enabled && st.whitelist) {
            throw RouterException("This router's MAC filter is set to Whitelist (only listed devices allowed). " +
                "Switch it to Blacklist in the router web panel before blocking from the app.")
        }
        if (st.entries.any { it.mac == m }) {
            if (!st.enabled) enableBlacklist()
            return
        }
        if (st.entries.size >= 16) throw RouterException("The router's block list is full (16 devices).")
        // The web panel only lets you add once the filter is on, so switch it on first.
        if (!st.enabled) enableBlacklist()
        client.submitPageForm("/secu_macfilter_src_add.asp", "formRteMacFilter") { _, f ->
            f.set("action", "ad")
            f.set("mac", m)
            f.set("submit-url", macPage)
        }
        st = macFilter()
        if (st.entries.none { it.mac == m }) throw RouterException("The router did not add $m to the block list.")
        if (!st.enabled) throw RouterException("$m is on the block list, but the router's MAC filter is still off.")
    }

    private suspend fun enableBlacklist() {
        client.submitPageForm(macPage, "formRteMacFilter") { _, f ->
            f.set("action", "sw")
            f.set("macFilterEnble", "on")
            f.set("macFilterMode", "off") // off = Black list, on = White list
            f.set("submit-url", macPage)
        }
        val st = macFilter()
        if (st.enabled && st.whitelist) {
            // Safety net: never leave a whitelist running from the app.
            client.submitPageForm(macPage, "formRteMacFilter") { _, f ->
                f.set("action", "sw"); f.set("macFilterEnble", "off"); f.set("submit-url", macPage)
            }
            throw RouterException("The router turned the filter on as a Whitelist, so the app switched it off again. " +
                "Set it to Blacklist in the router web panel.")
        }
    }

    suspend fun unblock(mac: String) {
        val m = mac.lowercase()
        val st = macFilter()
        val entry = st.entries.firstOrNull { it.mac == m } ?: return
        client.submitPageForm(macPage, "formRteMacFilter") { _, f ->
            f.set("action", "rm")
            f.set("bcdata", Parsers.sjiEncode(listOf(entry.record)))
            f.set("submit-url", macPage)
        }
        if (macFilter().entries.any { it.mac == m }) throw RouterException("The router did not remove $m.")
    }

    // ---------------------------------------------------------------- guest wi-fi

    /** The guest network is the first extra SSID ("AP1") of the 2.4 GHz radio. */
    private val guestBand = Band.GHZ_2_4

    suspend fun guestWifi(): GuestWifi {
        val html = try { client.get(bandPage("/wlmultipleap.asp", guestBand)) }
            catch (e: SessionExpiredException) { throw e } catch (_: Exception) { return GuestWifi.UNSUPPORTED }
        if (Parsers.form(html, "formWlanMultipleAP") == null || !html.contains("wl_ssid1")) return GuestWifi.UNSUPPORTED
        // The first `var wlanDisabled = N;` in the page body belongs to AP1.
        val disabled = Regex("""var\s+wlanDisabled\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)
        val enabled = disabled == "0"
        val ssid = Parsers.doc(html).select("input[name=wl_ssid1]").firstOrNull()?.attr("value").orEmpty()
        var pass = ""
        if (enabled) try {
            val sec = client.get(bandPage("/wlwpa.asp", guestBand))
            val idx = Parsers.selectOptions(sec, "wpaSSID").indexOfFirst { it.first == "1" }
            if (idx >= 0) pass = Parsers.jsArray(sec, "_wpaPSK")[idx].orEmpty()
        } catch (e: SessionExpiredException) { throw e } catch (_: Exception) {}
        return GuestWifi(true, enabled, ssid, pass)
    }

    /** Turn the guest network on (with its own name and WPA2 password) or off. */
    suspend fun setGuestWifi(enable: Boolean, ssid: String, password: String): List<String> {
        if (enable) {
            if (!isValidSsid(ssid)) throw RouterException("Invalid guest Wi-Fi name")
            if (!isValidPsk(password)) throw RouterException("Guest password must be 8 to 63 characters")
        }
        val log = mutableListOf<String>()
        // wl_hide_ssid1 "0" = broadcast the name; wl_access1 "1" = relay blocking (guests can't see each other).
        val fields = if (enable) mapOf("wl_ssid1" to ssid, "wl_hide_ssid1" to "0", "wl_access1" to "1") else emptyMap()
        val r = runForm(bandPage("/wlmultipleap.asp", guestBand), "MultipleAP", fields,
            strict = false, checks = mapOf("wl_disable1" to enable))
        if (!r.ok) throw RouterException("Guest Wi-Fi: ${r.message}")
        log += if (enable) "Guest Wi-Fi \"$ssid\" is on (2.4 GHz)" else "Guest Wi-Fi is off"
        if (enable) {
            client.session {
                val page = get(bandPage("/wlwpa.asp", guestBand))
                val idx = Parsers.selectOptions(page, "wpaSSID").indexOfFirst { it.first == "1" }
                if (idx < 0) throw RouterException("The router did not list the guest network on its security page")
                val form = Parsers.form(page, "formWlEncrypt") ?: throw RouterException("Wi-Fi security form not found")
                fillSecurityFields(page, form.fields, password, idx)
                post(form.action, form.fields.toList())
            }
            log += "Guest password set"
        }
        return log
    }

    // ---------------------------------------------------------------- speed limit (IP QoS traffic shaping)

    private val qosPage = "/net_qos_traffictl.asp"
    private val qosEditPage = "/net_qos_traffictl_edit.asp"

    suspend fun speedLimits(): List<SpeedLimit> {
        val html = client.get(qosPage)
        if (Parsers.form(html, "formQosTraffictl") == null) throw RouterException("This router has no traffic shaping page")
        return Parsers.itNr(html, "traffictlRules").mapNotNull { r ->
            val dir = r["direction"]?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val ip = (if (dir == 1) r["dstip"] else r["srcip"])?.trim()
                ?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: return@mapNotNull null
            SpeedLimit(r["id"].orEmpty().trim(), ip, download = dir == 1, kbps = r["rate"]?.trim()?.toIntOrNull() ?: 0)
        }
    }

    /**
     * Limit [ip] to [downKbps] / [upKbps] (0 = no limit that way). The router only takes steps of
     * 512 kb/s, so values are rounded up to the next step. Old rules for [ip] are replaced.
     */
    suspend fun setSpeedLimit(ip: String, downKbps: Int, upKbps: Int) {
        removeSpeedLimit(ip)
        if (downKbps <= 0 && upKbps <= 0) return
        val edit = client.get(qosEditPage)
        val outs = Regex("""oifkeys\.add\(\s*new\s+it\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)\s*\)""").findAll(edit)
            .map { it.groupValues[1] to it.groupValues[2] }.filter { it.first.isNotBlank() }.toList()
        val wan = outs.firstOrNull { it.second.contains("INTERNET", true) } ?: outs.firstOrNull()
            ?: throw RouterException("No internet (WAN) connection found for the speed limit")
        val ipv6Build = Regex("""if\s*\(\s*1\s*\)""").containsMatchIn(edit)
        for ((download, kbps) in listOf(true to downKbps, false to upKbps)) {
            if (kbps <= 0) continue
            val rate = ((kbps + 511) / 512) * 512
            val lst = buildString {
                append("inf=").append(wan.first).append("&proto=0")
                if (ipv6Build) append("&IPversion=1")
                if (download) append("&srcip=&srcnetmask=&dstip=").append(ip).append("&dstnetmask=255.255.255.255")
                else append("&srcip=").append(ip).append("&srcnetmask=255.255.255.255&dstip=&dstnetmask=")
                append("&sport=&dport=&rate=").append(rate).append("&direction=").append(if (download) 1 else 0)
            }
            client.submitPageForm(qosEditPage, "formQosTraffictlEdit") { _, f ->
                f.set("lst", lst)
                f.set("submit-url", qosPage)
            }
        }
        val now = speedLimits().filter { it.ip == ip }
        if ((downKbps > 0 && now.none { it.download }) || (upKbps > 0 && now.none { !it.download })) {
            throw RouterException("The router did not save the speed limit for $ip.")
        }
    }

    suspend fun removeSpeedLimit(ip: String) {
        val ids = speedLimits().filter { it.ip == ip }.map { it.id }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return
        // The rule list's checkboxes are "delete"; the page posts the ticked ids like this.
        client.submitPageForm(qosPage, "formQosTraffictl") { _, f ->
            f.set("lst", "applysetting#id=" + ids.joinToString("|"))
            f.set("submit-url", qosPage)
        }
        if (speedLimits().any { it.ip == ip }) throw RouterException("The router did not remove the speed limit for $ip.")
    }

    // ---------------------------------------------------------------- website blocking (URL filter)

    private val urlPage = "/secu_urlfilter_cfg.asp"

    suspend fun urlFilter(): UrlFilterState {
        val html = client.get(urlPage)
        if (Parsers.form(html, "formURL") == null) throw RouterException("This router has no URL filter page")
        return UrlFilterState(
            enabled = Parsers.cgiFlag(html, "urlfilterEnble") ?: false,
            sites = Parsers.itNr(html).mapNotNull { it["url"]?.trim()?.takeIf { u -> u.isNotEmpty() } },
        )
    }

    suspend fun blockSite(raw: String) {
        val url = cleanSite(raw)
        if (url.isEmpty() || url.length >= 100 || url.contains(' ') || url == "www.") throw RouterException("Invalid website address")
        var st = urlFilter()
        if (!st.enabled) {
            client.submitPageForm(urlPage, "formURL") { _, f ->
                f.set("action", "sw"); f.set("urlfilterEnble", "on"); f.set("urlFilterMode", "off"); f.set("submit-url", urlPage)
            }
        }
        if (url !in st.sites) {
            client.submitPageForm("/secu_urlfilter_add.asp", "formURL") { _, f ->
                f.set("action", "ad"); f.set("url", url); f.set("submit-url", urlPage)
            }
        }
        st = urlFilter()
        if (url !in st.sites) throw RouterException("The router did not add $url.")
        if (!st.enabled) throw RouterException("$url is on the list, but website blocking is still off on the router.")
    }

    suspend fun unblockSite(url: String) {
        val st = urlFilter()
        val i = st.sites.indexOf(url)
        if (i < 0) return
        client.submitPageForm(urlPage, "formURL") { _, f ->
            f.set("action", "rm")
            f.set("bcdata", "ld3:idxi${i}eee") // sji_idxencode(rules, "select", "idx") for one row
            f.set("submit-url", urlPage)
        }
        if (url in urlFilter().sites) throw RouterException("The router did not remove $url.")
    }

    // ---------------------------------------------------------------- internet schedule (family groups)

    private fun jsonObj(s: String): org.json.JSONObject = try {
        val t = s.trim()
        org.json.JSONObject(t.substring(t.indexOf('{').coerceAtLeast(0)))
    } catch (_: Exception) { org.json.JSONObject() }

    /** Schedule groups made by this app, by group name (see [scheduleName]). */
    suspend fun schedules(): Map<String, DeviceSchedule> {
        val fml = jsonObj(client.get("/boaform/get_fml_info"))
        val dates = jsonObj(client.get("/boaform/get_fml_date_info"))
        val out = LinkedHashMap<String, DeviceSchedule>()
        for (k in fml.keys()) {
            val g = fml.optJSONObject(k) ?: continue
            val name = g.optString("family_name")
            if (!name.startsWith(SCHEDULE_PREFIX)) continue
            val idx = g.optString("family_index")
            val rules = dates.keys().asSequence().mapNotNull { dates.optJSONObject(it) }
                .filter { it.optString("family_index") == idx }
                .map { ScheduleRule(it.optString("date_week").toIntOrNull() ?: 0, it.optString("on_time").toIntOrNull() ?: 0, it.optString("off_time").toIntOrNull() ?: 0) }
                .toList()
            out[name] = DeviceSchedule(idx, name, rules)
        }
        return out
    }

    private suspend fun fmlPost(json: org.json.JSONObject) =
        client.postAjax("/boaform/form_fml_config_set", listOf("config_json" to json.toString()))

    /**
     * Allow [mac] internet only on [days] (0 = Monday … 6 = Sunday) between [fromMin] and [toMin]
     * (minutes after midnight), using the router's Family Group "allowed time of use".
     */
    suspend fun setSchedule(mac: String, days: Set<Int>, fromMin: Int, toMin: Int) {
        require(days.isNotEmpty()) { "Pick at least one day" }
        val name = scheduleName(mac)
        var g = schedules()[name]
        if (g == null) {
            fmlPost(org.json.JSONObject().put("family_index", "0").put("family_name", name))
            g = schedules()[name] ?: throw RouterException("The router did not create the schedule.")
        }
        fmlPost(org.json.JSONObject().put("family_index", g.index).put("family_name", name).put("member_list", mac.lowercase() + ","))
        val list = org.json.JSONObject()
        days.sorted().forEachIndexed { i, d ->
            list.put(i.toString(), org.json.JSONObject()
                .put("enable", "1").put("date_week", d.toString())
                .put("on_time", (fromMin * 60).toString()).put("off_time", (toMin * 60).toString()))
        }
        fmlPost(org.json.JSONObject().put("family_index", g.index).put("family_name", name).put("date_list", list))
        val after = schedules()[name]
        if (after == null || after.rules.size != days.size) throw RouterException("The router did not save the schedule.")
    }

    suspend fun removeSchedule(mac: String) {
        val g = schedules()[scheduleName(mac)] ?: return
        client.postAjax("/boaform/form_del_fml", listOf("del_family_index" to g.index))
        if (schedules().containsKey(g.name)) throw RouterException("The router did not remove the schedule.")
    }

    // ---------------------------------------------------------------- router login settings

    /** Minutes an idle web login is kept, or null when the page is missing. */
    suspend fun loginTimeout(): Int? = try {
        Parsers.doc(client.get("/mgm_web_login_timeout.asp")).select("input[name=session_timeout]").firstOrNull()
            ?.attr("value")?.trim()?.toIntOrNull()
    } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }

    suspend fun setLoginTimeout(minutes: Int) {
        require(minutes in 1..1440) { "Timeout must be 1 to 1440 minutes" }
        client.submitPageForm("/mgm_web_login_timeout.asp", "formWebLoginTimeout") { _, f ->
            f.set("session_timeout", minutes.toString())
            f.set("save", "Save")
        }
    }

    /** How many devices may be logged in at once (1 = only one), or null when the page is missing. */
    suspend fun maxLogins(): Int? = try {
        val html = client.get("/mgm_max_concurrent_login.asp")
        if (Parsers.form(html, "formMAxCoucurrentLogin") == null) null else {
            val d = Parsers.doc(html)
            val on = d.select("input[name=enable_max_login][value=1]").any { it.hasAttr("checked") }
            if (!on) 1 else d.select("input[name=max_login_devices]").firstOrNull()?.attr("value")?.trim()?.toIntOrNull() ?: 2
        }
    } catch (e: SessionExpiredException) { throw e } catch (_: Exception) { null }

    /** Allow [n] logins at once (2-10), or only one when [n] is 1. The router reboots to apply it. */
    suspend fun setMaxLogins(n: Int) {
        require(n in 1..10) { "Choose 1 to 10" }
        try {
            client.submitPageForm("/mgm_max_concurrent_login.asp", "formMAxCoucurrentLogin") { _, f ->
                f.set("enable_max_login", if (n >= 2) "1" else "0")
                if (n >= 2) f.set("max_login_devices", n.toString()) else f.remove("max_login_devices")
                f.set("save", "Save")
            }
        } catch (_: java.io.IOException) {
            // The router reboots to apply this and may drop the connection: that's success.
        }
    }


    // ---------------------------------------------------------------- tools

    suspend fun reboot() {
        try {
            client.submitPageForm("/mgm_dev_reboot.asp", "formReboot")
        } catch (e: java.io.IOException) {
            // The router often drops the connection as it goes down; that's success.
        }
    }

    suspend fun changeAdminPassword(oldPass: String, newPass: String) {
        client.submitPageForm("/mgm_usr_user.asp", "formPasswordSetup") { _, f ->
            f.set("oldPasswd", oldPass)
            f.set("newPasswd", newPass)
            f.set("affirmPasswd", newPass)
        }
    }

    companion object {
        private val MAC_RE = Regex("""(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}""")
        /** This firmware rejects empty names, spaces and names over 32 bytes. */
        fun isValidSsid(n: String) = n.isNotEmpty() && n.toByteArray().size <= 32 && !n.contains(' ') &&
            n.all { it.code in 33..126 }
        fun isValidPsk(p: String) = p.length in 8..63 && p.all { it.code in 32..126 }

        /** Family groups made by the app are named App + the last 8 hex digits of the device MAC. */
        const val SCHEDULE_PREFIX = "App"
        fun scheduleName(mac: String) = SCHEDULE_PREFIX + mac.replace(":", "").replace("-", "").lowercase().takeLast(8)

        /** "https://www.Example.com/path" -> "www.example.com/path" (what the router matches on). */
        fun cleanSite(raw: String): String = raw.trim().lowercase()
            .removePrefix("http://").removePrefix("https://").trimEnd('/')
    }
}
