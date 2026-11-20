package com.zyrotech.vsolrouter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zyrotech.vsolrouter.net.Band
import com.zyrotech.vsolrouter.net.Device
import com.zyrotech.vsolrouter.net.RouterApi
import com.zyrotech.vsolrouter.net.Section
import com.zyrotech.vsolrouter.net.WifiPlan

const val CREDIT_TEXT = "Developed & Designed by Zisan"
const val CREDIT_URL = "https://facebook.com/zisanme"

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try { com.zyrotech.vsolrouter.work.Background.schedule(this) } catch (_: Exception) {}
        setContent {
            VsolTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppLifecycle(vm)
                    AnimatedContent(
                        targetState = vm.loggedIn,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { loggedIn -> if (loggedIn) HomeScreen(vm) else LoginScreen(vm) }
                    Messages(vm)
                    WifiJoinPrompt(vm)
                }
            }
        }
    }
}

/** Give the router's single login back while the app is in the background. */
@Composable
private fun AppLifecycle(vm: MainViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                Lifecycle.Event.ON_START -> vm.onAppForeground()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
private fun Messages(vm: MainViewModel) {
    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            icon = { Icon(Icons.Rounded.Block, null, tint = Bad) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(msg) },
        )
    }
    vm.info?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.info = null },
            icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Good) },
            confirmButton = { TextButton(onClick = { vm.info = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}

// ------------------------------------------------------------------ credit

@Composable
fun CreditFooter(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier
            .fillMaxWidth()
            .clickable { try { uri.openUri(CREDIT_URL) } catch (_: Exception) {} }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            CREDIT_TEXT,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Unofficial app · not made by VSOL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------ login

@Composable
private fun LoginScreen(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Column {
                Text("Hello!", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                Text("Router Manager", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Wi-Fi, devices and your ONU, from your phone.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Router on this Wi-Fi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.primary)
            RouterCard(vm)
            Text(
                "Connect this phone to the router's Wi-Fi. The router is found automatically. " +
                    "Sign in once; after that a tap on the router is enough.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            SavedRouters(vm)
            TextButton(onClick = { vm.newLogin() }, enabled = !vm.busy) { Text("+ Add another router") }
        }
        CreditFooter(Modifier.navigationBarsPadding().padding(bottom = 4.dp))
    }
    if (vm.askCredentials) CredentialsDialog(vm)
}

@Composable
private fun RouterCard(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val f = vm.found
    val searching = vm.probing || f == null
    val model = f?.model
    val ok = model != null
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(enabled = !searching && !vm.busy && vm.loginWait == 0) {
            if (ok) { vm.tapRouter() } else { vm.newLogin() }
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                    .background(
                        if (ok) Brush.linearGradient(listOf(cs.primary, cs.tertiary))
                        else Brush.linearGradient(listOf(cs.surfaceVariant, cs.surfaceVariant)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (searching || vm.busy || vm.loginWait > 0) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = if (ok) Color.White else cs.primary, strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Rounded.Router, null, tint = if (ok) Color.White else cs.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                when {
                    searching -> {
                        Text("Looking for your router…", fontWeight = FontWeight.SemiBold)
                        Text("Checking the Wi-Fi gateway", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    model != null -> {
                        Text(model, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f?.host.orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        StatusPill(
                            when {
                                vm.loginWait > 0 -> "Waiting for router… %d:%02d".format(vm.loginWait / 60, vm.loginWait % 60)
                                vm.busy -> "Signing in…"
                                vm.hasSavedLogin -> "Tap to connect"
                                else -> "Tap to sign in"
                            },
                            if (vm.loginWait > 0) Warn else Good,
                        )
                    }
                    else -> {
                        Text("No router found", fontWeight = FontWeight.SemiBold)
                        Text("Join the router's Wi-Fi, then retry. Or tap to enter the address.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (!searching && !ok) {
                FilledTonalIconButton(onClick = { vm.probe() }) { Icon(Icons.Rounded.Refresh, "Search again") }
            }
        }
    }
    if (vm.loginWait > 0) {
        Card(
            Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Warn.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(vm.loginWaitReason ?: "The router is busy.", style = MaterialTheme.typography.bodyMedium)
                Text("The app keeps trying every 15 seconds until the router lets it in (up to 6 minutes). " +
                    "Logging out of the router page in a browser makes it faster.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                TextButton(onClick = { vm.cancelLoginWait() }) { Text("Stop waiting") }
            }
        }
    } else if (ok && vm.hasSavedLogin && !vm.busy) {
        TextButton(onClick = { vm.askCredentials = true }) { Text("Use a different login") }
    }
}

@Composable
private fun CredentialsDialog(vm: MainViewModel) {
    val l = vm.login
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.askCredentials = false },
        icon = { Icon(Icons.Rounded.Router, null) },
        title = { Text(vm.found?.model ?: "Sign in to router") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vm.profileName, onValueChange = { vm.profileName = it },
                    label = { Text("Name (e.g. Home, Office)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Router, null) },
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = l.host, onValueChange = { vm.updateLogin(l.copy(host = it)) },
                    label = { Text("Router address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Public, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = l.username, onValueChange = { vm.updateLogin(l.copy(username = it)) },
                    label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = l.password, onValueChange = { vm.updateLogin(l.copy(password = it)) },
                    label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide" else "Show")
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                )
                Text("Saved on this phone (encrypted), so next time a tap on the router signs you in.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.doLogin() },
                enabled = !vm.busy && l.host.isNotBlank() && l.username.isNotBlank() && l.password.isNotEmpty(),
            ) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Sign in")
            }
        },
        dismissButton = { TextButton(onClick = { vm.askCredentials = false }, enabled = !vm.busy) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ home

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Status", Icons.Rounded.Router),
    Tab("Wi-Fi", Icons.Rounded.Wifi),
    Tab("Devices", Icons.Rounded.Devices),
    Tab("Tools", Icons.Rounded.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(TABS[tab].title, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = {
                        when (tab) { 0 -> vm.refreshDashboard(); 1 -> vm.refreshWifi(); 2 -> vm.refreshDevices(); else -> vm.loadTools() }
                    }, enabled = !vm.busy) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    IconButton(onClick = { vm.logout() }) { Icon(Icons.Rounded.PowerSettingsNew, "Log out") }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                CreditFooter()
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.title) }, label = { Text(t.title) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            AnimatedVisibility(vm.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    0 -> StatusTab(vm)
                    1 -> WifiTab(vm)
                    2 -> DevicesTab(vm)
                    else -> ToolsTab(vm)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ status

private fun findValue(sections: List<Section>, vararg keys: String): String? =
    sections.asSequence().flatMap { it.rows.asSequence() }
        .firstOrNull { (k, v) -> v.isNotBlank() && keys.any { k.contains(it, ignoreCase = true) } }?.second

private fun parseDbm(v: String?): Double? = v?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

@Composable
private fun StatusTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { if (vm.dashboard.isEmpty()) vm.refreshDashboard() }
    val d = vm.dashboard
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { StatusHero(d, vm.found?.model, vm.login.host) }
        if (d.isEmpty() && !vm.busy) item { EmptyState("No status yet", "Tap refresh to read the router.") }
        items(d) { SectionCard(it) }
    }
}

@Composable
private fun StatusHero(d: List<Section>, detectedModel: String?, host: String) {
    val cs = MaterialTheme.colorScheme
    val model = detectedModel ?: findValue(d, "model", "device name", "product") ?: "VSOL ONU"
    val rx = findValue(d, "rx power", "receive", "rx optical", "rx")
    val rxDbm = parseDbm(rx)
    val uptime = findValue(d, "uptime", "up time", "run time")
    val pon = findValue(d, "onu state", "pon state", "status", "state")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Router, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(model, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat("Optical Rx", rx ?: "—", Modifier.weight(1f), rxColor(rxDbm))
                HeroStat("PON", pon ?: "—", Modifier.weight(1f))
            }
            if (uptime != null) HeroStat("Uptime", uptime, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier, dot: Color? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(12.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionCard(s: Section) {
    val icon = when {
        s.title.contains("optical", true) || s.title.contains("pon", true) -> Icons.Rounded.Lan
        s.title.contains("internet", true) || s.title.contains("wan", true) -> Icons.Rounded.Public
        else -> Icons.Rounded.Router
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            for ((k, v) in s.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
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

/** Optical receive power: good above -25 dBm, weak to -27, bad below. */
private fun rxColor(dbm: Double?): Color? = when {
    dbm == null -> null
    dbm > -25 -> Good
    dbm > -27 -> Warn
    else -> Bad
}

@Composable
internal fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
internal fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------ wifi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiTab(vm: MainViewModel) {
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
    var show by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<WifiPlan?>(null) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()

    fun nameOk(n: String) = n.isEmpty() || RouterApi.isValidSsid(n)
    fun passOk(p: String) = p.isEmpty() || RouterApi.isValidPsk(p)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (b in listOfNotNull(b24, b5)) {
                ElevatedCard(Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                Icons.Rounded.Wifi,
                                if (b.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                if (b.enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 34,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(b.band.label, fontWeight = FontWeight.Bold)
                        }
                        Text(b.ssid.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (show) b.password.ifEmpty { "(open)" } else "••••••••", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(if (b.enabled) "On" else "Off", if (b.enabled) Good else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.weight(1f))
                            if (b.ssid.isNotBlank()) {
                                IconButton(onClick = { qr = b.ssid to b.password }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.QrCode2, "Share ${b.band.label} as QR code")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (vm.wifi.isEmpty() && !vm.busy) EmptyState("Wi-Fi not loaded", "Tap refresh to read the Wi-Fi settings.")

        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wi-Fi name & password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dual-band Smart Connect", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (smart) "One name and password for 2.4 GHz and 5 GHz" else "2.4 GHz and 5 GHz are set separately",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = smart, onCheckedChange = { smart = it })
                    }
                }
                if (smart) {
                    OutlinedTextField(name, { name = it.trim() }, label = { Text("Wi-Fi name") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Wifi, null) }, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(), isError = !nameOk(name))
                    OutlinedTextField(pass, { pass = it }, label = { Text("New password (blank = keep)") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Key, null) }, shape = MaterialTheme.shapes.small,
                        visualTransformation = vt, modifier = Modifier.fillMaxWidth(), isError = !passOk(pass))
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
                        OutlinedTextField(name24, { name24 = it.trim() }, label = { Text("2.4 GHz name") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Wifi, null) }, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(), isError = !nameOk(name24))
                        OutlinedTextField(pass24, { pass24 = it }, label = { Text("2.4 GHz password (blank = keep)") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Key, null) }, shape = MaterialTheme.shapes.small,
                            visualTransformation = vt, modifier = Modifier.fillMaxWidth(), isError = !passOk(pass24))
                    } else {
                        OutlinedTextField(name5, { name5 = it.trim() }, label = { Text("5 GHz name") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Wifi, null) }, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(), isError = !nameOk(name5))
                        OutlinedTextField(pass5, { pass5 = it }, label = { Text("5 GHz password (blank = keep)") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Key, null) }, shape = MaterialTheme.shapes.small,
                            visualTransformation = vt, modifier = Modifier.fillMaxWidth(), isError = !passOk(pass5))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(show, { show = it }); Text("Show passwords", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Name: up to 32 characters, no spaces. Password: 8 to 63 characters.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val valid = if (smart) name.isNotEmpty() && nameOk(name) && passOk(pass)
                    else nameOk(name24) && nameOk(name5) && passOk(pass24) && passOk(pass5)
                Button(
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
                    enabled = !vm.busy && valid, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.small,
                ) { Text("Save changes", fontWeight = FontWeight.SemiBold) }
                for (line in vm.wifiLog) Text(line, style = MaterialTheme.typography.bodySmall)
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

// ------------------------------------------------------------------ devices

@Composable
private fun DevicesTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { if (vm.devices.isEmpty()) vm.refreshDevices() }
    val ctx = LocalContext.current
    val myIp = remember { MainViewModel.phoneIp(ctx) }
    var toBlock by remember { mutableStateOf<Device?>(null) }
    var toLimit by remember { mutableStateOf<Device?>(null) }
    var toSchedule by remember { mutableStateOf<Device?>(null) }
    val blockedMacs = vm.blocked.map { it.mac }.toSet()
    val canBlock = vm.blockNote?.contains("Whitelist") != true
    val visible = vm.devices.filter { it.mac !in blockedMacs }
    val wifiCount = visible.count { it.band != null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountTile("Connected", visible.size, Icons.Rounded.Devices, Modifier.weight(1f))
                CountTile("On Wi-Fi", wifiCount, Icons.Rounded.Wifi, Modifier.weight(1f))
                CountTile("Blocked", vm.blocked.size, Icons.Rounded.Block, Modifier.weight(1f), Bad)
            }
        }
        if (vm.blocked.isNotEmpty() || vm.blockNote != null) {
            item { SectionLabel("Blocked") }
            vm.blockNote?.let { n ->
                item { Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vm.blocked, key = { "b-" + it.mac }) { e ->
                Card(
                    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = Bad.copy(alpha = 0.08f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Block, Bad.copy(alpha = 0.15f), Bad)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name ?: vm.devices.firstOrNull { it.mac == e.mac }?.name ?: "Unknown device", fontWeight = FontWeight.SemiBold)
                            Text(e.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { vm.unblock(e) }, enabled = !vm.busy) { Text("Unblock") }
                    }
                }
            }
        }
        item { SectionLabel("Connected devices") }
        if (visible.isEmpty() && !vm.busy) item { EmptyState("No devices", "Tap refresh to load the device list.") }
        items(visible, key = { it.mac }) { d ->
            val isMe = myIp != null && d.ip == myIp
            DeviceRow(
                d, isMe,
                blockEnabled = canBlock && !isMe && !vm.busy,
                limit = speedSummary(vm.speedLimits.orEmpty().filter { it.ip != null && it.ip == d.ip }),
                schedule = scheduleSummary(vm.schedules?.get(RouterApi.scheduleName(d.mac))),
                onLimit = if (vm.speedLimits != null && d.ip != null) ({ toLimit = d }) else null,
                onSchedule = if (vm.schedules != null) ({ toSchedule = d }) else null,
                onBlock = { toBlock = d },
            )
        }
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

@Composable
internal fun SectionLabel(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CountTile(label: String, n: Int, icon: ImageVector, modifier: Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    ElevatedCard(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text("$n", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun deviceIcon(d: Device, isMe: Boolean): ImageVector {
    val n = d.name.orEmpty().lowercase()
    return when {
        isMe -> Icons.Rounded.PhoneAndroid
        listOf("desktop", "laptop", "pc", "macbook", "windows").any { n.contains(it) } -> Icons.Rounded.Computer
        listOf("phone", "galaxy", "redmi", "iphone", "android", "oppo", "vivo", "realme", "pixel", "note", "xiaomi", "samsung")
            .any { n.contains(it) } -> Icons.Rounded.PhoneAndroid
        d.band == null -> Icons.Rounded.Lan
        else -> Icons.Rounded.Devices
    }
}

@Composable
private fun DeviceRow(
    d: Device,
    isMe: Boolean,
    blockEnabled: Boolean,
    limit: String?,
    schedule: String?,
    onLimit: (() -> Unit)?,
    onSchedule: (() -> Unit)?,
    onBlock: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                deviceIcon(d, isMe),
                if (isMe) cs.primary else cs.secondaryContainer,
                if (isMe) cs.onPrimary else cs.onSecondaryContainer,
                size = 44,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name ?: "Unknown device", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("This phone", cs.primary)
                    }
                }
                Text(listOfNotNull(d.ip, d.mac).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val band = d.band
                    if (band != null) {
                        SignalBars(d.rssi)
                        Spacer(Modifier.width(6.dp))
                        Text(band.label + (d.rssi?.let { " · $it dBm" } ?: ""), style = MaterialTheme.typography.labelMedium, color = Good)
                    } else {
                        Text("Wired / not on Wi-Fi now", style = MaterialTheme.typography.labelMedium, color = cs.outline)
                    }
                }
                if (limit != null || schedule != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        limit?.let { StatusPill(it, cs.tertiary) }
                        schedule?.let { StatusPill(it, Warn) }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for ${d.name ?: d.mac}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Speed limit") }, enabled = onLimit != null,
                        leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                        onClick = { menu = false; onLimit?.invoke() },
                    )
                    DropdownMenuItem(
                        text = { Text("Internet time") }, enabled = onSchedule != null,
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                        onClick = { menu = false; onSchedule?.invoke() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isMe) "Block (not this phone)" else "Block", color = if (blockEnabled) Bad else cs.outline) },
                        enabled = blockEnabled,
                        leadingIcon = { Icon(Icons.Rounded.Block, null, tint = if (blockEnabled) Bad else cs.outline) },
                        onClick = { menu = false; onBlock() },
                    )
                }
            }
        }
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
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) {
            Box(
                Modifier.width(4.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i <= level) Good else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

// ------------------------------------------------------------------ tools

@Composable
private fun ToolsTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.loadTools() }
    var page by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    qr?.let { (s, p) -> WifiQrDialog(s, p) { qr = null } }
    if (page == "guest") {
        androidx.activity.compose.BackHandler { page = "" }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(onClick = { page = "" }) { Text("‹ Back to Tools") }
            GuestWifiCard(vm) { s, p -> qr = s to p }
        }
    } else ToolsMenu(vm) { page = it }
}

@Composable
private fun ToolsMenu(vm: MainViewModel, open: (String) -> Unit) {
    var askReboot by remember { mutableStateOf(false) }
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ElevatedCard(
            Modifier.fillMaxWidth().clickable { vm.refreshGuest(); open("guest") },
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Wifi, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Guest Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !vm.guest.supported -> "A separate network for visitors"
                            vm.guest.enabled -> "On · ${vm.guest.ssid}"
                            else -> "Off"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.RestartAlt, Warn.copy(alpha = 0.15f), Warn)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reboot router", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Internet and Wi-Fi drop for about 1-2 minutes.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { askReboot = true }, enabled = !vm.busy) { Text("Reboot") }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Lock, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text("Router admin password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(old, { old = it }, label = { Text("Current password") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new1, { new1 = it }, label = { Text("New password") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new2, { new2 = it }, label = { Text("Repeat new password") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    isError = new2.isNotEmpty() && new1 != new2)
                Button(
                    onClick = { vm.changeAdminPassword(old, new1); old = ""; new1 = ""; new2 = "" },
                    enabled = !vm.busy && old.isNotEmpty() && new1.length >= 6 && new1 == new2,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                ) { Text("Change password") }
                Text("6 to 32 characters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WebsiteBlockCard(vm)
        AlertsCard(vm)
        RouterLoginCard(vm)
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Router: ${vm.found?.model ?: "VSOL"} at ${vm.login.host}", style = MaterialTheme.typography.bodySmall)
                Text("This app is not made or endorsed by VSOL.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (askReboot) {
        AlertDialog(
            onDismissRequest = { askReboot = false },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text("Reboot router?") },
            text = { Text("Everyone loses internet for about 1-2 minutes.") },
            confirmButton = { TextButton(onClick = { askReboot = false; vm.reboot() }) { Text("Reboot") } },
            dismissButton = { TextButton(onClick = { askReboot = false }) { Text("Cancel") } },
        )
    }
}
