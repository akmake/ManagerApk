package com.sterni.dailystudy.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.sterni.dailystudy.admin.TetherPolicyManager
import com.sterni.dailystudy.data.api.RetrofitClient
import com.sterni.dailystudy.data.api.TetherApiService
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
        )

        return try {
            val api = RetrofitClient.create(TetherApiService::class.java)
            val response = api.getPolicy(deviceId)

            if (response.isSuccessful) {
                val policy = response.body()?.policy
                if (policy != null) {
                    val current = TetherPolicyManager.loadPolicy(context)
                    if (current != policy) {
                        TetherPolicyManager.applyPolicy(context, policy)
                        Log.i(TAG, "Policy updated and applied")
                    }
                }
                Result.success()
            } else {
                Log.w(TAG, "Policy fetch failed: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Policy sync error: ${e.message}")
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
