package com.sterni.tether.admin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TetherAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TetherA11y"
        private const val OUR_PACKAGE = "com.sterni.tether"
        private const val OUR_APP_NAME = "Tether"

        private val WATCHED_PACKAGES = arrayOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.oppo.packageinstaller",
            "com.coloros.packageinstaller",
            "com.android.packageinstaller",
            "com.android.systemui",
            "com.android.vending" // חסימת ה-Play Store אם הוא מצליח להיפתח
        )

        private val UNINSTALL_KEYWORDS = listOf(
            "הסר התקנה", "uninstall", "UNINSTALL",
            "עצור בכפייה", "force stop", "FORCE STOP",
            "השבת", "disable", "DISABLE",
            "כבה", "turn off", "TURN OFF",
            "הפסק", "stop", "STOP",
            "מחק נתונים", "clear data", "CLEAR DATA",
            "ניהול שטח", "manage space", "MANAGE SPACE",
            "מחק", "delete", "DELETE",
            "התקן", "install", "INSTALL", // חסימת כפתור התקנה!
            "עדכן", "update", "UPDATE",
            "אישור", "OK", "כן"
        )

        @Volatile var isRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallAllowed(this)) return

        val pkg = event.packageName?.toString() ?: return
        
        // הגנה על הגדרות הנגישות, הניהול והאפליקציות
        if (pkg == "com.android.settings") {
            val className = event.className?.toString() ?: ""
            // חסימת גישה למסכים קריטיים שמאפשרים עקיפה
            if (className.contains("AccessibilitySettings") || 
                className.contains("DeviceAdminSettings") ||
                className.contains("ManageWriteSettings") ||
                className.contains("AppDetailsSettings") ||
                className.contains("DevelopmentSettings") || // חסימת אפשרויות מפתח
                className.contains("VpnSettings")) { // חסימת הגדרות VPN
                
                val root = rootInActiveWindow
                if (root != null) {
                    if (isCriticalActionAttempted(root)) {
                        Log.w(TAG, "Blocking access to critical settings")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        root.recycle()
                        return
                    }
                    root.recycle()
                }
            }
        }

        if (pkg !in WATCHED_PACKAGES) return

        val root = rootInActiveWindow ?: return
        try {
            if (isCriticalActionAttempted(root)) {
                Log.w(TAG, "Blocking uninstall/stop attempt from $pkg")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } catch (_: Exception) {
        } finally {
            root.recycle()
        }
    }

    // סריקה מהירה ויעילה לזיהוי פעולות מסוכנות
    private fun isCriticalActionAttempted(root: AccessibilityNodeInfo): Boolean {
        // האם המילה "Tether" מופיעה על המסך?
        val appNameNodes = root.findAccessibilityNodeInfosByText(OUR_APP_NAME)
        val hasAppName = appNameNodes.isNotEmpty()
        appNameNodes.forEach { it.recycle() }

        if (!hasAppName) {
            val pkgNodes = root.findAccessibilityNodeInfosByText(OUR_PACKAGE)
            val hasPkgName = pkgNodes.isNotEmpty()
            pkgNodes.forEach { it.recycle() }
            if (!hasPkgName) return false
        }

        // אם אנחנו על המסך של האפליקציה שלנו, בדוק אם לוחצים על כפתור מסוכן
        return UNINSTALL_KEYWORDS.any { keyword ->
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            val exists = nodes.any { it.isEnabled && it.isVisibleToUser && it.isClickable }
            nodes.forEach { it.recycle() }
            exists
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
