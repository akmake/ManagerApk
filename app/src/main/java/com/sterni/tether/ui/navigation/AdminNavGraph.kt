package com.sterni.tether.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.sterni.tether.admin.AdminSession
import com.sterni.tether.ui.screens.admin.*

sealed class AdminScreen(val route: String) {
    object Login       : AdminScreen("admin_login")
    object Dashboard   : AdminScreen("admin_dashboard")
    object Communities : AdminScreen("admin_communities")
    object CreateCommunity : AdminScreen("admin_create_community")
    object CommunityDetail : AdminScreen("admin_community/{id}") {
        fun route(id: String) = "admin_community/$id"
    }
    object Approvals   : AdminScreen("admin_approvals")
    object Logs        : AdminScreen("admin_logs/{communityId}") {
        fun route(id: String) = "admin_logs/$id"
    }
    object ShareCode   : AdminScreen("admin_share/{code}/{name}") {
        fun route(code: String, name: String) =
            "admin_share/${encode(code)}/${encode(name)}"
    }
    object ManageAdmins   : AdminScreen("admin_manage_admins")
    object ApprovedAppsCatalog : AdminScreen("admin_approved_apps_catalog")
    object Provisioning   : AdminScreen("admin_provisioning")
    object GlobalOverview : AdminScreen("admin_global_overview")
    object DeviceDetail  : AdminScreen("admin_device/{deviceId}") {
        fun route(deviceId: String) = "admin_device/${encode(deviceId)}"
    }
}

private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun decode(s: String) = java.net.URLDecoder.decode(s, "UTF-8")

private val bottomNavRoutes = setOf(
    AdminScreen.Dashboard.route,
    AdminScreen.Communities.route,
    AdminScreen.Approvals.route
)

@Composable
fun AdminNavGraph(
    navController: NavHostController,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val startDestination = if (AdminSession.isLoggedIn(context))
        AdminScreen.Dashboard.route else AdminScreen.Login.route
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == AdminScreen.Dashboard.route,
                        onClick = {
                            navController.navigate(AdminScreen.Dashboard.route) {
                                popUpTo(AdminScreen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("לוח בקרה") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == AdminScreen.Communities.route,
                        onClick = {
                            navController.navigate(AdminScreen.Communities.route) {
                                popUpTo(AdminScreen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.People, null) },
                        label = { Text("קהילות") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == AdminScreen.Approvals.route,
                        onClick = {
                            navController.navigate(AdminScreen.Approvals.route) {
                                popUpTo(AdminScreen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Pending, null) },
                        label = { Text("בקשות") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {

            composable(AdminScreen.Login.route) {
                AdminLoginScreen(
                    onLoggedIn = {
                        navController.navigate(AdminScreen.Dashboard.route) {
                            popUpTo(AdminScreen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = onExit
                )
            }

            composable(AdminScreen.Dashboard.route) {
                AdminDashboardScreen(
                    onCommunitiesClick = {
                        navController.navigate(AdminScreen.Communities.route) {
                            launchSingleTop = true
                        }
                    },
                    onApprovalsClick = {
                        navController.navigate(AdminScreen.Approvals.route) {
                            launchSingleTop = true
                        }
                    },
                    onProvisioningClick = {
                        navController.navigate(AdminScreen.Provisioning.route)
                    },
                    onGlobalOverviewClick = {
                        navController.navigate(AdminScreen.GlobalOverview.route)
                    },
                    onApprovedAppsClick = {
                        navController.navigate(AdminScreen.ApprovedAppsCatalog.route)
                    },
                    onLogout = onExit
                )
            }

            composable(AdminScreen.Provisioning.route) {
                ProvisioningQrScreen(onBack = { navController.popBackStack() })
            }

            composable(AdminScreen.Communities.route) {
                CommunitiesListScreen(
                    onCommunityClick = { id -> navController.navigate(AdminScreen.CommunityDetail.route(id)) },
                    onCreateClick    = { navController.navigate(AdminScreen.CreateCommunity.route) },
                    onBack           = { navController.popBackStack() }
                )
            }

            composable(AdminScreen.CreateCommunity.route) {
                CreateCommunityScreen(
                    onCreated = { community ->
                        navController.navigate(AdminScreen.ShareCode.route(community.code, community.name)) {
                            popUpTo(AdminScreen.CreateCommunity.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = AdminScreen.CommunityDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { back ->
                val id = back.arguments?.getString("id") ?: ""
                CommunityDetailScreen(
                    communityId = id,
                    onShareClick = { code, name ->
                        navController.navigate(AdminScreen.ShareCode.route(code, name))
                    },
                    onDeviceClick = { deviceId ->
                        navController.navigate(AdminScreen.DeviceDetail.route(deviceId))
                    },
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(
                route = AdminScreen.DeviceDetail.route,
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
            ) { back ->
                val deviceId = decode(back.arguments?.getString("deviceId") ?: "")
                DeviceDetailScreen(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AdminScreen.Approvals.route) {
                ApprovalRequestsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = AdminScreen.Logs.route,
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { back ->
                val communityId = back.arguments?.getString("communityId") ?: ""
                LogsScreen(communityId = communityId, onBack = { navController.popBackStack() })
            }

            composable(
                route = AdminScreen.ShareCode.route,
                arguments = listOf(
                    navArgument("code") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType }
                )
            ) { back ->
                val code = decode(back.arguments?.getString("code") ?: "")
                val name = decode(back.arguments?.getString("name") ?: "")
                ShareCommunityCodeScreen(
                    code = code,
                    communityName = name,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AdminScreen.ManageAdmins.route) {
                ManageAdminsScreen(onBack = { navController.popBackStack() })
            }

            composable(AdminScreen.GlobalOverview.route) {
                GlobalOverviewScreen(onBack = { navController.popBackStack() })
            }

            composable(AdminScreen.ApprovedAppsCatalog.route) {
                ApprovedAppsCatalogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
