package com.sterni.dailystudy.admin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TetherAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TetherA11y"
        private const val OUR_PACKAGE = "com.sterni.tether" // ודא שזה הפאקג' הנכון שלך
        private const val OUR_APP_NAME = "Tether"

        private val WATCHED_PACKAGES = arrayOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.oppo.packageinstaller",
            "com.coloros.packageinstaller",
            "com.android.packageinstaller"
        )

        private val UNINSTALL_KEYWORDS = listOf(
            "הסר התקנה", "uninstall", "UNINSTALL",
            "עצור בכפייה", "force stop", "FORCE STOP",
            "השבת", "disable", "DISABLE",
            "כבה", "turn off", "TURN OFF",
            "הפסק", "stop", "STOP",
            "OK", "אישור", "כן"
        )

        @Volatile var isRunning = false
    }

    override fun onServiceConnected() {
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
        
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            // תיקון קריטי: האזנה *רק* לאפליקציות ההתקנה וההגדרות
            packageNames = WATCHED_PACKAGES
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallAllowed(this)) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in WATCHED_PACKAGES) return

        val root = rootInActiveWindow ?: return
        try {
            if (isUninstallAttemptFast(root)) {
                Log.w(TAG, "Blocking uninstall attempt from $pkg")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (_: Exception) {
        } finally {
            root.recycle()
        }
    }

    // סריקה מהירה ויעילה ללא רקורסיה קטלנית
    private fun isUninstallAttemptFast(root: AccessibilityNodeInfo): Boolean {
        // שלב 1: האם יש כפתור מסוכן? (חיפוש מהיר של המערכת במקום סריקה ידנית)
        val hasDangerousButton = UNINSTALL_KEYWORDS.any { keyword ->
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            val exists = nodes.any { it.isEnabled && it.isVisibleToUser && it.isClickable }
            nodes.forEach { it.recycle() } // חובה לשחרר זיכרון
            exists
        }

        if (!hasDangerousButton) return false

        // שלב 2: אם יש כפתור מסוכן, האם זה קשור לאפליקציה שלנו?
        val appNameNodes = root.findAccessibilityNodeInfosByText(OUR_APP_NAME)
        val hasAppName = appNameNodes.isNotEmpty()
        appNameNodes.forEach { it.recycle() }
        
        if (hasAppName) return true

        val pkgNodes = root.findAccessibilityNodeInfosByText(OUR_PACKAGE)
        val hasPkgName = pkgNodes.isNotEmpty()
        pkgNodes.forEach { it.recycle() }

        return hasPkgName
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}