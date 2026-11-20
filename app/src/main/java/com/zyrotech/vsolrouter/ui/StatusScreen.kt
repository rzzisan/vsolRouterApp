package com.zyrotech.vsolrouter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrotech.vsolrouter.net.Section

internal fun findValue(sections: List<Section>, vararg keys: String): String? =
    sections.asSequence().flatMap { it.rows.asSequence() }
        .firstOrNull { (k, v) -> v.isNotBlank() && keys.any { k.contains(it, ignoreCase = true) } }?.second

internal fun parseDbm(v: String?): Double? = v?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

/** Optical receive power: good above -25 dBm, weak to -27, bad below. */
internal fun rxColor(dbm: Double?): Color? = when {
    dbm == null -> null
    dbm > -25 -> Good
    dbm > -27 -> Warn
    else -> Bad
}

private fun rxLabel(dbm: Double?): String = when {
    dbm == null -> "No reading"
    dbm > -20 -> "Excellent"
    dbm > -25 -> "Good"
    dbm > -27 -> "Weak"
    else -> "Poor"
}

@Composable
fun StatusTab(vm: MainViewModel, nav: Nav) {
    LaunchedEffect(Unit) { if (vm.dashboard.isEmpty()) vm.refreshHome() }
    val d = vm.dashboard
    var askReboot by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    val blockedMacs = vm.blocked.map { it.mac }.toSet()
    val online = vm.devices.count { it.mac !in blockedMacs }
    val bandsOn = vm.wifi.count { it.enabled }
    val mainBand = vm.wifi.firstOrNull { it.enabled && it.ssid.isNotBlank() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { StatusHero(d, vm.found?.model, vm.login.host) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.Devices, "$online", "Devices", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                    nav.go(2, "")
                }
                StatTile(
                    Icons.Rounded.Wifi, if (vm.wifi.isEmpty()) "—" else "$bandsOn/${vm.wifi.size}", "Wi-Fi on",
                    MaterialTheme.colorScheme.secondary, Modifier.weight(1f),
                ) { nav.go(1, "") }
                StatTile(Icons.Rounded.Block, "${vm.blocked.size}", "Blocked", Bad, Modifier.weight(1f)) { nav.go(2, "") }
            }
        }
        item { SectionLabel("Quick actions") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Rounded.QrCode2, "Share Wi-Fi", MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                    enabled = mainBand != null) { mainBand?.let { qr = it.ssid to it.password } }
                QuickAction(Icons.Rounded.Groups, "Guest Wi-Fi", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                    nav.go(3, "guest")
                }
                QuickAction(Icons.Rounded.Language, "Block sites", Bad, Modifier.weight(1f)) { nav.go(3, "sites") }
                QuickAction(Icons.Rounded.RestartAlt, "Reboot", Warn, Modifier.weight(1f), enabled = !vm.busy) { askReboot = true }
            }
        }
        if (d.isNotEmpty()) item { SectionLabel("Router details") }
        if (d.isEmpty() && !vm.busy) {
            item { EmptyState("No status yet", "Tap refresh at the top to read the router.", Icons.Rounded.Router) }
        }
        items(d) { SectionCard(it) }
    }

    qr?.let { (s, p) -> WifiQrDialog(s, p) { qr = null } }
    if (askReboot) RebootDialog(vm) { askReboot = false }
}

@Composable
internal fun RebootDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RestartAlt, null, tint = Warn) },
        title = { Text("Reboot router?") },
        text = { Text("Everyone loses internet and Wi-Fi for about 1-2 minutes. Sign in again after it is back.") },
        confirmButton = { TextButton(onClick = { onDismiss(); vm.reboot() }) { Text("Reboot", color = Warn) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusHero(d: List<Section>, detectedModel: String?, host: String) {
    val model = detectedModel ?: findValue(d, "model", "device name", "product") ?: "VSOL ONU"
    val rx = findValue(d, "rx power", "receive", "rx optical", "rx")
    val rxDbm = parseDbm(rx)
    val uptime = findValue(d, "uptime", "up time", "run time")
    val pon = findValue(d, "onu state", "pon state", "status", "state")
    // -30 dBm (empty) … -10 dBm (full)
    val level by animateFloatAsState(rxDbm?.let { ((it + 30) / 20).toFloat().coerceIn(0.03f, 1f) } ?: 0f, label = "rx")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(BrandGradient)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Router, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(model, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                if (pon != null) {
                    Text(
                        pon, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            // Optical signal meter
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.14f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Fiber signal (Rx)", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(rx ?: "—", color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(rxColor(rxDbm) ?: Color.White.copy(alpha = 0.5f)))
                        Spacer(Modifier.width(6.dp))
                        Text(rxLabel(rxDbm), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(level).height(8.dp).clip(CircleShape).background(rxColor(rxDbm) ?: Color.White))
                }
            }
            if (uptime != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Up for $uptime", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        IconBadge(icon, tint.copy(alpha = 0.14f), tint, size = 34)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(s: Section) {
    var open by rememberSaveable(s.title) { mutableStateOf(false) }
    val icon = when {
        s.title.contains("optical", true) || s.title.contains("pon", true) -> Icons.Rounded.Lan
        s.title.contains("internet", true) || s.title.contains("wan", true) -> Icons.Rounded.Public
        s.title.contains("wlan", true) || s.title.contains("wi", true) -> Icons.Rounded.Wifi
        else -> Icons.Rounded.Router
    }
    AppCard(padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.primary, size = 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text("${s.rows.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                for ((k, v) in s.rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        val isRx = k.contains("rx", true) || k.contains("receiv", true)
                        Text(
                            v, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = (if (isRx) rxColor(parseDbm(v)) else null) ?: Color.Unspecified,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}
