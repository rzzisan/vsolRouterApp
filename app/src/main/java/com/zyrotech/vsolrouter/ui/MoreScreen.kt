package com.zyrotech.vsolrouter.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zyrotech.vsolrouter.work.Background

@Composable
fun MoreTab(vm: MainViewModel, page: String, open: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.loadTools() }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    qr?.let { (s, p) -> WifiQrDialog(s, p) { qr = null } }
    when (page) {
        "guest" -> SubPage("Guest Wi-Fi", { open("") }) { GuestWifiCard(vm) { s, p -> qr = s to p } }
        "sites" -> SubPage("Block websites", { open("") }) { WebsiteBlockCard(vm) }
        "password" -> SubPage("Admin password", { open("") }) { AdminPasswordCard(vm) }
        "login" -> SubPage("Router login", { open("") }) { RouterLoginCard(vm) }
        else -> MoreMenu(vm, open)
    }
}

@Composable
private fun MoreMenu(vm: MainViewModel, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var askReboot by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateNotifyNew(granted)
        if (!granted) vm.error = "Notifications are blocked for this app. Allow them in Android settings to get alerts."
    }
    val g = vm.guest
    val sites = vm.sites
    val loginOk = vm.loginTimeout != null || vm.maxLogins != null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Network")
        SettingsGroup(
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        Icons.Rounded.Groups, "Guest Wi-Fi",
                        when {
                            !g.supported -> "A separate network for visitors"
                            g.enabled -> "On · ${g.ssid}"
                            else -> "Off"
                        },
                        tint = cs.tertiary, onClick = { open("guest") },
                    )
                },
                {
                    SettingsRow(
                        Icons.Rounded.Language, "Block websites",
                        when {
                            sites == null -> "Block sites for every device"
                            sites.sites.isEmpty() -> "No websites blocked"
                            else -> "${sites.sites.size} blocked"
                        },
                        tint = Bad, onClick = { open("sites") },
                    )
                },
            ),
        )

        SectionLabel("Router")
        SettingsGroup(
            listOf<@Composable () -> Unit>(
                { SettingsRow(Icons.Rounded.Lock, "Admin password", "Change the router's login password", onClick = { open("password") }) },
                {
                    SettingsRow(
                        Icons.Rounded.Timer, "Router login",
                        if (loginOk) "Idle timeout and logins allowed at once" else "Not available on this router",
                        tint = cs.secondary, enabled = loginOk, onClick = { open("login") },
                    )
                },
                {
                    SettingsRow(
                        Icons.Rounded.RestartAlt, "Reboot router", "Internet and Wi-Fi drop for about 1-2 minutes",
                        tint = Warn, enabled = !vm.busy, onClick = { askReboot = true },
                    )
                },
            ),
        )

        SectionLabel("App")
        SettingsGroup(
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        Icons.Rounded.NotificationsActive, "New device alerts",
                        "Notify when a device joins for the first time",
                        trailing = {
                            Switch(checked = vm.notifyNew, onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 && !Background.canNotify(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else vm.updateNotifyNew(on)
                            })
                        },
                    )
                },
                {
                    SettingsRow(
                        Icons.Rounded.Widgets, "Home-screen widget",
                        "Long-press the home screen → Widgets → VSOL Router",
                        tint = cs.secondary,
                    )
                },
                {
                    SettingsRow(
                        Icons.Rounded.Info, "About",
                        "${vm.found?.model ?: "VSOL"} at ${vm.login.host} · v${appVersion(ctx)}",
                        tint = cs.outline,
                    )
                },
            ),
        )
        if (vm.notifyNew) {
            Text(
                "Checked about every 15 minutes while this phone is on the router's Wi-Fi. Each check signs in for a few seconds and signs out again.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        CreditFooter()
    }
    if (askReboot) RebootDialog(vm) { askReboot = false }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Exception) { "?" }
