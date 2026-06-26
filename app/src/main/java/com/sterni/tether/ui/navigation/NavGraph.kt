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
import com.sterni.tether.ui.screens.study.StudyDetailScreen
import com.sterni.tether.ui.screens.zmanim.ZmanimScreen
import com.sterni.tether.ui.screens.tools.ToolsScreen
import com.sterni.tether.ui.screens.tools.JerusalemDirectionScreen
import com.sterni.tether.ui.screens.location.LocationZoneScreen
import com.sterni.tether.ui.screens.appblocker.AppBlockerScreen
import com.sterni.tether.ui.screens.tefila.TefilaScreen
import com.sterni.tether.ui.screens.mamaarim.MamaarimScreen
import com.sterni.tether.ui.screens.mamaarim.MamaarReaderScreen
import com.sterni.tether.ui.screens.mamaarim.ArticleUploadScreen
import com.sterni.tether.ui.screens.pdflibrary.PdfStudyScreen
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
    object StudyDetail : Screen("study/{studyKey}/{date}/{title}/{label}") {
        fun createRoute(studyKey: String, date: String, title: String, label: String) =
            "study/$studyKey/${encode(date)}/${encode(title)}/${encode(label)}"
    }
    object Store    : Screen("store")
    object Calendar : Screen("calendar")
    object News     : Screen("news")
    object Settings : Screen("settings")
    object Zmanim   : Screen("zmanim")
    object Tools    : Screen("tools")
    object JerusalemDir : Screen("jerusalemDirection")
    object LocationZones : Screen("locationZones")
    object AppBlocker : Screen("appBlocker")
    object Tefila   : Screen("tefila")
    object Mamaarim : Screen("mamaarim")
    object MamaarReader : Screen("mamaarReader/{id}") {
        fun createRoute(id: String) = "mamaarReader/$id"
    }
    object ArticleUpload : Screen("articleUpload")
    object PdfLibrary : Screen("pdfLibrary")
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
            HomeScreen(
                onStudyClick      = { studyKey, date, title, label ->
                    navController.navigate(Screen.StudyDetail.createRoute(studyKey, date, title, label))
                },
                onZmanimClick     = { navController.navigate(Screen.Zmanim.route) },
                onNewsClick       = { navController.navigate(Screen.News.route) },
                onCalendarClick   = { navController.navigate(Screen.Calendar.route) },
                onSettingsClick   = { navController.navigate(Screen.Settings.route) },
                onMamaarimClick   = { navController.navigate(Screen.Mamaarim.route) },
                onTefilaClick     = { navController.navigate(Screen.Tefila.route) },
                onPdfLibraryClick = { navController.navigate(Screen.PdfLibrary.route) },
                onToolsClick      = { navController.navigate(Screen.Tools.route) },
                onEnterAdmin      = onEnterAdmin
            )
        }

        composable(
            route = Screen.StudyDetail.route,
            arguments = listOf(
                navArgument("studyKey") { type = NavType.StringType },
                navArgument("date")     { type = NavType.StringType },
                navArgument("title")    { type = NavType.StringType },
                navArgument("label")    { type = NavType.StringType }
            )
        ) { back ->
            val studyKey = back.arguments?.getString("studyKey") ?: return@composable
            val date     = decode(back.arguments?.getString("date") ?: "")
            val title    = decode(back.arguments?.getString("title") ?: "")
            val label    = decode(back.arguments?.getString("label") ?: "")
            StudyDetailScreen(
                studyKey = studyKey,
                date     = date,
                title    = title,
                label    = label,
                onBack   = { navController.popBackStack() }
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

        composable(Screen.Zmanim.route) {
            ZmanimScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Tools.route) {
            ToolsScreen(
                onBack              = { navController.popBackStack() },
                onTefilaClick       = { navController.navigate(Screen.Tefila.route) },
                onJerusalemDirClick = { navController.navigate(Screen.JerusalemDir.route) },
                onSilentZoneClick   = { navController.navigate(Screen.LocationZones.route) },
                onAppBlockerClick   = { navController.navigate(Screen.AppBlocker.route) },
                onMamaarimClick     = { navController.navigate(Screen.Mamaarim.route) }
            )
        }

        composable(Screen.JerusalemDir.route) {
            JerusalemDirectionScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.LocationZones.route) {
            LocationZoneScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AppBlocker.route) {
            AppBlockerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Tefila.route) {
            TefilaScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Mamaarim.route) {
            MamaarimScreen(
                onBack   = { navController.popBackStack() },
                onOpen   = { id -> navController.navigate(Screen.MamaarReader.createRoute(id)) },
                onUpload = { navController.navigate(Screen.ArticleUpload.route) }
            )
        }

        composable(
            route = Screen.MamaarReader.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("id") ?: return@composable
            MamaarReaderScreen(
                mamaarId = id,
                onBack   = { navController.popBackStack() }
            )
        }

        composable(Screen.ArticleUpload.route) {
            ArticleUploadScreen(
                onBack    = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.PdfLibrary.route) {
            PdfStudyScreen(onBack = { navController.popBackStack() })
        }
    }
}
