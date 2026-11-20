package com.zyrotech.vsolrouter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- gradient header
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(BrandGradient)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 76.dp),
        ) {
            Column {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Router, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(18.dp))
                Text("VSOL Router", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Manage Wi-Fi, devices and your ONU from your phone.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ---- content, pulled up over the header
        Column(
            Modifier.offset(y = (-52).dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RouterCard(vm)
            SavedRouters(vm)
            OutlinedButton(
                onClick = { vm.newLogin() }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add another router")
            }
            InfoNote(
                "Connect this phone to the router's Wi-Fi and it is found automatically. Sign in once; after that one tap is enough.",
                icon = Icons.Rounded.Shield,
            )
        }
        CreditFooter(Modifier.navigationBarsPadding().padding(bottom = 4.dp))
    }
    if (vm.askCredentials) CredentialsDialog(vm)
}

/** Soft rings that pulse around the router icon while the app is looking for it. */
@Composable
private fun PulsingIcon(active: Boolean, ok: Boolean) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier.size(84.dp).scale(0.6f + 0.4f * p).alpha(1f - p)
                    .clip(CircleShape).background(cs.primary.copy(alpha = 0.35f)),
            )
        }
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(if (ok) BrandGradient else androidx.compose.ui.graphics.SolidColor(cs.surfaceContainerHigh)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active && !ok) Icons.Rounded.WifiFind else Icons.Rounded.Router, null,
                tint = if (ok) Color.White else cs.primary, modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun RouterCard(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val f = vm.found
    val searching = vm.probing || f == null
    val model = f?.model
    val ok = model != null
    val working = vm.busy || vm.loginWait > 0
    AppCard(padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingIcon(active = searching || working, ok = ok)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                when {
                    searching -> {
                        Text("Looking for your router…", style = MaterialTheme.typography.titleMedium)
                        Text("Checking the Wi-Fi gateway", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    model != null -> {
                        Text("Router found", style = MaterialTheme.typography.labelMedium, color = Good)
                        Text(model, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f?.host.orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    else -> {
                        Text("No router found", style = MaterialTheme.typography.titleMedium)
                        Text("Join the router's Wi-Fi and search again, or enter its address.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            searching -> {}
            ok -> PrimaryButton(
                text = when {
                    vm.loginWait > 0 -> "Waiting for router… %d:%02d".format(vm.loginWait / 60, vm.loginWait % 60)
                    vm.hasSavedLogin -> "Connect"
                    else -> "Sign in"
                },
                onClick = { vm.tapRouter() },
                enabled = vm.loginWait == 0,
                loading = vm.busy,
                icon = Icons.AutoMirrored.Rounded.Login,
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.probe() }, modifier = Modifier.weight(1f).height(50.dp), shape = MaterialTheme.shapes.small,
                ) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Search again") }
                PrimaryButton("Enter address", { vm.newLogin() }, Modifier.weight(1f), enabled = !vm.busy)
            }
        }
        if (vm.loginWait > 0) {
            Spacer(Modifier.height(12.dp))
            InfoNote(
                (vm.loginWaitReason ?: "The router is busy.") +
                    "\nThe app tries again every 15 seconds (up to 6 minutes). Logging out of the router page in a browser makes it faster.",
                color = Warn, icon = Icons.Rounded.HourglassTop,
            )
            TextButton(onClick = { vm.cancelLoginWait() }) { Text("Stop waiting") }
        } else if (ok && vm.hasSavedLogin && !vm.busy) {
            TextButton(onClick = { vm.askCredentials = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Use a different login")
            }
        }
    }
}

@Composable
private fun CredentialsDialog(vm: MainViewModel) {
    val l = vm.login
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.askCredentials = false },
        icon = { Icon(Icons.Rounded.Router, null) },
        title = { Text(vm.found?.model ?: "Sign in to router", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(vm.profileName, { vm.profileName = it }, "Name (e.g. Home, Office)", icon = Icons.Rounded.Router)
                AppField(
                    l.host, { vm.updateLogin(l.copy(host = it)) }, "Router address", icon = Icons.Rounded.Public,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                AppField(
                    l.username, { vm.updateLogin(l.copy(username = it)) }, "Username", icon = Icons.Rounded.Person,
                    keyboard = KeyboardOptions(imeAction = ImeAction.Next),
                )
                PasswordField(
                    l.password, { vm.updateLogin(l.copy(password = it)) }, "Password", icon = Icons.Rounded.Lock,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                Text("Saved on this phone (encrypted), so next time one tap signs you in.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.doLogin() },
                enabled = !vm.busy && l.host.isNotBlank() && l.username.isNotBlank() && l.password.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
            ) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Sign in")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.askCredentials = false }, enabled = !vm.busy) { Text("Cancel") }
        },
    )
}
