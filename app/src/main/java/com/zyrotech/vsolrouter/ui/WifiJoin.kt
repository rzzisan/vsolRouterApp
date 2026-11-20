package com.zyrotech.vsolrouter.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * After the Wi-Fi name/password changed, offer to save the new details on this phone so it
 * reconnects by itself. Android does not let apps change saved Wi-Fi silently:
 *  - Android 11+: the system "Save network?" screen (one tap on Save).
 *  - Android 10: a network suggestion (Android may ask once to allow it).
 *  - Android 8-9: the app adds the network directly.
 */
@Composable
fun WifiJoinPrompt(vm: MainViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val j = vm.wifiJoin
        vm.wifiJoin = null
        vm.info = if (r.resultCode == Activity.RESULT_OK) {
            "Saved on this phone. It reconnects to \"${j?.ssid}\" with the new password, then tap the router to sign in again " +
                "(the app retries until the router lets it in)."
        } else {
            "Not saved. Open Wi-Fi settings and connect to \"${j?.ssid}\" with the new password."
        }
    }
    val j = vm.wifiJoin
    if (j != null) {
        AlertDialog(
            onDismissRequest = { vm.wifiJoin = null },
            icon = { Icon(Icons.Rounded.Wifi, null) },
            title = { Text("Connect this phone again?") },
            text = {
                Text(
                    j.log + "\n\nThis phone was disconnected. Save \"${j.ssid}\" with the new password on this phone " +
                        "so it reconnects by itself?",
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val s = WifiNetworkSuggestion.Builder().setSsid(j.ssid).setWpa2Passphrase(j.password).build()
                        val i = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(s))
                        try { launcher.launch(i) } catch (e: Exception) {
                            vm.wifiJoin = null
                            vm.info = "Could not open the Wi-Fi save screen. Connect to \"${j.ssid}\" manually."
                        }
                    } else {
                        vm.wifiJoin = null
                        vm.info = saveLegacy(ctx, j.ssid, j.password)
                    }
                }) { Text("Save on this phone") }
            },
            dismissButton = { TextButton(onClick = { vm.wifiJoin = null }) { Text("Not now") } },
        )
    }
}

@Suppress("DEPRECATION")
private fun saveLegacy(ctx: Context, ssid: String, pass: String): String {
    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return try {
        if (Build.VERSION.SDK_INT == 29) {
            val s = WifiNetworkSuggestion.Builder().setSsid(ssid).setWpa2Passphrase(pass).setIsAppInteractionRequired(false).build()
            wm.removeNetworkSuggestions(listOf(s))
            val st = wm.addNetworkSuggestions(listOf(s))
            if (st == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                "Saved as a suggestion. Android connects to \"$ssid\" by itself (it may ask once to allow this app)."
            else "Android refused the suggestion (code $st). Connect to \"$ssid\" manually."
        } else {
            val c = WifiConfiguration().apply { SSID = "\"$ssid\""; preSharedKey = "\"$pass\"" }
            val id = wm.addNetwork(c)
            if (id >= 0) { wm.enableNetwork(id, true); wm.saveConfiguration(); "Saved. This phone connects to \"$ssid\" with the new password." }
            else "Android did not accept the network. Connect to \"$ssid\" manually."
        }
    } catch (e: Exception) {
        "Could not save the network (${e.message}). Connect to \"$ssid\" manually."
    }
}
