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
import com.sterni.tether.data.model.AppPolicyMode
import com.sterni.tether.data.model.BlockedActionBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TetherAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val approvalCooldown = mutableMapOf<String, Long>()

    // Throttles for the WhatsApp Status/Channels shield so a single visit can't spam
    // GLOBAL_ACTION_HOME on every WINDOW_CONTENT_CHANGED tick (which felt like "all apps closing").
    private var lastWhatsAppScanTs = 0L
    private var lastWhatsAppActionTs = 0L
    private var lastWhatsAppDebugTs = 0L

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

        // Both WhatsApp variants share the same engine, Status/Channels features and internal
        // resource-ids — only the package name differs. The shield must cover both.
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",      // regular WhatsApp
            "com.whatsapp.w4b"   // WhatsApp Business
        )

        // Exact popup text shown when WhatsApp Status/Channels are blocked (user-defined wording).
        private const val WHATSAPP_BLOCK_MESSAGE = "חסום בה וחסום בה דכלא בה"

        // Shield throttles (ms).
        private const val WHATSAPP_SCAN_COOLDOWN_MS = 300L    // max scan rate on noisy content-changes
        private const val WHATSAPP_ACTION_COOLDOWN_MS = 1200L // min gap between two Home bounces
        private const val WHATSAPP_DEBUG_COOLDOWN_MS = 4000L  // id-dump rate (on-device tuning aid)

        // LANGUAGE-INDEPENDENT detection of WhatsApp's Status & Channels screens.
        // We match WhatsApp's internal resource-id / class tokens (always English, for every
        // UI language) instead of localized on-screen text. Key fact: WhatsApp "Channels" are
        // internally called "newsletter". This avoids the previous bug where matching the words
        // "Status"/"Channels" also hit the always-visible bottom-tab label and bounced the user
        // out of WhatsApp entirely.
        private val WHATSAPP_STATUS_CHANNEL_TOKENS = listOf(
            "newsletter",        // Channels (internal name) — directory + channel view
            "channel",           // channel-related views
            "status_playback",   // watching a status (full-screen)
            "my_status",         // Status section on the Updates tab
            "status_list",
            "status_recycler",
            "status_row",
            "status_grid"
        )

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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (TetherPolicyManager.isUninstallWindowActive(this)) return

        val pkg = event.packageName?.toString() ?: return
        val policy = TetherPolicyManager.loadPolicy(this)
        val blockedBehavior = policy?.blockedActionBehavior ?: BlockedActionBehavior.SILENT
        val hasTempApproval = TetherPolicyManager.isAppTemporarilyAllowed(this, pkg)

        // === Layer 1: APK installers — ALWAYS blocked, independent of app mode ===
        if (pkg in PACKAGE_INSTALLER_PACKAGES && !hasTempApproval) {
            Log.w(TAG, "Blocking APK installer: $pkg")
            logEvent("BLOCKED_APP_OPENED", pkg)
            maybeRequestApproval(pkg, blockedBehavior)
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // === Layer 1b: App-access enforcement (mode-aware) ===
        // BLACKLIST (default): only blockedApps are bounced — every other app runs normally.
        // WHITELIST (kiosk):   only allowedApps (and critical system apps) run; the rest are bounced.
        // Critical system apps (keyboard/dialer/launcher/settings/us) are never bounced, so the
        // device stays usable. With no policy yet we behave as BLACKLIST (don't brick the device).
        if (pkg !in CRITICAL_SYSTEM_PACKAGES && !hasTempApproval && policy != null) {
            val blockedByList = policy.blockedApps.contains(pkg)
            val blockedByWhitelist = policy.appPolicyMode == AppPolicyMode.WHITELIST &&
                    !policy.allowedApps.contains(pkg)
            if (blockedByList || blockedByWhitelist) {
                Log.w(TAG, "Blocking app (${policy.appPolicyMode}): $pkg")
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
                return
            }
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

        // === Layer 5: WhatsApp Status & Channels shield ===
        // Enabled either by admin policy OR by the local user-controlled shield (Settings toggle).
        // Detection is language-independent (resource-id / class tokens), so it works for every
        // UI language — see whatsAppStatusOrChannelMarker(). Covers WhatsApp + WhatsApp Business.
        if (pkg in WHATSAPP_PACKAGES) {
            val shieldOn = policy?.blockWhatsAppChannels == true ||
                    TetherPolicyManager.isWhatsAppShieldEnabled(this)
            if (!shieldOn) return

            // Throttle the noisy content-change stream (state-changes always pass through, so
            // tab switches / opening a status are still caught immediately).
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !canScanWhatsApp()) return

            val root = rootInActiveWindow ?: return
            try {
                val marker = whatsAppStatusOrChannelMarker(event, root)
                if (marker != null) {
                    if (canActOnWhatsApp()) {
                        Log.w(TAG, "Blocking WhatsApp Status/Channels access (marker=$marker)")
                        logEvent("BLOCKED_APP_OPENED", "com.whatsapp.channels")
                        Toast.makeText(this, WHATSAPP_BLOCK_MESSAGE, Toast.LENGTH_LONG).show()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                } else {
                    debugDumpWhatsAppIds(root)
                }
            } finally {
                root.recycle()
            }
            return
        }
    }

    /**
     * Returns a short marker string if the active WhatsApp window is the Status viewer or a
     * Channel ("newsletter") screen, else null. LANGUAGE-INDEPENDENT: it inspects WhatsApp's
     * internal resource-ids / class names (always English) rather than localized text, so it
     * behaves the same in Hebrew, English, Arabic, etc.
     */
    private fun whatsAppStatusOrChannelMarker(event: AccessibilityEvent, root: AccessibilityNodeInfo): String? {
        event.className?.toString()?.lowercase()?.let { cls ->
            WHATSAPP_STATUS_CHANNEL_TOKENS.firstOrNull { cls.contains(it) }?.let { return "class:$it" }
        }
        return findStatusChannelMarker(root, 0)
    }

    private fun findStatusChannelMarker(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 25) return null
        if (node.isVisibleToUser) {
            node.viewIdResourceName?.lowercase()?.let { id ->
                WHATSAPP_STATUS_CHANNEL_TOKENS.firstOrNull { id.contains(it) }?.let { return "id:$it" }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findStatusChannelMarker(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun canScanWhatsApp(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppScanTs < WHATSAPP_SCAN_COOLDOWN_MS) return false
        lastWhatsAppScanTs = now
        return true
    }

    private fun canActOnWhatsApp(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppActionTs < WHATSAPP_ACTION_COOLDOWN_MS) return false
        lastWhatsAppActionTs = now
        return true
    }

    /**
     * On-device tuning aid: when the shield is on but nothing matched, log the visible WhatsApp
     * resource-ids (throttled) so the real Status/Channel ids can be confirmed from logcat and
     * locked into WHATSAPP_STATUS_CHANNEL_TOKENS if a particular WhatsApp build differs.
     */
    private fun debugDumpWhatsAppIds(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppDebugTs < WHATSAPP_DEBUG_COOLDOWN_MS) return
        lastWhatsAppDebugTs = now
        val ids = mutableListOf<String>()
        collectVisibleIds(root, 0, ids)
        if (ids.isNotEmpty()) Log.d(TAG, "WA visible ids: ${ids.distinct().take(40).joinToString()}")
    }

    private fun collectVisibleIds(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String>) {
        if (node == null || depth > 25 || out.size > 60) return
        if (node.isVisibleToUser) node.viewIdResourceName?.let { out.add(it.substringAfter("id/")) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleIds(child, depth + 1, out)
            child.recycle()
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

