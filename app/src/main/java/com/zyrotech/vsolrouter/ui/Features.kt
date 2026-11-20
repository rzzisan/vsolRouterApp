package com.zyrotech.vsolrouter.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.zyrotech.vsolrouter.data.RouterProfile
import com.zyrotech.vsolrouter.net.Device
import com.zyrotech.vsolrouter.net.DeviceSchedule
import com.zyrotech.vsolrouter.net.RouterApi
import com.zyrotech.vsolrouter.net.SpeedLimit
import com.zyrotech.vsolrouter.work.Background

// ------------------------------------------------------------------ Wi-Fi QR code

/** The standard Wi-Fi QR text that phone cameras understand. */
private fun wifiQrText(ssid: String, password: String): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    return if (password.isEmpty()) "WIFI:T:nopass;S:${esc(ssid)};;" else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;"
}

private fun qrBitmap(text: String, size: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun WifiQrDialog(ssid: String, password: String, onDismiss: () -> Unit) {
    val bmp = remember(ssid, password) { qrBitmap(wifiQrText(ssid, password)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QrCode2, null) },
        title = { Text("Join \"$ssid\"") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                    Image(bmp.asImageBitmap(), "Wi-Fi QR code", Modifier.size(230.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text("Scan with the phone camera to connect.", textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (password.isNotEmpty()) Text("Password: $password", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ------------------------------------------------------------------ guest Wi-Fi

@Composable
fun GuestWifiCard(vm: MainViewModel, onQr: (String, String) -> Unit) {
    val g = vm.guest
    if (!g.supported) {
        EmptyState(if (vm.busy) "Loading…" else "Guest Wi-Fi not available",
            if (vm.busy) "Reading the router." else "This router did not show a guest network page. Tap refresh to try again.",
            Icons.Rounded.Groups)
    } else GuestWifiCardInner(vm, onQr)
}

@Composable
private fun GuestWifiCardInner(vm: MainViewModel, onQr: (String, String) -> Unit) {
    val g = vm.guest
    var on by remember(g) { mutableStateOf(g.enabled) }
    var name by remember(g) { mutableStateOf(g.ssid.ifBlank { "Guest" }) }
    var pass by remember(g) { mutableStateOf(g.password) }
    val valid = !on || (RouterApi.isValidSsid(name) && RouterApi.isValidPsk(pass))
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardHeader(
                Icons.Rounded.Groups, "Guest Wi-Fi",
                "A separate 2.4 GHz network for visitors. Guests cannot see each other.",
                tint = MaterialTheme.colorScheme.tertiary,
            ) { Switch(checked = on, onCheckedChange = { on = it }) }
            if (on) {
                AppField(name, { name = it.trim() }, "Guest Wi-Fi name", icon = Icons.Rounded.Wifi, isError = !RouterApi.isValidSsid(name))
                PasswordField(pass, { pass = it }, "Guest password", icon = Icons.Rounded.Key,
                    isError = pass.isNotEmpty() && !RouterApi.isValidPsk(pass), supporting = "8 to 63 characters.")
            }
            PrimaryButton(
                "Save guest Wi-Fi", { vm.saveGuest(on, name, pass) },
                enabled = !vm.busy && valid && (on != g.enabled || name != g.ssid || pass != g.password),
            )
            if (g.enabled && g.ssid.isNotBlank()) {
                OutlinedButton(
                    onClick = { onQr(g.ssid, g.password) }, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Rounded.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Show QR code for guests")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ speed limit

private fun mbps(kbps: Int) = if (kbps % 1024 == 0) "${kbps / 1024} Mbps" else String.format("%.1f Mbps", kbps / 1024.0)

fun speedSummary(rules: List<SpeedLimit>): String? {
    if (rules.isEmpty()) return null
    val d = rules.firstOrNull { it.download }?.let { "↓ " + mbps(it.kbps) }
    val u = rules.firstOrNull { !it.download }?.let { "↑ " + mbps(it.kbps) }
    return listOfNotNull(d, u).joinToString("  ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedLimitDialog(vm: MainViewModel, d: Device, onDismiss: () -> Unit) {
    val current = vm.speedLimits.orEmpty().filter { it.ip == d.ip }
    fun fmt(k: Int?) = k?.let { if (it % 1024 == 0) (it / 1024).toString() else (it / 1024.0).toString() }.orEmpty()
    var down by remember { mutableStateOf(fmt(current.firstOrNull { it.download }?.kbps)) }
    var up by remember { mutableStateOf(fmt(current.firstOrNull { !it.download }?.kbps)) }
    fun kbps(s: String): Int? = if (s.isBlank()) 0 else s.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }?.let { (it * 1024).toInt() }
    val ok = kbps(down) != null && kbps(up) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Speed, null) },
        title = { Text("Speed limit · ${d.name ?: d.ip ?: d.mac}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quick pick (download)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in listOf("1", "2", "5", "10", "20")) {
                        FilterChip(selected = down == m, onClick = { down = m }, label = { Text("$m Mbps") })
                    }
                }
                AppField(down, { down = it }, "Download limit (Mbps)", isError = kbps(down) == null,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), icon = Icons.Rounded.Speed)
                AppField(up, { up = it }, "Upload limit (Mbps)", isError = kbps(up) == null,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), icon = Icons.Rounded.Speed)
                Text("Blank = no limit. The router uses steps of 0.5 Mbps. The limit follows the device's IP " +
                    "(${d.ip ?: "none"}); if the router gives it a new IP later, set it again.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = ok && d.ip != null, onClick = { vm.setSpeedLimit(d, kbps(down) ?: 0, kbps(up) ?: 0); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) TextButton(onClick = { vm.setSpeedLimit(d, 0, 0); onDismiss() }) { Text("Remove", color = Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ------------------------------------------------------------------ internet schedule

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
private fun parseHm(s: String): Int? {
    val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").find(s) ?: return null
    val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
    return if (h in 0..24 && mi in 0..59 && h * 60 + mi <= 1440) h * 60 + mi else null
}

fun scheduleSummary(s: DeviceSchedule?): String? {
    val rules = s?.rules ?: return null
    val r = rules.firstOrNull() ?: return null
    val days = rules.map { it.day }.toSet()
    val dayText = when {
        days.size == 7 -> "Every day"
        days == setOf(0, 1, 2, 3, 4) -> "Mon-Fri"
        else -> days.sorted().joinToString(" ") { DAYS.getOrElse(it) { "?" } }
    }
    return "$dayText ${hhmm(r.onSec / 60)}-${hhmm(r.offSec / 60)}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(vm: MainViewModel, d: Device, onDismiss: () -> Unit) {
    val current = vm.schedules?.get(RouterApi.scheduleName(d.mac))
    var days by remember { mutableStateOf(current?.rules?.map { it.day }?.toSet()?.ifEmpty { null } ?: (0..6).toSet()) }
    var from by remember { mutableStateOf(current?.rules?.firstOrNull()?.let { hhmm(it.onSec / 60) } ?: "06:00") }
    var to by remember { mutableStateOf(current?.rules?.firstOrNull()?.let { hhmm(it.offSec / 60) } ?: "22:00") }
    val f = parseHm(from); val t = parseHm(to)
    val ok = days.isNotEmpty() && f != null && t != null && f < t
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, null) },
        title = { Text("Internet time · ${d.name ?: d.mac}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This device gets internet only during these hours.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEachIndexed { i, n ->
                        FilterChip(selected = i in days, onClick = { days = if (i in days) days - i else days + i }, label = { Text(n) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { days = (0..6).toSet() }) { Text("Every day") }
                    TextButton(onClick = { days = (0..4).toSet() }) { Text("Mon-Fri") }
                    TextButton(onClick = { days = setOf(5, 6) }) { Text("Weekend") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(from, { from = it }, "From", Modifier.weight(1f), placeholder = "06:00", isError = f == null)
                    AppField(to, { to = it }, "To", Modifier.weight(1f), placeholder = "22:00", isError = t == null || (f != null && t <= f))
                }
                Text("Uses the router's clock (24-hour time). The router must have the correct time from the internet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { vm.setSchedule(d, days, f!!, t!!); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { vm.removeSchedule(d); onDismiss() }) { Text("Remove", color = Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ------------------------------------------------------------------ tools pages

@Composable
fun WebsiteBlockCard(vm: MainViewModel) {
    val st = vm.sites
    var url by remember { mutableStateOf("") }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Rounded.Language, "Blocked websites", "For every device on this router.", tint = Bad)
            if (st == null) {
                InfoNote(if (vm.busy) "Reading the router…" else "Could not read the router's website filter.", color = Warn)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppField(url, { url = it.trim() }, "Website", Modifier.weight(1f), placeholder = "e.g. facebook.com",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { vm.blockSite(url); url = "" }, enabled = !vm.busy && url.length >= 3,
                        modifier = Modifier.height(54.dp), shape = MaterialTheme.shapes.small,
                    ) { Text("Block") }
                }
                if (st.sites.isEmpty()) {
                    Text("No blocked websites yet.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (s in st.sites) {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = Bad, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.unblockSite(s) }, enabled = !vm.busy) { Icon(Icons.Rounded.Close, "Unblock $s") }
                    }
                }
                Text("Works for plain web addresses. Some HTTPS sites and apps may still open.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AdminPasswordCard(vm: MainViewModel) {
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(Icons.Rounded.Lock, "Router admin password", "Used to sign in to the router. The app saves the new one.")
            PasswordField(old, { old = it }, "Current password")
            PasswordField(new1, { new1 = it }, "New password", isError = new1.isNotEmpty() && new1.length !in 6..32,
                supporting = "6 to 32 characters.")
            PasswordField(new2, { new2 = it }, "Repeat new password", isError = new2.isNotEmpty() && new1 != new2,
                supporting = if (new2.isNotEmpty() && new1 != new2) "Passwords do not match." else null)
            PrimaryButton(
                "Change password",
                { vm.changeAdminPassword(old, new1); old = ""; new1 = ""; new2 = "" },
                enabled = !vm.busy && old.isNotEmpty() && new1.length in 6..32 && new1 == new2,
                icon = Icons.Rounded.Lock,
            )
        }
    }
}

@Composable
fun RouterLoginCard(vm: MainViewModel) {
    if (vm.loginTimeout != null || vm.maxLogins != null) RouterLoginCardInner(vm)
    else EmptyState(if (vm.busy) "Loading…" else "Not available", "This router did not show its login settings.", Icons.Rounded.Timer)
}

@Composable
private fun RouterLoginCardInner(vm: MainViewModel) {
    var timeout by remember(vm.loginTimeout) { mutableStateOf(vm.loginTimeout?.toString().orEmpty()) }
    var max by remember(vm.maxLogins) { mutableStateOf(vm.maxLogins?.toString().orEmpty()) }
    var confirmMax by remember { mutableStateOf<Int?>(null) }
    InfoNote(
        "The router lets one device sign in at a time and keeps an unused login for the timeout below. " +
            "A shorter timeout, or allowing 2+ logins, stops \"logged in on another device\" errors.",
    )
    if (vm.loginTimeout != null) {
        val v = timeout.toIntOrNull()
        AppCard {
            CardHeader(Icons.Rounded.Timer, "Idle timeout", "Minutes before an unused login is released", tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppField(timeout, { timeout = it.filter(Char::isDigit) }, "Minutes (1-1440)", Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), isError = v == null || v !in 1..1440)
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { vm.saveLoginTimeout(v!!) }, modifier = Modifier.height(54.dp), shape = MaterialTheme.shapes.small,
                    enabled = !vm.busy && v != null && v in 1..1440 && v != vm.loginTimeout,
                ) { Text("Save") }
            }
        }
    }
    if (vm.maxLogins != null) {
        val v = max.toIntOrNull()
        AppCard {
            CardHeader(Icons.Rounded.Groups, "Logins at once", "How many devices may be signed in together", tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppField(max, { max = it.filter(Char::isDigit) }, "Logins (1-10)", Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), isError = v == null || v !in 1..10)
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { confirmMax = v }, modifier = Modifier.height(54.dp), shape = MaterialTheme.shapes.small,
                    enabled = !vm.busy && v != null && v in 1..10 && v != vm.maxLogins,
                ) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Changing this reboots the router.", style = MaterialTheme.typography.bodySmall, color = Warn)
        }
    }
    confirmMax?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmMax = null },
            icon = { Icon(Icons.Rounded.Groups, null) },
            title = { Text("Allow $n login(s) at once?") },
            text = { Text("The router reboots to apply this. Internet and Wi-Fi drop for about 1-2 minutes.") },
            confirmButton = { TextButton(onClick = { confirmMax = null; vm.saveMaxLogins(n) }) { Text("Save and reboot") } },
            dismissButton = { TextButton(onClick = { confirmMax = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ saved routers

@Composable
fun SavedRouters(vm: MainViewModel) {
    if (vm.routers.isNotEmpty()) SavedRoutersInner(vm)
}

@Composable
private fun SavedRoutersInner(vm: MainViewModel) {
    var toRemove by remember { mutableStateOf<RouterProfile?>(null) }
    val cs = MaterialTheme.colorScheme
    SectionLabel("Saved routers")
    SettingsGroup(
        vm.routers.map<RouterProfile, @Composable () -> Unit> { r ->
            {
                val here = vm.found?.model != null && vm.found?.host == r.host
                SettingsRow(
                    Icons.Rounded.Router, r.name,
                    listOfNotNull(r.model?.takeIf { it != r.name }, r.host, if (r.id == vm.activeId) "last used" else null).joinToString(" · "),
                    tint = if (here) Good else cs.primary,
                    enabled = !vm.busy && vm.loginWait == 0,
                    onClick = { vm.tapSaved(r) },
                    trailing = {
                        IconButton(onClick = { toRemove = r }) { Icon(Icons.Rounded.Delete, "Remove ${r.name}", tint = cs.outline) }
                    },
                )
            }
        },
    )
    toRemove?.let { r ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Remove ${r.name}?") },
            text = { Text("Its saved login is deleted from this phone. The router itself is not changed.") },
            confirmButton = { TextButton(onClick = { vm.removeSaved(r); toRemove = null }) { Text("Remove", color = Bad) } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
