package com.sterni.tether.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class TetherInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                Log.i(TAG, "Update installed successfully")

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Non-DO device: system requests user confirmation — launch the install dialog
                @Suppress("DEPRECATION")
                val userIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (userIntent != null) {
                    userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userIntent)
                }
            }

            else -> Log.e(TAG, "Install failed (status=$status): $message")
        }
    }

    companion object {
        private const val TAG = "TetherInstall"
    }
}
