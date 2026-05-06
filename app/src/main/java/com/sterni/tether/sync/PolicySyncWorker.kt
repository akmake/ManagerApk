package com.sterni.tether.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.gson.JsonParser
import com.sterni.tether.R
import com.sterni.tether.admin.AppScanner
import com.sterni.tether.admin.TetherPolicyManager
import com.sterni.tether.data.model.ReportAppsRequest
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import java.util.concurrent.TimeUnit

class PolicySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!TetherPolicyManager.isEnrolled(context)) return Result.success()

        val deviceId = TetherPolicyManager.getDeviceId(context) ?: return Result.failure()

        return try {
            val api = RetrofitClient.create(TetherApiService::class.java)
            val response = api.getPolicy(deviceId)

            try {
                val isDo = TetherPolicyManager.isDeviceOwner(context)
                val isA11y = com.sterni.tether.admin.TetherAccessibilityService.isRunning
                val installed = AppScanner.getInstalledApps(context)
                
                // זיהוי אפליקציות חדשות
                val prefs = context.getSharedPreferences("tether_sync_state", Context.MODE_PRIVATE)
                val lastReportedPkgs = prefs.getStringSet("last_reported_apps", emptySet()) ?: emptySet()
                val currentPkgs = installed.map { it.packageName }.toSet()
                
                val newApps = installed.filter { it.packageName !in lastReportedPkgs }
                if (newApps.isNotEmpty() && lastReportedPkgs.isNotEmpty()) {
                    // המשתמש התקין אפליקציות חדשות! נדווח למנהל כאירוע אבטחה
                    newApps.forEach { app ->
                        try {
                            api.logSecurityEvent(deviceId, com.sterni.tether.data.api.SecurityEventRequest(
                                type = "NEW_APP_INSTALLED", // נצטרך להוסיף את זה ל-Enum בשרת
                                packageName = "${app.appName} (${app.packageName})"
                            ))
                        } catch (_: Exception) {}
                    }
                }
                
                // עדכון הרשימה השמורה
                prefs.edit().putStringSet("last_reported_apps", currentPkgs).apply()

                // דיווח הרשימה המלאה לשרת
                api.reportApps(deviceId, ReportAppsRequest(deviceId = deviceId, installedApps = installed))
                
                // Heartbeat
                api.sendHeartbeat(deviceId, com.sterni.tether.data.api.HeartbeatRequest(
                    accessibilityEnabled = isA11y,
                    isDeviceAdmin = true,
                    isDeviceOwner = isDo,
                    vpnActive = com.sterni.tether.admin.TetherVpnService.isRunning
                ))
                
                Log.i(TAG, "Status and ${installed.size} apps reported. Found ${newApps.size} new apps.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report status: " + e.message)
            }

            if (response.isSuccessful) {
                val body = response.body()
                val policy = body?.policy
                if (policy != null) {
                    val current = TetherPolicyManager.loadPolicy(context)
                    if (current != policy) {
                        TetherPolicyManager.applyPolicy(context, policy)
                        Log.i(TAG, "Policy updated and applied")
                    }
                }

                // Execute pending commands from admin
                val commands = body?.pendingCommands ?: emptyList()
                for (command in commands) {
                    Log.i(TAG, "Executing command: ${command.type} payload=${command.payload}")
                    when (command.type) {
                        "SHOW_MESSAGE" -> showAdminMessage(context, command.payload)
                        "FORCE_SYNC"   -> if (policy != null) TetherPolicyManager.applyPolicy(context, policy)
                        "RELEASE_ALL"  -> TetherPolicyManager.releaseAllAndUninstall(context, force = true)
                        "APPROVAL_GRANTED" -> applyApprovalGrant(context, command.payload)
                    }
                }

                // allowUninstall=true from server starts a 1-hour local window (works offline too)
                if ((body?.allowUninstall == true) && !TetherPolicyManager.isUninstallWindowActive(context)) {
                    TetherPolicyManager.startUninstallWindow(context)
                    if (policy != null) TetherPolicyManager.applyPolicy(context, policy)
                    Log.i(TAG, "Uninstall window started — 1h from now")
                }
                Result.success()
            } else {
                Log.w(TAG, "Policy fetch failed: " + response.code())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Policy sync error: " + e.message)
            Result.retry()
        }
    }

    private fun showAdminMessage(context: Context, message: String) {
        if (message.isBlank()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tether_admin_msg"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "הודעות מנהל", NotificationManager.IMPORTANCE_HIGH)
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("הודעה מהמנהל")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.app_logo)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_ADMIN_MSG, notif)
    }

    private fun applyApprovalGrant(context: Context, payload: String) {
        runCatching {
            val obj = JsonParser().parse(payload).asJsonObject
            val packageName = obj.get("packageName")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val expiresAt = obj.get("expiresAt")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            if (packageName.isNotBlank() && expiresAt > System.currentTimeMillis()) {
                TetherPolicyManager.grantTemporaryAppAccess(context, packageName, expiresAt)
                showAdminMessage(context, "הגישה אושרה זמנית עבור $packageName")
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply APPROVAL_GRANTED command: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "PolicySync"
        private const val WORK_NAME = "tether_policy_sync"
        private const val NOTIF_ID_ADMIN_MSG = 2001

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<PolicySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
