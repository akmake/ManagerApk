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
        
        // הפעלת Lock Task Mode אם אנחנו Device Owner
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                startLockTask()
                android.util.Log.i("MainActivity", "Lock Task Mode started")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to start Lock Task Mode", e)
            }
        }

        // Force apply policy on startup
        com.sterni.tether.admin.TetherPolicyManager.applyStoredPolicy(this)

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
