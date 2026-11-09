package dev.proxboss.proxguard.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.screens.AlertsScreen
import dev.proxboss.proxguard.ui.screens.DashboardScreen
import dev.proxboss.proxguard.ui.screens.GuestDetailScreen
import dev.proxboss.proxguard.ui.screens.GuestsScreen
import dev.proxboss.proxguard.ui.screens.IncidentsScreen
import dev.proxboss.proxguard.ui.screens.LoginScreen
import dev.proxboss.proxguard.ui.screens.MetricsHistoryScreen
import dev.proxboss.proxguard.ui.screens.SettingsScreen

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("dashboard", "Tableau", Icons.Rounded.Dashboard),
    NavItem("guests", "Invités", Icons.Rounded.Dns),
    NavItem("alerts", "Alertes", Icons.Rounded.NotificationsActive),
    NavItem("settings", "Réglages", Icons.Rounded.Settings),
)

private val topLevelRoutes = navItems.map { it.route }.toSet()

@Composable
fun ProxGuardRoot(
    viewModel: MainViewModel,
    config: AppConfig,
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(config.isConfigured, config.alertsEnabled) {
        if (!permissionAsked && config.isConfigured && config.alertsEnabled && Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionAsked = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pendingRoute, route) {
        if (!pendingRoute.isNullOrBlank() && route != null && route != pendingRoute) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onPendingRouteHandled()
        } else if (!pendingRoute.isNullOrBlank() && route == pendingRoute) {
            onPendingRouteHandled()
        }
    }

    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (route in topLevelRoutes) {
                    PgBottomBar(
                        navController = navController,
                        currentRoute = route,
                        alertCount = incidents.size,
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(padding),
                enterTransition = {
                    fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }
                },
                exitTransition = {
                    fadeOut(tween(160))
                },
                popEnterTransition = {
                    fadeIn(tween(220))
                },
                popExitTransition = {
                    fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 12 }
                },
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        viewModel = viewModel,
                        config = config,
                        onOpenGuests = { navController.navigate("guests") },
                        onOpenAlerts = { navController.navigate("alerts") },
                        onOpenMetrics = { navController.navigate("metrics") },
                    )
                }
                composable("guests") {
                    GuestsScreen(
                        viewModel = viewModel,
                        config = config,
                        onOpenGuest = { guest ->
                            navController.navigate("guest/${guest.node}/${guest.type.apiValue}/${guest.vmid}")
                        },
                    )
                }
                composable("alerts") {
                    AlertsScreen(
                        viewModel = viewModel,
                        config = config,
                        onOpenIncidents = { navController.navigate("incidents") },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        config = config,
                        onEditConnection = { navController.navigate("login") },
                        onOpenAlerts = { navController.navigate("alerts") },
                    )
                }
                composable("login") {
                    LoginScreen(
                        viewModel = viewModel,
                        isEditing = true,
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "guest/{node}/{type}/{vmid}",
                    arguments = listOf(
                        navArgument("node") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType },
                        navArgument("vmid") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val node = entry.arguments?.getString("node").orEmpty()
                    val type = GuestType.from(entry.arguments?.getString("type")) ?: GuestType.LXC
                    val vmid = entry.arguments?.getInt("vmid") ?: 0
                    GuestDetailScreen(
                        viewModel = viewModel,
                        node = node,
                        type = type,
                        vmid = vmid,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("incidents") {
                    IncidentsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("metrics") {
                    MetricsHistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun PgBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    alertCount: Int,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        if (item.route == "alerts" && alertCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(if (alertCount > 99) "99+" else alertCount.toString())
                                    }
                                }
                            ) {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        } else {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
