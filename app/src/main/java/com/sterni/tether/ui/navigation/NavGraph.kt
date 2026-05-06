package com.sterni.tether.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.ui.screens.calendar.CalendarScreen
import com.sterni.tether.ui.screens.home.HomeScreen
import com.sterni.tether.ui.screens.join.AccessibilityPermissionScreen
import com.sterni.tether.ui.screens.join.VpnPermissionScreen
import com.sterni.tether.ui.screens.join.CommunityFoundScreen
import com.sterni.tether.ui.screens.join.DeviceAdminPermissionScreen
import com.sterni.tether.ui.screens.join.EnrollmentSuccessScreen
import com.sterni.tether.ui.screens.join.JoinCommunityScreen
import com.sterni.tether.ui.screens.join.QrScannerScreen
import com.sterni.tether.ui.screens.join.isAccessibilityEnabled
import com.sterni.tether.ui.screens.news.NewsScreen
import com.sterni.tether.ui.screens.onboarding.OnboardingScreen
import com.sterni.tether.ui.screens.settings.SettingsScreen
import com.sterni.tether.ui.screens.store.StoreScreen
import com.sterni.tether.ui.screens.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash            : Screen("splash")
    object Onboarding        : Screen("onboarding")
    object Join              : Screen("join")
    object CommunityFound    : Screen("community_found/{code}/{name}") {
        fun createRoute(code: String, name: String) =
            "community_found/${encode(code)}/${encode(name)}"
    }
    object DeviceAdmin            : Screen("device_admin")
    object AccessibilityPermission: Screen("accessibility_permission?direct={direct}") {
        fun createRoute(direct: Boolean = false) = "accessibility_permission?direct=$direct"
    }
    object VpnPermission          : Screen("vpn_permission")
    object EnrollmentSuccess : Screen("enrollment_success/{name}") {
        fun createRoute(name: String) = "enrollment_success/${encode(name)}"
    }
    object Home     : Screen("home")
    object Store    : Screen("store")
    object Calendar : Screen("calendar")
    object News     : Screen("news")
    object Settings : Screen("settings")
    object Emergency: Screen("emergency")
}

private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun decode(s: String) = java.net.URLDecoder.decode(s, "UTF-8")

@Composable
fun NavGraph(
    navController: NavHostController,
    onEnterAdmin: () -> Unit
) {
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(onFinished = {
                val dest = when {
                    !TetherPolicyManager.isEnrolled(context) -> Screen.Onboarding.route
                    !isAccessibilityEnabled(context) -> Screen.AccessibilityPermission.createRoute(direct = true)
                    else -> Screen.Home.route
                }
                navController.navigate(dest) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinished = {
                navController.navigate(Screen.Join.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Join.route) {
            JoinCommunityScreen(
                onCommunityFound = { code, name ->
                    navController.navigate(Screen.CommunityFound.createRoute(code, name))
                },
                onEnterAdmin = onEnterAdmin
            )
        }

        composable(
            route = Screen.CommunityFound.route,
            arguments = listOf(
                navArgument("code") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { back ->
            val code = decode(back.arguments?.getString("code") ?: "")
            val name = decode(back.arguments?.getString("name") ?: "")
            CommunityFoundScreen(
                code = code,
                communityName = name,
                onConfirmed = { navController.navigate(Screen.DeviceAdmin.route) },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.DeviceAdmin.route) {
            DeviceAdminPermissionScreen(
                onGranted = {
                    navController.navigate(Screen.AccessibilityPermission.route)
                }
            )
        }

        composable(
            route = Screen.AccessibilityPermission.route,
            arguments = listOf(navArgument("direct") { type = NavType.BoolType; defaultValue = false })
        ) { back ->
            val direct = back.arguments?.getBoolean("direct") ?: false
            AccessibilityPermissionScreen(
                onGranted = {
                    if (direct) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.VpnPermission.route)
                    }
                }
            )
        }

        composable(Screen.VpnPermission.route) {
            VpnPermissionScreen(
                onGranted = {
                    val communityName = TetherPolicyManager.getCommunityName(context) ?: ""
                    navController.navigate(Screen.EnrollmentSuccess.createRoute(communityName)) {
                        popUpTo(Screen.Join.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.EnrollmentSuccess.route,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { back ->
            val name = decode(back.arguments?.getString("name") ?: "")
            EnrollmentSuccessScreen(
                communityName = name,
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.EnrollmentSuccess.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val communityName = TetherPolicyManager.getCommunityName(context) ?: "Tether"
            val isDeviceOwner = TetherPolicyManager.isDeviceOwner(context)
            HomeScreen(
                communityName = communityName,
                isDeviceOwner = isDeviceOwner,
                onCalendarClick  = { navController.navigate(Screen.Calendar.route) },
                onNewsClick      = { navController.navigate(Screen.News.route) },
                onSettingsClick  = { navController.navigate(Screen.Settings.route) },
                onStoreClick     = { navController.navigate(Screen.Store.route) },
                onEnterAdmin     = onEnterAdmin
            )
        }

        composable(Screen.Store.route) {
            StoreScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Calendar.route) {
            CalendarScreen()
        }

        composable(Screen.News.route) {
            NewsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Screen.Emergency.route) }
            )
        }

        composable(Screen.Emergency.route) {
            com.sterni.tether.ui.screens.emergency.EmergencyCodeScreen(onBack = { navController.popBackStack() })
        }
    }
}
