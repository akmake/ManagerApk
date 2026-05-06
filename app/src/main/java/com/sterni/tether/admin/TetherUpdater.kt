package com.sterni.tether.admin

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import com.sterni.tether.BuildConfig
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.ApprovedApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TetherUpdater {

    private const val TAG = "TetherUpdater"

    suspend fun checkAndUpdateAll(context: Context) {
        if (!TetherPolicyManager.isDeviceOwner(context)) return
        
        withContext(Dispatchers.IO) {
            // 1. Update Tether itself
            checkAndUpdateSelf(context)
            
            // 2. Update approved third-party apps
            checkAndUpdateApprovedApps(context)
        }
    }

    private suspend fun checkAndUpdateSelf(context: Context) {
        try {
            val api = RetrofitClient.create(TetherApiService::class.java)
            val versionResponse = api.getAppVersion()
            if (!versionResponse.isSuccessful) return
            val serverVersion = versionResponse.body()?.versionCode ?: return

            if (serverVersion <= BuildConfig.VERSION_CODE) return
            Log.i(TAG, "Self-update available: ${BuildConfig.VERSION_CODE} → $serverVersion")

            val apkFile = File(context.cacheDir, "tether-update.apk")
            val download = api.downloadApk()
            if (download.isSuccessful) {
                download.body()?.byteStream()?.use { input ->
                    apkFile.outputStream().use { out -> input.copyTo(out) }
                }
                installApp(context, apkFile, context.packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Self-update failed: ${e.message}")
        }
    }

    private suspend fun checkAndUpdateApprovedApps(context: Context) {
        try {
            val api = RetrofitClient.create(TetherApiService::class.java)
            val response = api.getApprovedApps()
            if (!response.isSuccessful) return
            val approvedApps = response.body() ?: return

            val pm = context.packageManager
            for (app in approvedApps) {
                val currentVersion = try {
                    pm.getPackageInfo(app.packageName, 0).versionCode
                } catch (e: PackageManager.NameNotFoundException) {
                    -1 // Not installed
                }

                if (app.versionCode > currentVersion) {
                    Log.i(TAG, "Updating ${app.packageName}: $currentVersion → ${app.versionCode}")
                    downloadAndInstall(context, api, app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Approved apps update failed: ${e.message}")
        }
    }

    private suspend fun downloadAndInstall(context: Context, api: TetherApiService, app: ApprovedApp) {
        try {
            val apkFile = File(context.cacheDir, "${app.packageName}.apk")
            val download = api.downloadExternalApk(app.downloadUrl)
            
            if (download.isSuccessful) {
                download.body()?.byteStream()?.use { input ->
                    apkFile.outputStream().use { out -> input.copyTo(out) }
                }
                installApp(context, apkFile, app.packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/install ${app.packageName}: ${e.message}")
        }
    }

    private fun installApp(context: Context, apkFile: File, packageName: String) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            
            // This is key for silent install on Android 10+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("app_install.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().copyTo(out)
                    session.fsync(out)
                }
                
                // We use a broadcast receiver to handle the result
                val intent = Intent(context, TetherInstallReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                session.commit(pi.intentSender)
            }
            Log.i(TAG, "Install session committed for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for $packageName: ${e.message}")
        } finally {
            if (apkFile.exists()) apkFile.delete()
        }
    }
}
