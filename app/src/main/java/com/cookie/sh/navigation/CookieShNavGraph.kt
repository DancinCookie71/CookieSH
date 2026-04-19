package com.cookie.sh.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cookie.sh.feature.boot.BootInfoRoute
import com.cookie.sh.feature.dashboard.DashboardRoute
import com.cookie.sh.feature.logcat.LogcatRoute
import com.cookie.sh.feature.network.NetworkToolsRoute
import com.cookie.sh.feature.packages.PackageManagerRoute
import com.cookie.sh.feature.partitions.PartitionViewerRoute
import com.cookie.sh.feature.props.PropsEditorRoute
import com.cookie.sh.feature.settings.SettingsRoute
import com.cookie.sh.feature.shellrunner.ShellRunnerRoute
import com.cookie.sh.feature.system.SystemOverviewRoute
import com.cookie.sh.feature.splash.SplashScreen
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookiePrimary
import com.cookie.sh.ui.theme.CookieRed
import com.cookie.sh.ui.theme.CookieSecondary
import com.cookie.sh.ui.theme.CookieYellow

sealed class Destination(
    val route: String,
    val title: String,
    val description: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Dashboard : Destination(
        route = "dashboard",
        title = "Dashboard",
        description = "Overview cards for your device toolkit",
        accent = CookiePrimary,
        icon = Icons.Rounded.Dashboard,
    )

    data object Props : Destination(
        route = "props",
        title = "Props Editor",
        description = "Inspect, tweak, favorite, and export build props",
        accent = CookieSecondary,
        icon = Icons.Rounded.Widgets,
    )

    data object Shell : Destination(
        route = "shell",
        title = "Shell Runner",
        description = "Run commands with live output and local history",
        accent = CookieGreen,
        icon = Icons.Rounded.Code,
    )

    data object Logcat : Destination(
        route = "logcat",
        title = "Logcat Viewer",
        description = "Stream, search, filter, and export device logs",
        accent = CookieYellow,
        icon = Icons.Rounded.Description,
    )

    data object Packages : Destination(
        route = "packages",
        title = "Package Manager",
        description = "Review installed apps and run root package actions",
        accent = CookiePrimary,
        icon = Icons.Rounded.Apps,
    )

    data object Partitions : Destination(
        route = "partitions",
        title = "Partition Viewer",
        description = "Inspect block devices and dump partitions to files",
        accent = CookieRed,
        icon = Icons.Rounded.Storage,
    )

    data object Network : Destination(
        route = "network",
        title = "ADB & Network Tools",
        description = "Toggle ADB over Wi-Fi and keep track of your IP",
        accent = CookieSecondary,
        icon = Icons.Rounded.Usb,
    )

    data object Boot : Destination(
        route = "boot",
        title = "Boot Info",
        description = "Parse slot, AVB, cmdline, and verified boot details",
        accent = CookieGreen,
        icon = Icons.Rounded.Memory,
    )

    data object Settings : Destination(
        route = "settings",
        title = "Settings",
        description = "App preferences and customization",
        accent = CookiePrimary,
        icon = Icons.Rounded.Settings,
    )

    data object SystemOverview : Destination(
        route = "system",
        title = "System Overview",
        description = "Real-time CPU, RAM, Battery, and Disk stats",
        accent = CookieYellow,
        icon = Icons.Rounded.Speed,
    )

    data object SplashScreen : Destination(
        route = "splash",
        title = "Splash",
        description = "Initial loading screen",
        accent = Color.Transparent,
        icon = Icons.Rounded.Dashboard,
    )
}

val toolDestinations = listOf(
    Destination.Props,
    Destination.Shell,
    Destination.Logcat,
    Destination.Packages,
    Destination.Partitions,
    Destination.Network,
    Destination.Boot,
    Destination.SystemOverview,
)

@Composable
fun CookieShNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Destination.SplashScreen.route,
    ) {
        composable(Destination.SplashScreen.route) {
            SplashScreen(onSplashFinished = {
                navController.navigate(Destination.Dashboard.route) {
                    popUpTo(Destination.SplashScreen.route) { inclusive = true }
                }
            })
        }
        composable(Destination.Dashboard.route) {
            DashboardRoute(onNavigate = { destination -> navController.navigate(destination.route) })
        }
        composable(Destination.Props.route) {
            PropsEditorRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Shell.route) {
            ShellRunnerRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Logcat.route) {
            LogcatRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Packages.route) {
            PackageManagerRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Partitions.route) {
            PartitionViewerRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Network.route) {
            NetworkToolsRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Boot.route) {
            BootInfoRoute(onBack = navController::popBackStack)
        }
        composable(Destination.Settings.route) {
            SettingsRoute(onBack = navController::popBackStack)
        }
        composable(Destination.SystemOverview.route) {
            SystemOverviewRoute(onBack = navController::popBackStack)
        }
    }
}
