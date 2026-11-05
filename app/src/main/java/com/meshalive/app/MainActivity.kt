package com.meshalive.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meshalive.app.mesh.MeshService
import com.meshalive.app.ui.MeshViewModel
import com.meshalive.app.ui.LocalLang
import com.meshalive.app.ui.Permissions
import com.meshalive.app.ui.translate
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import com.meshalive.app.ui.SimulatorViewModel
import com.meshalive.app.ui.components.WarningBanner
import com.meshalive.app.ui.screens.AboutScreen
import com.meshalive.app.ui.screens.ChatScreen
import com.meshalive.app.ui.screens.HomeScreen
import com.meshalive.app.ui.screens.ImpactScreen
import com.meshalive.app.ui.screens.OnboardingScreen
import com.meshalive.app.ui.screens.InboxScreen
import com.meshalive.app.ui.screens.MessagesScreen
import com.meshalive.app.ui.screens.NetworkMapScreen
import com.meshalive.app.ui.screens.PermissionScreen
import com.meshalive.app.ui.screens.SettingsScreen
import com.meshalive.app.ui.screens.SimulatorScreen
import com.meshalive.app.ui.theme.MeshAliveTheme
import com.meshalive.app.ui.theme.SosRed
import com.meshalive.app.ui.theme.Navy
import com.meshalive.app.ui.theme.Paper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val meshVm: MeshViewModel by viewModels()
    private val simVm: SimulatorViewModel by viewModels()

    /** Screen requested by a notification tap. */
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            MeshAliveTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(meshVm, simVm, requestedRoute.value) { requestedRoute.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { requestedRoute.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun Root(meshVm: MeshViewModel, simVm: SimulatorViewModel, requestedRoute: String?, onRouteHandled: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permsOk by remember { mutableStateOf(Permissions.allRequiredGranted(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        permsOk = Permissions.allRequiredGranted(context)
        refresh++
    }
    // Re-check whenever the user comes back (e.g. from the system settings page).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            permsOk = Permissions.allRequiredGranted(context)
            refresh++
            meshVm.refreshFlags()
        }
    }
    LaunchedEffect(permsOk) {
        if (permsOk) MeshService.start(context)
    }

    val appSettings by meshVm.settings.collectAsStateWithLifecycle()
    if (!appSettings.onboarded) {
        OnboardingScreen(appSettings) { meshVm.updateSettings(it) }
        return
    }
    if (!permsOk && !skipped) {
        PermissionScreen(
            groups = Permissions.groups(),
            grantedCheck = { refresh.let { _ -> Permissions.groupGranted(context, it) } },
            askedOnce = askedOnce,
            onRequest = { launcher.launch(Permissions.allRequested()) },
            onContinueLimited = { skipped = true },
        )
    } else {
        MainScaffold(meshVm, simVm, meshEnabled = permsOk, onFixPermissions = { skipped = false }, requestedRoute, onRouteHandled)
    }
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    meshVm: MeshViewModel,
    simVm: SimulatorViewModel,
    meshEnabled: Boolean,
    onFixPermissions: () -> Unit,
    requestedRoute: String?,
    onRouteHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val settings by meshVm.settings.collectAsStateWithLifecycle()
    val toast by meshVm.toast.collectAsStateWithLifecycle()
    val meshState by meshVm.state.collectAsStateWithLifecycle()
    val lang = settings.lang
    val context = LocalContext.current
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); meshVm.consumeToast() }
    }

    val bottom = buildList {
        add(Dest("home", "Home", Icons.Filled.Home))
        add(Dest("messages", "Messages", Icons.Filled.Email))
        add(Dest("network", "Network", Icons.Filled.Share))
        if (settings.gatewayMode || meshState.isGateway) add(Dest("inbox", "Inbox", Icons.Filled.Notifications))
        add(Dest("impact", "Impact", Icons.Filled.Insights))
    }
    val titles = mapOf(
        "home" to "MeshAlive", "messages" to "Messages", "network" to "Network map", "inbox" to "Gateway inbox",
        "simulator" to "Disaster simulator", "impact" to "Impact", "settings" to "Settings", "about" to "About",
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"
    val chatPeer = backStack?.arguments?.getString("id")
    val isSubScreen = current.startsWith("chat") || current == "settings" || current == "about"

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val onHome = current == "home"
    val barContent = if (onHome) Color.White else MaterialTheme.colorScheme.onSurface
    val activity = context as? android.app.Activity
    SideEffect {
        activity?.window?.let { w ->
            w.statusBarColor = (if (onHome) Navy else Paper).toArgb()
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = !onHome
        }
    }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) { go(requestedRoute); onRouteHandled() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current.startsWith("chat")) meshVm.nameOf(chatPeer ?: "") else titles[current] ?: "MeshAlive",
                        fontWeight = FontWeight.Bold,
                        color = if (current == "home") Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (isSubScreen) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { meshVm.updateSettings { it.copy(lang = if (it.lang == "ta") "en" else "ta") } }) {
                        Text(if (lang == "ta") "EN" else "தமிழ்", fontWeight = FontWeight.Bold, color = barContent)
                    }
                    IconButton(onClick = { go("about") }) { Icon(Icons.Filled.Info, "About", tint = barContent) }
                    IconButton(onClick = { go("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = barContent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (onHome) Navy else MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                bottom.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(translate(lang, d.label), maxLines = 1, softWrap = false, fontSize = 10.sp) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!meshEnabled) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    WarningBanner("Mesh is off: required permissions are missing.", "Fix", onFixPermissions)
                }
            }
            CompositionLocalProvider(LocalLang provides lang) { NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(meshVm) }
                composable("messages") { MessagesScreen(meshVm) { id -> nav.navigate("chat/$id") } }
                composable("chat/{id}") { entry -> ChatScreen(meshVm, entry.arguments?.getString("id") ?: "") }
                composable("network") { NetworkMapScreen(meshVm) }
                composable("inbox") { InboxScreen(meshVm) }
                composable("simulator") { SimulatorScreen(simVm) }
                composable("impact") { ImpactScreen(meshVm, simVm) }
                composable("settings") { SettingsScreen(meshVm) }
                composable("about") { AboutScreen() }
            } }
        }
    }
}
