package com.sterni.tether.admin

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.sterni.tether.BuildConfig
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import java.io.File

object TetherUpdater {

    private const val TAG = "TetherUpdater"

    suspend fun checkAndUpdate(context: Context) {
        if (!TetherPolicyManager.isDeviceOwner(context)) return
        try {
            val api = RetrofitClient.create(TetherApiService::class.java)

            val versionResponse = api.getAppVersion()
            if (!versionResponse.isSuccessful) return
            val serverVersion = versionResponse.body()?.versionCode ?: return

            if (serverVersion <= BuildConfig.VERSION_CODE) return
            Log.i(TAG, "Update available: ${BuildConfig.VERSION_CODE} → $serverVersion, downloading…")

            val apkFile = File(context.cacheDir, "tether-update.apk")
            val download = api.downloadApk()
            if (!download.isSuccessful) return

            download.body()?.byteStream()?.use { input ->
                apkFile.outputStream().use { out -> input.copyTo(out) }
            } ?: return

            installUpdate(context, apkFile)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        }
    }

    private fun installUpdate(context: Context, apkFile: File) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().copyTo(out)
                    session.fsync(out)
                }
                val intent = Intent(context, TetherInstallReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                session.commit(pi.intentSender)
            }
            Log.i(TAG, "Install session committed")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
        } finally {
            apkFile.delete()
        }
    }
}
