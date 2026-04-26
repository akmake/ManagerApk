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
        private const val OUR_LABEL = "Tether"

        // All app stores — blocked entirely when blockInstallApps = true
        private val APP_STORE_PACKAGES = setOf(
            "com.android.vending",              // Google Play Store
            "com.sec.android.app.samsungapps",  // Samsung Galaxy Store
            "com.amazon.venezia",               // Amazon Appstore
            "com.huawei.appmarket",             // Huawei AppGallery
            "com.xiaomi.market",                // Xiaomi GetApps
            "com.oppo.market",                  // Oppo App Market
            "com.vivo.appstore",                // Vivo App Store
            "com.bbk.appstore",                 // BBK App Store
            "com.meizu.mstore"                  // Meizu App Store
        )

        // APK installer packages — ALWAYS blocked regardless of policy
        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.oppo.packageinstaller",
            "com.coloros.packageinstaller",
            "com.vivo.packageinstaller"
        )

        // Settings screens that allow bypassing protection
        private val CRITICAL_SETTINGS_CLASSES = setOf(
            "AccessibilitySettings",
            "ToggleAccessibilityServicePreferenceFragment",
            "AccessibilityDetailsSettingsActivity",
            "DeviceAdminSettings",
            "ManageWriteSettings",
            "InstalledAppDetails",
            "AppInfoDashboardFragment",
            "AppDetailFragment",
            "DevelopmentSettings",
            "DevelopmentSettingsDashboardFragment",
            "VpnSettings",
            "UnknownSourcesSettings",
            "InstallUnknownAppsFragment"
        )

        // Actions that would harm Tether
        private val TETHER_DANGER_KEYWORDS = listOf(
            "הסר התקנה", "uninstall", "UNINSTALL",
            "עצור בכפייה", "force stop", "FORCE STOP",
            "השבת", "disable", "DISABLE",
            "מחק נתונים", "clear data", "CLEAR DATA",
            "ניהול שטח", "manage space", "MANAGE SPACE"
        )

        @Volatile var isRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceInfo = serviceInfo?.also { info ->
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            // Watch window changes and content changes — enough to detect store openings
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.notificationTimeout = 50
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallAllowed(this)) return

        val pkg = event.packageName?.toString() ?: return

        // === Layer 1: Block APK installer unless policy explicitly allows it ===
        if (pkg in PACKAGE_INSTALLER_PACKAGES) {
            val policy = TetherPolicyManager.loadPolicy(this)
            if (policy == null || policy.blockApkInstall) {
                Log.w(TAG, "Blocking package installer: $pkg")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // === Layer 2: Block app stores when any relevant policy flag is set ===
        if (pkg in APP_STORE_PACKAGES) {
            val policy = TetherPolicyManager.loadPolicy(this)
            if (policy == null || policy.blockInstallApps || policy.blockAllStores) {
                Log.w(TAG, "Blocking app store: $pkg")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // === Layer 3: Block dangerous settings screens ===
        if (pkg == "com.android.settings") {
            val className = event.className?.toString() ?: ""
            if (CRITICAL_SETTINGS_CLASSES.any { className.contains(it) }) {
                Log.w(TAG, "Blocking critical settings: $className")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            // Block app-info page when showing Tether
            val root = rootInActiveWindow ?: return
            try {
                if (isTetherOnScreen(root) && hasDangerButton(root)) {
                    Log.w(TAG, "Blocking Tether danger action in Settings")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } finally {
                root.recycle()
            }
            return
        }

        // === Layer 4: Protect Tether from uninstall/disable in any other app ===
        val root = rootInActiveWindow ?: return
        try {
            if (isTetherOnScreen(root) && hasDangerButton(root)) {
                Log.w(TAG, "Blocking Tether danger action in $pkg")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } finally {
            root.recycle()
        }
    }

    private fun isTetherOnScreen(root: AccessibilityNodeInfo): Boolean {
        val labelNodes = root.findAccessibilityNodeInfosByText(OUR_LABEL)
        val found = labelNodes.isNotEmpty()
        labelNodes.forEach { it.recycle() }
        if (found) return true
        val pkgNodes = root.findAccessibilityNodeInfosByText(OUR_PACKAGE)
        val foundPkg = pkgNodes.isNotEmpty()
        pkgNodes.forEach { it.recycle() }
        return foundPkg
    }

    private fun hasDangerButton(root: AccessibilityNodeInfo): Boolean =
        TETHER_DANGER_KEYWORDS.any { keyword ->
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            val found = nodes.any { it.isEnabled && it.isVisibleToUser && it.isClickable }
            nodes.forEach { it.recycle() }
            found
        }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
