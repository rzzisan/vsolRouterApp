package com.zyrotech.vsolrouter.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrotech.vsolrouter.net.BlockedEntry
import com.zyrotech.vsolrouter.net.Device
import com.zyrotech.vsolrouter.net.RouterApi

private val FILTERS = listOf("All", "Wi-Fi", "Wired", "Blocked")

@Composable
fun DevicesTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { if (vm.devices.isEmpty()) vm.refreshDevices() }
    val ctx = LocalContext.current
    val myIp = remember { MainViewModel.phoneIp(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Device?>(null) }
    var toBlock by remember { mutableStateOf<Device?>(null) }
    var toLimit by remember { mutableStateOf<Device?>(null) }
    var toSchedule by remember { mutableStateOf<Device?>(null) }
    val blockedMacs = vm.blocked.map { it.mac }.toSet()
    val canBlock = vm.blockNote?.contains("Whitelist") != true
    val connected = vm.devices.filter { it.mac !in blockedMacs }
    val q = query.trim()
    fun matches(vararg s: String?) = q.isEmpty() || s.any { it?.contains(q, ignoreCase = true) == true }
    val shown = when (filter) {
        1 -> connected.filter { it.band != null }
        2 -> connected.filter { it.band == null }
        3 -> emptyList()
        else -> connected
    }.filter { matches(it.name, it.ip, it.mac) }
    val shownBlocked = if (filter == 0 || filter == 3) vm.blocked.filter { matches(it.name, it.mac) } else emptyList()
    val counts = listOf(connected.size, connected.count { it.band != null }, connected.count { it.band == null }, vm.blocked.size)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search name, IP or MAC") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                shape = RoundedCornerShape(50), colors = appFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FILTERS.size) { i ->
                    FilterChip(
                        selected = filter == i, onClick = { filter = i },
                        label = { Text("${FILTERS[i]} · ${counts[i]}") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        vm.blockNote?.let { n -> item { InfoNote(n, color = Warn) } }
        if (shownBlocked.isNotEmpty()) {
            item { SectionLabel("Blocked") }
            items(shownBlocked, key = { "b-" + it.mac }) { e -> BlockedRow(vm, e) }
        }
        if (filter != 3) {
            item { SectionLabel(if (filter == 0) "Connected now" else FILTERS[filter]) }
            if (shown.isEmpty() && !vm.busy) {
                item {
                    EmptyState(
                        if (q.isEmpty()) "No devices" else "No match",
                        if (q.isEmpty()) "Tap refresh at the top to load the device list." else "No device matches \"$q\".",
                        Icons.Rounded.Devices,
                    )
                }
            }
            items(shown, key = { it.mac }) { d ->
                val isMe = myIp != null && d.ip == myIp
                DeviceRow(
                    d, isMe,
                    limit = speedSummary(vm.speedLimits.orEmpty().filter { it.ip == d.ip }),
                    schedule = scheduleSummary(vm.schedules?.get(RouterApi.scheduleName(d.mac))),
                ) { selected = d }
            }
        } else if (shownBlocked.isEmpty() && !vm.busy) {
            item { EmptyState("Nothing blocked", "Devices you block appear here, so you can unblock them later.", Icons.Rounded.Block) }
        }
    }

    selected?.let { d ->
        val isMe = myIp != null && d.ip == myIp
        DeviceSheet(
            vm, d, isMe,
            blockEnabled = canBlock && !isMe && !vm.busy,
            onDismiss = { selected = null },
            onLimit = if (vm.speedLimits != null && d.ip != null) ({ selected = null; toLimit = d }) else null,
            onSchedule = if (vm.schedules != null) ({ selected = null; toSchedule = d }) else null,
            onBlock = { selected = null; toBlock = d },
        )
    }
    toLimit?.let { d -> SpeedLimitDialog(vm, d) { toLimit = null } }
    toSchedule?.let { d -> ScheduleDialog(vm, d) { toSchedule = null } }
    toBlock?.let { d ->
        AlertDialog(
            onDismissRequest = { toBlock = null },
            icon = { Icon(Icons.Rounded.Block, null, tint = Bad) },
            title = { Text("Block ${d.name ?: d.mac}?") },
            text = { Text("This device will lose internet through this router until you unblock it.\n\nMAC: ${d.mac}") },
            confirmButton = { TextButton(onClick = { vm.block(d); toBlock = null }) { Text("Block", color = Bad) } },
            dismissButton = { TextButton(onClick = { toBlock = null }) { Text("Cancel") } },
        )
    }
}

private fun deviceIcon(d: Device, isMe: Boolean): ImageVector {
    val n = d.name.orEmpty().lowercase()
    return when {
        isMe -> Icons.Rounded.PhoneAndroid
        listOf("desktop", "laptop", "pc", "macbook", "windows").any { n.contains(it) } -> Icons.Rounded.Computer
        listOf("tv", "android-tv", "chromecast", "firetv", "roku").any { n.contains(it) } -> Icons.Rounded.Tv
        listOf("phone", "galaxy", "redmi", "iphone", "android", "oppo", "vivo", "realme", "pixel", "note", "xiaomi", "samsung")
            .any { n.contains(it) } -> Icons.Rounded.PhoneAndroid
        d.band == null -> Icons.Rounded.Lan
        else -> Icons.Rounded.Devices
    }
}

@Composable
private fun DeviceRow(d: Device, isMe: Boolean, limit: String?, schedule: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                deviceIcon(d, isMe),
                if (isMe) cs.primary else cs.primary.copy(alpha = 0.10f),
                if (isMe) cs.onPrimary else cs.primary,
                size = 46,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("You", cs.primary)
                    }
                }
                Text(d.ip ?: d.mac, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (limit != null || schedule != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        limit?.let { StatusPill(it, cs.tertiary) }
                        schedule?.let { StatusPill(it, Warn) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val band = d.band
                if (band != null) {
                    SignalBars(d.rssi)
                    Text(band.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                } else {
                    Icon(Icons.Rounded.Lan, "Wired", tint = cs.outline)
                    Text("Wired", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(vm: MainViewModel, e: BlockedEntry) {
    AppCard(color = Bad.copy(alpha = 0.06f), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Block, Bad.copy(alpha = 0.15f), Bad, size = 46)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name ?: vm.devices.firstOrNull { it.mac == e.mac }?.name ?: "Unknown device",
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { vm.unblock(e) }, enabled = !vm.busy) {
                Icon(Icons.Rounded.LockOpen, null, Modifier.padding(end = 6.dp)); Text("Unblock")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSheet(
    vm: MainViewModel,
    d: Device,
    isMe: Boolean,
    blockEnabled: Boolean,
    onDismiss: () -> Unit,
    onLimit: (() -> Unit)?,
    onSchedule: (() -> Unit)?,
    onBlock: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val limit = speedSummary(vm.speedLimits.orEmpty().filter { it.ip == d.ip })
    val schedule = scheduleSummary(vm.schedules?.get(RouterApi.scheduleName(d.mac)))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(deviceIcon(d, isMe), cs.primary, cs.onPrimary, size = 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name ?: "Unknown device", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (isMe) "This phone" else if (d.band != null) "On Wi-Fi" else "Wired / not on Wi-Fi now",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(cs.surfaceContainerHigh).padding(vertical = 4.dp),
            ) {
                DetailRow("IP address", d.ip ?: "—") { d.ip?.let { copyText(ctx, "IP address", it) } }
                DetailRow("MAC address", d.mac) { copyText(ctx, "MAC address", d.mac) }
                d.band?.let { b -> DetailRow("Band", b.label + (d.rssi?.let { " · $it dBm" } ?: ""), null) }
            }
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(cs.surfaceContainerHigh)) {
                SettingsRow(
                    Icons.Rounded.Speed, "Speed limit",
                    limit ?: if (onLimit == null) "Not available for this device" else "No limit",
                    tint = cs.tertiary, enabled = onLimit != null, onClick = { onLimit?.invoke() },
                )
                SettingsRow(
                    Icons.Rounded.Schedule, "Internet time",
                    schedule ?: if (onSchedule == null) "Not available on this router" else "Always allowed",
                    tint = Warn, enabled = onSchedule != null, onClick = { onSchedule?.invoke() },
                )
                SettingsRow(
                    Icons.Rounded.Block, "Block this device",
                    when {
                        isMe -> "You cannot block the phone running the app"
                        !blockEnabled -> "Not available right now"
                        else -> "Stops its internet until you unblock it"
                    },
                    tint = if (blockEnabled) Bad else cs.outline, enabled = blockEnabled, onClick = onBlock,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (onCopy != null) {
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label", Modifier.padding(2.dp), tint = MaterialTheme.colorScheme.outline) }
        } else Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun SignalBars(rssi: Int?) {
    val level = when {
        rssi == null -> 0
        rssi > -55 -> 4
        rssi > -65 -> 3
        rssi > -75 -> 2
        else -> 1
    }
    val color = when (level) { 4, 3 -> Good; 2 -> Warn; 1 -> Bad; else -> MaterialTheme.colorScheme.outline }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) {
            Box(
                Modifier.width(4.dp).height((5 + i * 3).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i <= level) color else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
