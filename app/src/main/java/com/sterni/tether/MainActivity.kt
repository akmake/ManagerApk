package com.sterni.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
                // rememberSaveable, NOT remember: this activity has no android:configChanges, so the
                // system recreates it on every configuration change — rotation, dark/light switch,
                // font-size change, split screen — and on process death. With plain `remember` the
                // flag reset to false each time and the manager was dumped back to the app's start
                // screen mid-task, over and over.
                var adminMode by rememberSaveable { mutableStateOf(false) }

                if (adminMode) {
                    val adminNavController = rememberNavController()
                    // No login gate here. There used to be a second AdminLoginScreen at this level
                    // guarded by a transient `isAuthenticated` boolean, which ignored the stored
                    // session entirely — so every recreation demanded the password again even
                    // though a valid token was sitting in AdminSession. AdminNavGraph owns login
                    // now, and it picks its start destination from that stored session, so a
                    // signed-in manager goes straight in. rememberNavController restores the back
                    // stack too, which puts them back on the screen they were actually using.
                    AdminNavGraph(
                        navController = adminNavController,
                        onExit = { adminMode = false }
                    )
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
