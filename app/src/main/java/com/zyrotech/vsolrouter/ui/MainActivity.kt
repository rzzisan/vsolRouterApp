package com.zyrotech.vsolrouter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

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
                val snack = remember { SnackbarHostState() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppLifecycle(vm)
                    AnimatedContent(
                        targetState = vm.loggedIn,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { loggedIn -> if (loggedIn) HomeScreen(vm, snack) else LoginScreen(vm) }
                    Messages(vm, snack)
                    WifiJoinPrompt(vm)
                }
                if (!vm.loggedIn) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                        SnackbarHost(snack)
                    }
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

/** Short confirmations slide in at the bottom; longer results and errors open a dialog. */
@Composable
private fun Messages(vm: MainViewModel, snack: SnackbarHostState) {
    val info = vm.info
    val short = info != null && info.length <= 110 && !info.contains('\n')
    // Own scope: clearing vm.info must not cancel the snackbar that is showing it.
    val scope = rememberCoroutineScope()
    LaunchedEffect(info) {
        if (info != null && short) {
            scope.launch { snack.showSnackbar(info) }
            vm.info = null
        }
    }
    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Bad) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(msg) },
        )
    }
    if (info != null && !short) {
        AlertDialog(
            onDismissRequest = { vm.info = null },
            icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Good) },
            confirmButton = { TextButton(onClick = { vm.info = null }) { Text("OK") } },
            title = { Text("Done") },
            text = { Text(info) },
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
            .padding(vertical = 10.dp),
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

// ------------------------------------------------------------------ home shell

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Rounded.Home),
    Tab("Wi-Fi", Icons.Rounded.Wifi),
    Tab("Devices", Icons.Rounded.Devices),
    Tab("More", Icons.Rounded.Apps),
)

/** Where the app can jump to from anywhere (quick actions on Home open More pages). */
class Nav(val tab: Int, val go: (tab: Int, page: String) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel, snack: SnackbarHostState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("") }
    var askLogout by remember { mutableStateOf(false) }
    val nav = Nav(tab) { t, p -> tab = t; page = p }
    if (tab != 0) BackHandler { tab = 0; page = "" }

    val cs = MaterialTheme.colorScheme
    val title = vm.routers.firstOrNull { it.id == vm.activeId }?.name ?: vm.found?.model ?: "My router"
    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
                                Spacer(Modifier.width(6.dp))
                                Text("Connected · ${vm.login.host}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            when (tab) {
                                0 -> vm.refreshHome(); 1 -> vm.refreshWifi(); 2 -> vm.refreshDevices(); else -> vm.loadTools()
                            }
                        }, enabled = !vm.busy) { Icon(Icons.Rounded.Refresh, "Refresh") }
                        IconButton(onClick = { askLogout = true }) { Icon(Icons.AutoMirrored.Rounded.Logout, "Log out") }
                    },
                )
                // Always reserve the bar's height so content does not jump when loading starts.
                Box(Modifier.fillMaxWidth().height(3.dp)) {
                    androidx.compose.animation.AnimatedVisibility(visible = vm.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), trackColor = cs.background)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surfaceContainer, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i; page = "" },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = cs.primaryContainer),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    0 -> StatusTab(vm, nav)
                    1 -> WifiTab(vm)
                    2 -> DevicesTab(vm)
                    else -> MoreTab(vm, page) { page = it }
                }
            }
        }
    }
    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            title = { Text("Log out?") },
            text = { Text("The router's login is released so a browser or another phone can sign in. Your saved login stays on this phone.") },
            confirmButton = { TextButton(onClick = { askLogout = false; vm.logout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Cancel") } },
        )
    }
}
