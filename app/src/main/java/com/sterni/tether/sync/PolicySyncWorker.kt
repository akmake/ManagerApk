package com.sterni.tether.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.*
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

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: return Result.failure()

        return try {
            val api = RetrofitClient.create(TetherApiService::class.java)
            val response = api.getPolicy(deviceId)

            try {
                // Heartbeat מורחב: מדווח על מצב ההגנות
                val isDo = TetherPolicyManager.isDeviceOwner(context)
                val isA11y = com.sterni.tether.admin.TetherAccessibilityService.isRunning
                val installed = AppScanner.getInstalledApps(context)
                
                // שליחת דיווח מפורט לשרת
                api.reportApps(deviceId, ReportAppsRequest(deviceId = deviceId, apps = installed))
                Log.i(TAG, "Heartbeat sent: DO=$isDo, A11y=$isA11y")
            } catch(e: Exception) {
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
                val allowUninstall = body?.allowUninstall ?: false
                if (allowUninstall && !TetherPolicyManager.isUninstallAllowed(context)) {
                    TetherPolicyManager.saveAllowUninstall(context, allowUninstall)
                    TetherPolicyManager.removeDeviceOwner(context)
                    TetherPolicyManager.clearEnrollment(context)
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    TetherPolicyManager.saveAllowUninstall(context, allowUninstall)
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

    companion object {
        private const val TAG = "PolicySync"
        private const val WORK_NAME = "tether_policy_sync"

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
