package com.sterni.tether.admin

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Cancels the system "Can't uninstall active device admin app" notification
 * before the user can tap "Manage device admin apps" and reach the Device Admin page.
 */
class TetherNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "TetherNotifListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldCancel(sbn)) return
        try {
            cancelNotification(sbn.key)
            Log.i(TAG, "Cancelled uninstall-blocked notification from ${sbn.packageName}")
        } catch (_: Exception) {}
    }

    private fun shouldCancel(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text  = extras.getCharSequence("android.text")?.toString()  ?: ""
        val combined = "$title $text"

        val mentionsUs = combined.contains(packageName, ignoreCase = true) ||
                         combined.contains("Tether", ignoreCase = true)

        val isAdminBlock = combined.contains("device admin", ignoreCase = true) ||
                           combined.contains("מנהל המכשיר", ignoreCase = true) ||
                           combined.contains("uninstall", ignoreCase = true) ||
                           combined.contains("להסיר", ignoreCase = true) ||
                           combined.contains("הסרת", ignoreCase = true)

        return mentionsUs && isAdminBlock
    }
}
