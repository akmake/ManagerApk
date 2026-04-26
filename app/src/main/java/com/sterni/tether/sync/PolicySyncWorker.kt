package com.sterni.tether.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
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
                api.reportApps(deviceId, ReportAppsRequest(deviceId = deviceId, apps = installed))
                Log.i(TAG, "Heartbeat sent: DO=$isDo, A11y=$isA11y")
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
                        "RELEASE_ALL"  -> TetherPolicyManager.releaseAllAndUninstall(context)
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
