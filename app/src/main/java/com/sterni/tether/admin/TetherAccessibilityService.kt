package com.sterni.tether.admin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.SecurityEventRequest
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.BlockedActionBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TetherAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val approvalCooldown = mutableMapOf<String, Long>()

    private fun logEvent(type: String, packageName: String? = null) {
        val deviceId = TetherPolicyManager.getDeviceId(this) ?: return
        serviceScope.launch {
            try {
                RetrofitClient.create(TetherApiService::class.java)
                    .logSecurityEvent(deviceId, SecurityEventRequest(type, packageName))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log security event: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "TetherA11y"
        private const val OUR_PACKAGE = "com.sterni.tether"
        private const val OUR_LABEL = "Tether"

        // All app stores — blocked entirely when blockInstallApps = true
        private val APP_STORE_PACKAGES = setOf(
            "com.android.vending",              // Google Play Store
            "com.sec.android.app.samsungapps",  // Samsung Galaxy Store
            "com.samsung.android.app.settings.bixby", // Bixby can sometimes bypass
            "com.amazon.venezia",               // Amazon Appstore
            "com.huawei.appmarket",             // Huawei AppGallery
            "com.xiaomi.market",                // Xiaomi GetApps
            "com.oppo.market",                  // Oppo App Market
            "com.vivo.appstore",                // Vivo App Store
            "com.bbk.appstore",                 // BBK App Store
            "com.meizu.mstore"                  // Meizu App Store
        )

        // Exact popup text shown when WhatsApp Status/Channels are blocked (user-defined wording).
        private const val WHATSAPP_BLOCK_MESSAGE = "חסום בה וחסום בה דכלא בה"

        // Critical system packages that must NEVER be kicked/suspended by the real-time
        // whitelist backstop (mirrors the exclusions in TetherPolicyManager.applyAppSuspension).
        // Without this, opening the keyboard / dialer / system UI while not in allowedApps
        // would bounce the user to Home and break basic device usage.
        private val CRITICAL_SYSTEM_PACKAGES = setOf(
            OUR_PACKAGE,
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.systemui",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3"
        )

        // APK installer packages — ALWAYS blocked regardless of policy
        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.samsung.android.app.installserv", // Samsung background installer
            "com.sec.android.app.installserv",     // Older Samsung installer
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
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.notificationTimeout = 50
        }
        Log.i(TAG, "Accessibility service connected")
    }

    private fun isNewUnapprovedApp(packageName: String): Boolean {
        val policy = TetherPolicyManager.loadPolicy(this) ?: return true
        // If it's not in the allowed list and not a critical system app, it's "unapproved"
        // We consider it "new" if it's currently being interacted with but not allowed
        return !policy.allowedApps.contains(packageName)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallWindowActive(this)) return

        val pkg = event.packageName?.toString() ?: return
        val policy = TetherPolicyManager.loadPolicy(this)
        val blockedBehavior = policy?.blockedActionBehavior ?: BlockedActionBehavior.SILENT
        val hasTempApproval = TetherPolicyManager.isAppTemporarilyAllowed(this, pkg)

        // === Layer 1: Block APK installer and newly installed apps ===
        // Installers are always blocked; the whitelist backstop skips critical system apps
        // so the keyboard/dialer/launcher/settings keep working.
        if ((pkg in PACKAGE_INSTALLER_PACKAGES ||
                    (isNewUnapprovedApp(pkg) && pkg !in CRITICAL_SYSTEM_PACKAGES)) && !hasTempApproval) {
            if (policy == null || policy.blockApkInstall || !policy.allowedApps.contains(pkg)) {
                Log.w(TAG, "Blocking unapproved package: $pkg")
                logEvent("BLOCKED_APP_OPENED", pkg)
                maybeRequestApproval(pkg, blockedBehavior)
                performGlobalAction(GLOBAL_ACTION_HOME)

                val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val admin = android.content.ComponentName(this, TetherDeviceAdminReceiver::class.java)
                if (dpm.isDeviceOwnerApp(packageName)) {
                    try {
                        dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // === Layer 2: Block app stores when any relevant policy flag is set ===
        if (pkg in APP_STORE_PACKAGES && !hasTempApproval) {
            if (policy == null || policy.blockInstallApps || policy.blockAllStores) {
                Log.w(TAG, "Blocking app store: $pkg")
                logEvent("BLOCKED_APP_OPENED", pkg)
                maybeRequestApproval(pkg, blockedBehavior)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // === Layer 3: Block dangerous settings screens ===
        if (pkg == "com.android.settings") {
            val className = event.className?.toString() ?: ""
            if (CRITICAL_SETTINGS_CLASSES.any { className.contains(it) }) {
                Log.w(TAG, "Blocking critical settings: $className")
                logEvent("ADMIN_DEACTIVATE_ATTEMPT", pkg)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            val root = rootInActiveWindow ?: return
            try {
                if (isTetherOnScreen(root) && hasDangerButton(root)) {
                    Log.w(TAG, "Blocking Tether danger action in Settings")
                    logEvent("ADMIN_DEACTIVATE_ATTEMPT", pkg)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } finally {
                root.recycle()
            }
            return
        }

        // === Layer 5: WhatsApp Status & Channels Blocking ===
        // Enabled either by admin policy OR by the local user-controlled shield (Settings toggle).
        if (pkg == "com.whatsapp") {
            val shieldOn = policy?.blockWhatsAppChannels == true ||
                    TetherPolicyManager.isWhatsAppShieldEnabled(this)
            if (!shieldOn) return

            val root = rootInActiveWindow ?: return
            try {
                // Detect "Status"/"Channels" section text only — NOT the always-visible
                // "Updates" bottom-tab label, which would otherwise bounce the user out of
                // WhatsApp entirely. These words appear only on the Updates page / channel view.
                if (isOnStatusOrChannels(root)) {
                    Log.w(TAG, "Blocking WhatsApp Status/Channels access")
                    logEvent("BLOCKED_APP_OPENED", "com.whatsapp.channels")
                    Toast.makeText(this, WHATSAPP_BLOCK_MESSAGE, Toast.LENGTH_LONG).show()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } finally {
                root.recycle()
            }
            return
        }
    }

    private fun isOnStatusOrChannels(root: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("ערוצים", "Channels", "סטטוס", "Status")
        return keywords.any { keyword ->
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            val found = nodes.isNotEmpty()
            nodes.forEach { it.recycle() }
            found
        }
    }

    private fun maybeRequestApproval(packageName: String, behavior: BlockedActionBehavior) {
        if (behavior != BlockedActionBehavior.REQUEST_APPROVAL) return
        val now = System.currentTimeMillis()
        val key = packageName.lowercase()
        val lastSent = approvalCooldown[key] ?: 0L
        if (now - lastSent < 30_000L) return
        approvalCooldown[key] = now

        val deviceId = TetherPolicyManager.getDeviceId(this) ?: return
        serviceScope.launch {
            runCatching {
                RetrofitClient.tetherApi.requestApproval(
                    deviceId = deviceId,
                    action = "APP_ACCESS",
                    packageName = packageName
                )
            }
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

