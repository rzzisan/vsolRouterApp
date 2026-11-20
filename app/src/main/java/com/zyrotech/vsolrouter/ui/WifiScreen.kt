package com.zyrotech.vsolrouter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrotech.vsolrouter.net.Band
import com.zyrotech.vsolrouter.net.RouterApi
import com.zyrotech.vsolrouter.net.WifiBandInfo
import com.zyrotech.vsolrouter.net.WifiPlan

internal fun copyText(ctx: Context, label: String, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { if (vm.wifi.isEmpty()) vm.refreshWifi() }
    // Smart Connect is on by default; once the router's real state is read, follow it.
    var smart by remember { mutableStateOf(true) }
    LaunchedEffect(vm.smart) { if (vm.smart.supported) smart = vm.smart.enabled }
    val b24 = vm.wifi.firstOrNull { it.band == Band.GHZ_2_4 }
    val b5 = vm.wifi.firstOrNull { it.band == Band.GHZ_5 }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name24 by remember { mutableStateOf("") }
    var pass24 by remember { mutableStateOf("") }
    var name5 by remember { mutableStateOf("") }
    var pass5 by remember { mutableStateOf("") }
    var sepBand by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.wifi, vm.smart) {
        name = vm.smart.ssid.ifBlank { b24?.ssid ?: b5?.ssid ?: "" }
        name24 = b24?.ssid.orEmpty(); name5 = b5?.ssid.orEmpty()
    }
    var confirm by remember { mutableStateOf<WifiPlan?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun nameOk(n: String) = n.isEmpty() || RouterApi.isValidSsid(n)
    fun passOk(p: String) = p.isEmpty() || RouterApi.isValidPsk(p)
    val passHint = "8 to 63 characters. Leave blank to keep the current one."

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (b in listOfNotNull(b24, b5)) BandCard(b) { qr = b.ssid to b.password }
        if (vm.wifi.isEmpty() && !vm.busy) EmptyState("Wi-Fi not loaded", "Tap refresh at the top to read the Wi-Fi settings.", Icons.Rounded.Wifi)

        SectionLabel("Change name & password")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CardHeader(
                    Icons.Rounded.AutoAwesome, "Dual-band Smart Connect",
                    if (smart) "One name and password for 2.4 GHz and 5 GHz" else "2.4 GHz and 5 GHz are set separately",
                    tint = MaterialTheme.colorScheme.tertiary,
                ) { Switch(checked = smart, onCheckedChange = { smart = it }) }
                if (smart) {
                    AppField(name, { name = it.trim() }, "Wi-Fi name", icon = Icons.Rounded.Edit, isError = !nameOk(name),
                        supporting = "Up to 32 characters, no spaces.")
                    PasswordField(pass, { pass = it }, "New password", icon = Icons.Rounded.Key, isError = !passOk(pass), supporting = passHint)
                } else {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("2.4 GHz", "5 GHz").forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = sepBand == i, onClick = { sepBand = i },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(t) }
                        }
                    }
                    if (sepBand == 0) {
                        AppField(name24, { name24 = it.trim() }, "2.4 GHz name", icon = Icons.Rounded.Edit, isError = !nameOk(name24))
                        PasswordField(pass24, { pass24 = it }, "2.4 GHz password", icon = Icons.Rounded.Key, isError = !passOk(pass24), supporting = passHint)
                    } else {
                        AppField(name5, { name5 = it.trim() }, "5 GHz name", icon = Icons.Rounded.Edit, isError = !nameOk(name5))
                        PasswordField(pass5, { pass5 = it }, "5 GHz password", icon = Icons.Rounded.Key, isError = !passOk(pass5), supporting = passHint)
                    }
                }
                val valid = if (smart) name.isNotEmpty() && nameOk(name) && passOk(pass)
                    else nameOk(name24) && nameOk(name5) && passOk(pass24) && passOk(pass5)
                PrimaryButton(
                    "Save changes",
                    onClick = {
                        confirm = if (smart) WifiPlan(true, sharedName = name, sharedPassword = pass.ifEmpty { null })
                        else WifiPlan(
                            false,
                            names = buildMap {
                                if (name24.isNotEmpty() && name24 != b24?.ssid) put(Band.GHZ_2_4, name24)
                                if (name5.isNotEmpty() && name5 != b5?.ssid) put(Band.GHZ_5, name5)
                            },
                            passwords = buildMap {
                                if (pass24.isNotEmpty()) put(Band.GHZ_2_4, pass24)
                                if (pass5.isNotEmpty()) put(Band.GHZ_5, pass5)
                            },
                        )
                    },
                    enabled = !vm.busy && valid,
                    icon = Icons.Rounded.Save,
                )
                if (vm.wifiLog.isNotEmpty()) InfoNote(vm.wifiLog.joinToString("\n"))
                if (!vm.smart.supported && vm.wifi.isNotEmpty()) {
                    Text("This router has no built-in Smart Connect page, so the app writes the same name and password to each band.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    qr?.let { (s, p) -> WifiQrDialog(s, p) { qr = null } }
    confirm?.let { plan ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.Wifi, null) },
            title = { Text("Save Wi-Fi settings?") },
            text = {
                Text(
                    (if (plan.smartConnect) "Smart Connect ON: both bands become \"${plan.sharedName}\"." else "Smart Connect OFF: each band keeps its own name and password.") +
                        "\n\nWi-Fi restarts. Your phone and other devices will disconnect and must reconnect.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.saveWifi(plan); confirm = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BandCard(b: WifiBandInfo, onQr: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var show by remember { mutableStateOf(false) }
    val tint = if (b.enabled) (if (b.band == Band.GHZ_5) cs.tertiary else cs.primary) else cs.outline
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (b.enabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, tint.copy(alpha = 0.14f), tint, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.band.label, style = MaterialTheme.typography.labelLarge, color = tint)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(if (b.enabled) "On" else "Off", if (b.enabled) Good else cs.outline)
                }
                Text(b.ssid.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (b.ssid.isNotBlank()) {
                FilledTonalIconButton(onClick = onQr) { Icon(Icons.Rounded.QrCode2, "Share ${b.band.label} as QR code") }
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(
            Modifier.fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Key, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (b.password.isEmpty()) "Open network (no password)" else if (show) b.password else "••••••••••",
                style = MaterialTheme.typography.bodyLarge, fontFamily = if (show) FontFamily.Monospace else null,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (b.password.isNotEmpty()) {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password")
                }
                IconButton(onClick = { copyText(ctx, "Password", b.password) }) { Icon(Icons.Rounded.ContentCopy, "Copy password") }
            }
        }
    }
}
