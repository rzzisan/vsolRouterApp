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
            if (vm.busy) "Reading the router." else "This router did not show a guest network page. Tap refresh to try again.")
    } else GuestWifiCardInner(vm, onQr)
}

@Composable
private fun GuestWifiCardInner(vm: MainViewModel, onQr: (String, String) -> Unit) {
    val g = vm.guest
    var on by remember(g) { mutableStateOf(g.enabled) }
    var name by remember(g) { mutableStateOf(g.ssid.ifBlank { "Guest" }) }
    var pass by remember(g) { mutableStateOf(g.password) }
    val valid = !on || (RouterApi.isValidSsid(name) && RouterApi.isValidPsk(pass))
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Groups, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Guest Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("A separate network for visitors (2.4 GHz). Guests cannot see each other.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = on, onCheckedChange = { on = it })
            }
            if (on) {
                OutlinedTextField(name, { name = it.trim() }, label = { Text("Guest Wi-Fi name") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Wifi, null) }, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(), isError = !RouterApi.isValidSsid(name))
                OutlinedTextField(pass, { pass = it }, label = { Text("Guest password (8-63)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Key, null) }, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(), isError = pass.isNotEmpty() && !RouterApi.isValidPsk(pass))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.saveGuest(on, name, pass) },
                    enabled = !vm.busy && valid && (on != g.enabled || name != g.ssid || pass != g.password),
                    shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                ) { Text("Save guest Wi-Fi") }
                if (g.enabled && g.ssid.isNotBlank()) {
                    OutlinedButton(onClick = { onQr(g.ssid, g.password) }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("QR")
                    }
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
                OutlinedTextField(down, { down = it }, label = { Text("Download limit (Mbps)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(), isError = kbps(down) == null)
                OutlinedTextField(up, { up = it }, label = { Text("Upload limit (Mbps)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(), isError = kbps(up) == null)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text("From") }, singleLine = true, placeholder = { Text("06:00") },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f), isError = f == null)
                    OutlinedTextField(to, { to = it }, label = { Text("To") }, singleLine = true, placeholder = { Text("22:00") },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f), isError = t == null || (f != null && t <= f))
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

// ------------------------------------------------------------------ tools cards

@Composable
fun WebsiteBlockCard(vm: MainViewModel) {
    val st = vm.sites
    var url by remember { mutableStateOf("") }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Language, Bad.copy(alpha = 0.15f), Bad)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Block websites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("For every device on this router.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (st == null) {
                Text("Could not read the router's website filter.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
            for (s in st.sites) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { vm.unblockSite(s) }, enabled = !vm.busy) { Icon(Icons.Rounded.Close, "Unblock $s") }
                }
            }
            if (st.sites.isEmpty()) Text("No blocked websites.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(url, { url = it.trim() }, label = { Text("e.g. facebook.com") }, singleLine = true,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.blockSite(url); url = "" }, enabled = !vm.busy && url.length >= 3) { Text("Block") }
            }
            Text("Works for plain web addresses. Some HTTPS sites and apps may still open.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RouterLoginCard(vm: MainViewModel) {
    if (vm.loginTimeout != null || vm.maxLogins != null) RouterLoginCardInner(vm)
}

@Composable
private fun RouterLoginCardInner(vm: MainViewModel) {
    var timeout by remember(vm.loginTimeout) { mutableStateOf(vm.loginTimeout?.toString().orEmpty()) }
    var max by remember(vm.maxLogins) { mutableStateOf(vm.maxLogins?.toString().orEmpty()) }
    var confirmMax by remember { mutableStateOf<Int?>(null) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Timer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text("Router login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("The router lets one device log in at a time and keeps an unused login for the timeout below. " +
                "A shorter timeout, or allowing 2+ logins, stops \"logged in on another device\" errors.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.loginTimeout != null) {
                val v = timeout.toIntOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, label = { Text("Idle timeout (minutes)") },
                        singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = v == null || v !in 1..1440)
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { vm.saveLoginTimeout(v!!) },
                        enabled = !vm.busy && v != null && v in 1..1440 && v != vm.loginTimeout) { Text("Save") }
                }
            }
            if (vm.maxLogins != null) {
                val v = max.toIntOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(max, { max = it.filter(Char::isDigit) }, label = { Text("Logins allowed at once (1-10)") },
                        singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = v == null || v !in 1..10)
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { confirmMax = v },
                        enabled = !vm.busy && v != null && v in 1..10 && v != vm.maxLogins) { Text("Save") }
                }
            }
        }
    }
    confirmMax?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmMax = null },
            title = { Text("Allow $n login(s) at once?") },
            text = { Text("The router reboots to apply this. Internet and Wi-Fi drop for about 1-2 minutes.") },
            confirmButton = { TextButton(onClick = { confirmMax = null; vm.saveMaxLogins(n) }) { Text("Save and reboot") } },
            dismissButton = { TextButton(onClick = { confirmMax = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AlertsCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateNotifyNew(granted)
        if (!granted) vm.error = "Notifications are blocked for this app. Allow them in Android settings to get alerts."
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("New device alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Get a notification when a device joins for the first time.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.notifyNew, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Background.canNotify(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.updateNotifyNew(on)
                })
            }
            Text("Checked about every 15 minutes while this phone is on the router's Wi-Fi. Each check logs in for a few " +
                "seconds and logs out again. Home-screen widget: long-press the home screen → Widgets → VSOL Router.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    val list = vm.routers
    val cs = MaterialTheme.colorScheme
    Text("Saved routers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.primary)
    for (r in list) {
        val here = vm.found?.model != null && vm.found?.host == r.host
        ElevatedCard(
            Modifier.fillMaxWidth().clickable(enabled = !vm.busy && vm.loginWait == 0) { vm.tapSaved(r) },
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Router, if (here) cs.primaryContainer else cs.surfaceVariant,
                    if (here) cs.onPrimaryContainer else cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.model?.takeIf { it != r.name }, r.host).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (r.id == vm.activeId) StatusPill("Last used", Good)
                IconButton(onClick = { toRemove = r }) { Icon(Icons.Rounded.Delete, "Remove ${r.name}", tint = cs.outline) }
            }
        }
    }
    toRemove?.let { r ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${r.name}?") },
            text = { Text("Its saved login is deleted from this phone. The router itself is not changed.") },
            confirmButton = { TextButton(onClick = { vm.removeSaved(r); toRemove = null }) { Text("Remove", color = Bad) } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
