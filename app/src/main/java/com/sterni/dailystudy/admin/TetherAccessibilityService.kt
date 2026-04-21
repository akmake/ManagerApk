package com.sterni.dailystudy.admin

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

        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.oppo.packageinstaller",
            "com.coloros.packageinstaller",
            "com.android.packageinstaller"
        )

        // Uninstall / force-stop button texts in Hebrew and English
        private val UNINSTALL_KEYWORDS = listOf(
            "הסר התקנה", "uninstall", "UNINSTALL",
            "עצור בכפייה", "force stop", "FORCE STOP",
            "השבת", "disable", "DISABLE",
            "OK", "אישור", "כן"   // confirmation dialogs
        )

        @Volatile var isRunning = false
    }

    override fun onServiceConnected() {
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            packageNames = null // monitor ALL packages — more reliable
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallAllowed(this)) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in WATCHED_PACKAGES) return

        val root = rootInActiveWindow ?: return
        try {
            if (isAppInfoForOurApp(root) || isUninstallConfirmationForOurApp(root)) {
                Log.w(TAG, "Blocking uninstall attempt from $pkg")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (_: Exception) {
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    // Detects the App Info screen: our app name is the title AND an uninstall/force-stop button is visible
    private fun isAppInfoForOurApp(root: AccessibilityNodeInfo): Boolean {
        val allText = collectAllText(root)

        // Must contain our app name or package name somewhere on screen
        val hasOurApp = allText.any {
            it.equals(OUR_APP_NAME, ignoreCase = true) || it.contains(OUR_PACKAGE, ignoreCase = true)
        }
        if (!hasOurApp) return false

        // AND one of the dangerous buttons must be visible and enabled
        return UNINSTALL_KEYWORDS.any { keyword ->
            root.findAccessibilityNodeInfosByText(keyword).any { node ->
                node.isEnabled && node.isVisibleToUser && node.isClickable
            }
        }
    }

    // Detects the "Are you sure you want to uninstall?" confirmation dialog for our app
    private fun isUninstallConfirmationForOurApp(root: AccessibilityNodeInfo): Boolean {
        val allText = collectAllText(root)
        val combined = allText.joinToString(" ").lowercase()
        // The dialog mentions our package name OR our app name
        return (OUR_PACKAGE in combined || OUR_APP_NAME.lowercase() in combined) &&
                ("uninstall" in combined || "הסר" in combined || "מחק" in combined)
    }

    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        fun traverse(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            for (i in 0 until n.childCount) traverse(n.getChild(i))
        }
        traverse(node)
        return result
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
