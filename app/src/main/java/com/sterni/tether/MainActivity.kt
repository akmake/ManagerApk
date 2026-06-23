package com.sterni.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.sterni.tether.notification.CalendarReminderWorker
import com.sterni.tether.sync.PolicySyncWorker
import com.sterni.tether.ui.navigation.AdminNavGraph
import com.sterni.tether.ui.navigation.NavGraph
import com.sterni.tether.ui.theme.TetherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager

        // CRITICAL: never pin the device to Tether. A previous version called startLockTask() while
        // only com.sterni.tether was lock-task-allowed, which put every Device-Owner device into
        // single-app kiosk mode — the user was locked inside Tether and EVERY other app was blocked
        // ("kicks me out of all apps"). App restrictions are enforced by the policy
        // (BLACKLIST/WHITELIST) + accessibility + DPM suspension, NEVER by pinning.
        // Actively leave lock-task mode in case this device was pinned by an older build.
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                stopLockTask()
                android.util.Log.i("MainActivity", "Lock Task Mode cleared")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "stopLockTask failed", e)
            }
        }

        // Force apply policy on startup
        com.sterni.tether.admin.TetherPolicyManager.applyStoredPolicy(this)

        // הפעלת VPN להגנת רשת אם מחובר לקהילה
        if (com.sterni.tether.admin.TetherPolicyManager.isEnrolled(this)) {
            startForegroundService(android.content.Intent(this, com.sterni.tether.admin.TetherVpnService::class.java))
            com.sterni.tether.admin.TetherWatchdogService.start(this)
        }

        PolicySyncWorker.enqueue(this)
        CalendarReminderWorker.enqueuePeriodic(this)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TetherTheme {
                var adminMode by remember { mutableStateOf(false) }
                var isAuthenticated by remember { mutableStateOf(false) }

                if (adminMode) {
                    val adminNavController = rememberNavController()
                    if (!isAuthenticated) {
                        com.sterni.tether.ui.screens.admin.AdminLoginScreen(
                            onLoggedIn = { isAuthenticated = true },
                            onBack = { adminMode = false }
                        )
                    } else {
                        AdminNavGraph(
                            navController = adminNavController,
                            onExit = { 
                                adminMode = false 
                                isAuthenticated = false
                            }
                        )
                    }
                } else {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        onEnterAdmin = { adminMode = true }
                    )
                }
            }
        }
    }
}
