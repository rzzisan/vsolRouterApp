package com.zyrotech.vsolrouter.net

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.FormElement

data class ParsedForm(val action: String, val fields: FormFields)

/**
 * Parsers for the server-rendered Boa pages. Data appears in three shapes:
 *  - JS arrays:      _wpaPSK[0]='secret';
 *  - it_nr records:  clts.push(new it_nr("0", new it("ip","192.168.1.2"), ...));
 *  - HTML tables:    <tr><th>Label</th><td>Value</td></tr>  or  <tr class="tbl_body">...
 */
object Parsers {

    /** Key under which [itNr] stores the record's own name (first it_nr argument). */
    const val ID_KEY = "__id"
    /** Marks a value that was an unquoted number in the page (encoded differently by sji_encode). */
    const val NUM_PREFIX = "__num_"

    private val MAC = Regex("""(?i)\b([0-9a-f]{2}(?::[0-9a-f]{2}){5})\b""")

    fun doc(html: String, base: String = ""): Document = Jsoup.parse(html, base)

    fun isLoginPage(html: String): Boolean =
        html.contains("formLogin") && (html.contains("name=\"psd\"") || html.contains("psd1") || html.contains("name='psd'"))

    /** Router error pages contain lines such as "ERROR: You have entered wrong username or password thrice". */
    fun errorMessage(html: String): String? {
        val text = Jsoup.parse(html).text()
        val m = Regex("""ERROR\s*:?\s*([^!.]*[!.]?[^!.]*[!.]?)""").find(text) ?: return null
        return m.value.trim().take(200)
    }

    fun csrfToken(html: String): String? =
        Regex("""name=['"]?csrftoken['"]?\s+value=['"]?([^'">\s]*)""").find(html)?.groupValues?.get(1)

    /** The form whose action contains [actionPart], with its current values as a browser would send them. */
    fun form(html: String, actionPart: String): ParsedForm? {
        val d = doc(html, "http://router/")
        val el = d.select("form").firstOrNull { it.attr("action").contains(actionPart, ignoreCase = true) }
            ?: return null
        val fields = mutableListOf<Pair<String, String>>()
        if (el is FormElement) {
            for (kv in el.formData()) fields += kv.key() to kv.value()
        } else {
            for (input in el.select("input[name]")) {
                val type = input.attr("type").lowercase()
                if ((type == "checkbox" || type == "radio") && !input.hasAttr("checked")) continue
                if (type == "button" || type == "submit") continue
                fields += input.attr("name") to input.attr("value")
            }
        }
        val ff = FormFields(fields)
        // Boa puts the token just before </form>; if misnested HTML lost it, recover it by regex.
        if (ff["csrftoken"] == null) csrfToken(html)?.let { ff.set("csrftoken", it) }
        var action = el.attr("action")
        if (!action.startsWith("/")) action = "/" + action.trimStart('.', '/')
        return ParsedForm(action, ff)
    }

    /** Values of a JS array like `_wpaPSK[0]='abc';` → {0: "abc"}. */
    fun jsArray(html: String, name: String): Map<Int, String> {
        val re = Regex("""(?<![\w.])${Regex.escape(name)}\[(\d+)\]\s*=\s*(?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)"|([^;\s]*))\s*;""")
        val out = LinkedHashMap<Int, String>()
        for (m in re.findAll(html)) {
            val v = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            out[m.groupValues[1].toInt()] = unescapeJs(v)
        }
        return out
    }

    /** A scalar JS variable like `var wlan_idx= 0;` or `ssid_num=2;`. */
    fun jsVar(html: String, name: String): String? {
        val re = Regex("""(?<![\w.])${Regex.escape(name)}\s*=\s*(?:'([^']*)'|"([^"]*)"|([^;\s]+))""")
        val m = re.find(html) ?: return null
        return m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
    }

    /** Records pushed as `arr.push(new it_nr("id", new it("k","v"), new it("n", 5)))`. */
    fun itNr(html: String, arrayName: String? = null): List<Map<String, String>> {
        // Records are written either as `arr.push(new it_nr(...));` or, inside `with(arr){...}`,
        // as a bare `push(new it_nr(...));` (the MAC filter list does this).
        val prefix = if (arrayName == null) """(?<![\w.])(?:\w+\.)?""" else Regex.escape(arrayName) + """\."""
        val rec = Regex("""${prefix}push\(\s*new\s+it_nr\((.*?)\)\s*\)\s*;""", RegexOption.DOT_MATCHES_ALL)
        val kv = Regex("""new\s+it\(\s*"([^"]*)"\s*,\s*(?:"((?:[^"\\]|\\.)*)"|([^)\s]*))\s*\)""")
        val id = Regex("""^\s*"([^"]*)"""")
        return rec.findAll(html).map { m ->
            val map = LinkedHashMap<String, String>()
            for (k in kv.findAll(m.groupValues[1])) {
                val quoted = k.groups[2]
                map[k.groupValues[1]] = unescapeJs(quoted?.value ?: k.groups[3]?.value ?: "")
                if (quoted == null) map[NUM_PREFIX + k.groupValues[1]] = "1"
            }
            if (map.isNotEmpty()) id.find(m.groupValues[1])?.let { map[ID_KEY] = it.groupValues[1] }
            map.toMap()
        }.filter { it.isNotEmpty() }.toList()
    }

    /** Label/value rows: <tr><th|td>Label</th><td>Value</td></tr>. */
    fun keyValues(html: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (tr in doc(html).select("tr")) {
            val cells = tr.children().toList().filter { it.tagName() == "th" || it.tagName() == "td" }
            if (cells.size != 2) continue
            if (cells.any { it.select("input,select,table").isNotEmpty() }) continue
            val label = cells[0].text().trim().trimEnd(':', '：').trim()
            val value = cells[1].text().trim()
            if (label.isEmpty() || value.isEmpty() || label.length > 60) continue
            out += label to value
        }
        return out
    }

    data class WifiClient(val mac: String, val cells: List<String>, val rssi: Int?)

    /** Rows of the associated-station table (class tbl_body) that contain a MAC address. */
    fun wifiClients(html: String): List<WifiClient> {
        val out = mutableListOf<WifiClient>()
        for (tr in doc(html).select("tr")) {
            val cells = tr.select("td").map { it.text().trim() }
            val mac = cells.firstNotNullOfOrNull { MAC.find(it)?.value } ?: continue
            val rssi = cells.firstNotNullOfOrNull { c ->
                Regex("""-(\d{1,3})(\s*dBm)?$""").find(c)?.groupValues?.get(1)?.toIntOrNull()?.let { -it }
            }
            out += WifiClient(mac.lowercase(), cells, rssi)
        }
        return out.distinctBy { it.mac }
    }

    data class LanHost(val mac: String, val ip: String?, val name: String?)

    /** DHCP lease list: it_nr records first, then any table row holding an IP and a MAC. */
    fun lanHosts(html: String): List<LanHost> {
        val out = mutableListOf<LanHost>()
        for (rec in itNr(html)) {
            val r = rec.filterKeys { !it.startsWith("__") }
            val mac = r.values.firstNotNullOfOrNull { MAC.find(it)?.value } ?: continue
            val ip = r.values.firstOrNull { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
            val name = r.entries.firstOrNull { (k, _) ->
                k.contains("name", true) || k.contains("host", true) || k.contains("dev", true)
            }?.value
            out += LanHost(mac.lowercase(), ip, name?.takeIf { it.isNotBlank() && it != "---" })
        }
        if (out.isEmpty()) {
            for (tr in doc(html).select("tr")) {
                val cells = tr.select("td").map { it.text().trim() }
                val mac = cells.firstNotNullOfOrNull { MAC.find(it)?.value } ?: continue
                val ipIdx = cells.indexOfFirst { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
                val ip = cells.getOrNull(ipIdx)
                val name = cells.firstOrNull { c ->
                    c.isNotBlank() && c != ip && MAC.find(c) == null && !c.matches(Regex("""[\d:.\s-]+""")) && c != "---"
                }
                out += LanHost(mac.lowercase(), ip, name)
            }
        }
        return out.distinctBy { it.mac }
    }

    /**
     * Connected-device list from status_ethernet_info.asp: `clts` (DHCP leases: custName,
     * devname, macAddr, ipAddr, liveTime, connType) and `static_clts` (hostName). The user-set
     * custom name wins over the host name the device announced.
     */
    fun dhcpClients(html: String): List<LanHost> {
        fun clean(v: String?): String? = v?.trim()?.takeIf {
            it.isNotEmpty() && it != "0" && it != "*" && it != "---" && !it.equals("unknown", true)
        }
        return itNr(html, "clts").mapNotNull { r ->
            val macRaw = r["macAddr"] ?: r.values.firstNotNullOfOrNull { MAC.find(it)?.value } ?: return@mapNotNull null
            val mac = macRaw.trim().replace('-', ':').lowercase()
            if (MAC.find(mac) == null) return@mapNotNull null
            val ip = r["ipAddr"]?.trim()?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
            LanHost(mac, ip, clean(r["custName"]) ?: clean(r["devname"]) ?: clean(r["hostName"]))
        }.distinctBy { it.mac }
    }

    /** Options of a <select name=...> as (value, text). */
    fun selectOptions(html: String, name: String): List<Pair<String, String>> =
        doc(html).select("select[name=$name] option").map { it.attr("value") to it.text().trim() }

    /** `cgi.macFilterEnble = true;` style flags → true/false/null. */
    fun cgiFlag(html: String, name: String): Boolean? {
        val v = Regex("""cgi\.${Regex.escape(name)}\s*=\s*([^;\s]+)""").find(html)?.groupValues?.get(1) ?: return null
        return when (v.trim('"', '\'').lowercase()) { "true", "1", "on" -> true; "false", "0", "off" -> false; else -> null }
    }

    /** Encode records the way common.js sji_encode()/it_nr.enc() does (bencode-like). */
    fun sjiEncode(records: List<Map<String, String>>): String {
        fun enc(v: String) = "${v.length}:$v"
        val sb = StringBuilder("l")
        for (r in records) {
            sb.append('d')
            r[ID_KEY]?.let { sb.append(enc("name")).append(enc(it)) }
            for ((k, v) in r) {
                if (k.startsWith("__")) continue
                sb.append(enc(k)).append(if (r.containsKey(NUM_PREFIX + k)) "i${v}e" else enc(v))
            }
            sb.append('e')
        }
        return sb.append('e').toString()
    }

    private fun unescapeJs(s: String): String =
        s.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
}
