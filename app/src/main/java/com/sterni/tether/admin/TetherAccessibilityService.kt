package com.sterni.tether.admin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.sterni.tether.BuildConfig
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.local.AppBlockerPrefs
import com.sterni.tether.data.local.ShortsLimitPrefs
import com.sterni.tether.data.model.AppPolicyMode
import com.sterni.tether.data.model.BlockedActionBehavior
import com.sterni.tether.data.model.blockedBrowsers
import com.sterni.tether.data.model.blockedStores
import com.sterni.tether.data.model.blockedYoutube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.time.LocalDate

class TetherAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val approvalCooldown = mutableMapOf<String, Long>()

    // Throttles for the WhatsApp Status/Channels shield so a single visit can't spam
    // GLOBAL_ACTION_HOME on every WINDOW_CONTENT_CHANGED tick (which felt like "all apps closing").
    private var lastWhatsAppScanTs = 0L
    private var lastWhatsAppActionTs = 0L
    private var lastWhatsAppDebugTs = 0L
    private val whatsAppAccessGuard = WhatsAppAccessGuard()
    private var whatsAppExitJob: Job? = null

    // YouTube Shorts daily-limit tracker.
    // shortsWatchingSince = wall-clock ms when the current Shorts session started (0 = not watching).
    private var shortsWatchingSince = 0L
    private var lastShortsScanTs = 0L
    private var lastShortsActionTs = 0L
    private var lastShortsLimitLogTs = 0L        // throttles SHORTS_LIMIT_REACHED server events
    private var shortsDetectedLogDate: String? = null // last day a SHORTS_DETECTED event was sent
    private var lastShortsDebugTs = 0L           // throttles the debug id-dump aid

    // חבילת חסימה throttles — a blocked browser fires WINDOW_CONTENT_CHANGED continuously while
    // it is dying, and without these every tick would queue another Home bounce.
    private var lastPackActionTs = 0L
    private var lastUrlScanTs = 0L
    private var lastForegroundPkg: String? = null   // dedupes the foreground-app trace

    // All accessibility blocks funnel through the shared SecurityLogger (device-id + throttle + POST).
    private fun logEvent(type: String, packageName: String? = null) {
        SecurityLogger.log(this, type, packageName)
    }

    companion object {
        private const val TAG = "TetherA11y"
        private const val OUR_PACKAGE = "com.sterni.tether"
        private const val OUR_LABEL = "Tether"

        // Store and installer package lists now live in BlockPack — one source of truth shared
        // with TetherPolicyManager, so the two layers can never drift apart.

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

        // === YouTube Shorts daily-limit shield ===
        // Only the Shorts player is limited; regular videos are never touched.
        private val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube"   // common YouTube mod — same internal "reel" ids
        )

        // LANGUAGE-INDEPENDENT detection of the Shorts player. YouTube internally calls Shorts
        // "reel", so we match its resource-ids / class tokens (always English, for every UI
        // language) rather than localized on-screen text.
        private val YOUTUBE_SHORTS_TOKENS = listOf(
            "reel_recycler",              // the vertical Shorts pager
            "reel_player_page_container",
            "reel_player_underlay",
            "reel_watch_player",
            "reel_watch_fragment",
            "shorts_"                     // some builds prefix Shorts ids with shorts_
        )

        private const val SHORTS_SCAN_COOLDOWN_MS = 700L   // max tree-scan rate while in YouTube
        private const val SHORTS_ACTION_COOLDOWN_MS = 1500L // min gap between two Back bounces
        private const val SHORTS_MAX_TICK_SEC = 20L        // cap per accrual tick (guards idle gaps)
        private const val SHORTS_WARNING_SEC = 300L        // warn when this many seconds remain (5 min)
        private const val SHORTS_LIMIT_LOG_COOLDOWN_MS = 600_000L // 10 min between limit-reached events
        private const val SHORTS_DEBUG_COOLDOWN_MS = 4000L // id-dump rate (debug tuning aid)
        private const val SHORTS_LIMIT_MESSAGE = "נגמר הזמן לשורטס להיום. אפשר להוסיף דקות באפליקציה."
        private const val SHORTS_WARNING_MESSAGE = "נשארו לך 5 דקות שורטס להיום"
        private const val SHORTS_PSEUDO_PKG = "com.google.android.youtube.shorts"

        // === חבילת חסימה (block bundle) ===
        private const val PACK_ACTION_COOLDOWN_MS = 1200L  // min gap between two Home bounces
        private const val PACK_URL_SCAN_COOLDOWN_MS = 400L // max address-bar scan rate
        private const val BROWSER_BLOCK_MESSAGE = "הגלישה באינטרנט חסומה במכשיר הזה"
        private const val YOUTUBE_BLOCK_MESSAGE = "יוטיוב חסום במכשיר הזה"

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

        // Local self-restrictions apply to managers too, independently of community exemptions.
        if (pkg in WHATSAPP_PACKAGES && TetherPolicyManager.isWhatsAppBlockedLocally(this)) {
            if (canActOnWhatsApp()) {
                Toast.makeText(this, "וואטסאפ חסום בכלים העצמיים", Toast.LENGTH_SHORT).show()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // Foreground-app trace: one line per app switch (deduplicated, so it stays quiet). This is
        // the fastest way to learn the real package name of an OEM browser that slipped through —
        // open it on the device, then read the last line in the diagnostics console.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != lastForegroundPkg) {
            lastForegroundPkg = pkg
            Log.i(TAG, "Foreground app: $pkg")
        }

        val policy = TetherPolicyManager.loadPolicy(this)
        val blockedBehavior = policy?.blockedActionBehavior ?: BlockedActionBehavior.SILENT
        val hasTempApproval = TetherPolicyManager.isAppTemporarilyAllowed(this, pkg)

        // MANAGER DEVICE: while an admin is logged in on this device, none of the community
        // protections (installer/app/store/settings blocking) apply — a manager isn't locked down.
        // Only the SELF-imposed tools below (Shorts limit, local WhatsApp shield, schedules), which
        // the manager turned on themselves, keep working. Admin login requires server auth, so a
        // managed user can't reach this state.
        val managerExempt = AdminSession.isLoggedIn(this)

        if (pkg in WHATSAPP_PACKAGES && whatsAppAccessGuard.isPending(pkg)) {
            if (!isWhatsAppShieldActive()) {
                whatsAppAccessGuard.clear(pkg)
            } else {
                val root = rootInActiveWindow
                val reachedChats = try {
                    root?.packageName?.toString() == pkg && isWhatsAppChatsList(root, 0)
                } finally { root?.recycle() }
                if (reachedChats) whatsAppAccessGuard.reachedChats(pkg)
                else {
                    enforceWhatsAppExit(pkg)
                    return
                }
            }
        }

        // === Layer 1: APK installers — ALWAYS blocked, independent of app mode ===
        if (BlockPack.isInstaller(pkg) && !hasTempApproval && !managerExempt) {
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
        if (pkg !in CRITICAL_SYSTEM_PACKAGES && !hasTempApproval && policy != null && !managerExempt) {
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

        // === Layer 1c: חבילת חסימה — every browser on the device ===
        // The browser set is resolved from the system's http handlers (see BlockPack), so a browser
        // the user installs tomorrow is covered without any policy change. This is the app-level
        // half of the block: even with no Device Owner rights, opening a browser bounces Home.
        if (policy != null && policy.blockedBrowsers && !hasTempApproval && !managerExempt &&
            BlockPack.isBrowser(this, pkg)
        ) {
            if (canActOnPack()) {
                Log.w(TAG, "Blocking browser: $pkg")
                logEvent("BLOCKED_APP_OPENED", pkg)
                maybeRequestApproval(pkg, blockedBehavior)
                showPackMessage(BROWSER_BLOCK_MESSAGE, blockedBehavior)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // === Layer 1d: חבילת חסימה — YouTube in every form ===
        // 1) Every YouTube app variant (Kids / Music / TV / ReVanced / …), not just the main one.
        if (policy != null && policy.blockedYoutube && !hasTempApproval && !managerExempt &&
            BlockPack.isYoutube(pkg)
        ) {
            if (canActOnPack()) {
                Log.w(TAG, "Blocking YouTube app: $pkg")
                logEvent("BLOCKED_APP_OPENED", pkg)
                maybeRequestApproval(pkg, blockedBehavior)
                showPackMessage(YOUTUBE_BLOCK_MESSAGE, blockedBehavior)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // 2) youtube.com opened inside a browser. Only reachable when browsers are still allowed —
        //    if the whole bundle is on, Layer 1c already bounced this window. Only the address bar
        //    is inspected (never page content), so a page that merely mentions YouTube is safe.
        if (policy != null && policy.blockedYoutube && !policy.blockedBrowsers && !managerExempt &&
            !hasTempApproval && BlockPack.isBrowser(this, pkg) && canScanUrl()
        ) {
            val root = rootInActiveWindow
            if (root != null) {
                val url = try {
                    BlockPack.youtubeUrlInAddressBar(root)
                } catch (e: Exception) {
                    Log.e(TAG, "URL scan error: ${e.message}")
                    null
                } finally {
                    runCatching { root.recycle() }
                }
                if (url != null && canActOnPack()) {
                    Log.w(TAG, "Blocking YouTube in browser: $pkg ($url)")
                    logEvent("BLOCKED_APP_OPENED", BlockPack.YOUTUBE_WEB_PSEUDO_PKG)
                    showPackMessage(YOUTUBE_BLOCK_MESSAGE, blockedBehavior)
                    // Back first: leaves the browser usable on the previous page instead of
                    // bouncing the user out of the app entirely for one blocked tab.
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            // Deliberately no `return` — a browser window is still subject to the layers below.
        }

        // === Layer 2: Block app stores when any relevant policy flag is set ===
        if (BlockPack.isStore(pkg) && !hasTempApproval && !managerExempt) {
            if (policy == null || policy.blockedStores) {
                Log.w(TAG, "Blocking app store: $pkg")
                logEvent("BLOCKED_APP_OPENED", pkg)
                maybeRequestApproval(pkg, blockedBehavior)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // === Layer 3: Block dangerous settings screens ===
        if (pkg == "com.android.settings" && !managerExempt) {
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

        // === Layer 4: User-defined time-schedule blocks (local "App blocker" feature) ===
        // Independent of community policy: the user schedules apps to be blocked during certain
        // hours (e.g. at night). Only fires on window-state changes to avoid Home-bounce spam.
        // Formerly a separate accessibility service (AppBlockerAccessibilityService); folded in
        // here so the user only has to grant one accessibility permission.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != OUR_PACKAGE) {
            val activeSchedule = AppBlockerPrefs.getSchedules(this)
                .firstOrNull { it.isActiveNow() && pkg in it.blockedPackages }
            if (activeSchedule != null) {
                Log.w(TAG, "Blocking scheduled app: $pkg")
                logEvent("SCHEDULED_APP_BLOCKED", pkg)
                performGlobalAction(GLOBAL_ACTION_HOME)
                Toast.makeText(
                    this,
                    "חסום בין ${activeSchedule.formattedRange().replace("–", "ל-")}",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        // === Layer 5: WhatsApp Status & Channels shield ===
        // Enabled either by admin policy OR by the local user-controlled shield (Settings toggle).
        // Detection is language-independent (resource-id / class tokens), so it works for every
        // UI language — see whatsAppStatusOrChannelMarker(). Covers WhatsApp + WhatsApp Business.
        if (pkg in WHATSAPP_PACKAGES) {
            // Manager exempt: only the manager's OWN local shield toggle counts, not community policy.
            val shieldOn = TetherPolicyManager.isWhatsAppShieldEnabled(this) ||
                    (!managerExempt && policy?.blockWhatsAppChannels == true)
            if (!shieldOn) return

            // Throttle the noisy content-change stream (state-changes always pass through, so
            // tab switches / opening a status are still caught immediately).
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !canScanWhatsApp()) return

            val root = rootInActiveWindow ?: return
            try {
                val marker = whatsAppStatusOrChannelMarker(event, root)
                if (marker != null) {
                    if (whatsAppAccessGuard.block(pkg)) {
                        Log.w(TAG, "Blocking WhatsApp Status/Channels access (marker=$marker)")
                        logEvent("BLOCKED_APP_OPENED", "com.whatsapp.channels")
                        Toast.makeText(this, WHATSAPP_BLOCK_MESSAGE, Toast.LENGTH_LONG).show()
                    }
                    enforceWhatsAppExit(pkg)
                } else {
                    debugDumpWhatsAppIds(root)
                }
            } finally {
                root.recycle()
            }
            return
        }

        // === Layer 6: YouTube Shorts daily time limit (local self-restriction) ===
        // Regular videos are unrestricted; only the Shorts ("reel") player accrues time toward the
        // user's daily budget. When the budget is spent we bounce OUT of Shorts (Back) but leave
        // the rest of YouTube usable. Detection is language-independent (resource-id / class tokens).
        if (pkg in YOUTUBE_PACKAGES) {
            if (!ShortsLimitPrefs.isEnabled(this)) {
                shortsWatchingSince = 0L
                return
            }
            // Throttle the noisy content-change stream; window-state changes always pass through.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !canScanShorts()) return

            val root = rootInActiveWindow ?: return
            val marker = try {
                isYoutubeShortsOnScreen(event, root)
            } catch (e: Exception) {
                Log.e(TAG, "Shorts detection error: ${e.message}")
                null
            } finally {
                root.recycle()
            }
            val inShorts = marker != null

            val now = System.currentTimeMillis()
            if (inShorts) {
                // Log the detection transition (visible in the in-app diagnostic console / F12).
                if (shortsWatchingSince == 0L) Log.i(TAG, "Shorts DETECTED (marker=$marker)")
                // Telemetry: record (once per day) that Shorts detection is actually working on this
                // device / YouTube build — shows up in the admin activity log.
                logShortsDetectedOncePerDay()

                // Accrue the elapsed wall-clock time since the last tick (capped, so an idle/backgrounded
                // gap can't dump minutes at once).
                if (shortsWatchingSince != 0L) {
                    val delta = ((now - shortsWatchingSince) / 1000L).coerceIn(0L, SHORTS_MAX_TICK_SEC)
                    if (delta > 0L) ShortsLimitPrefs.addSeconds(this, delta)
                }
                shortsWatchingSince = now

                if (ShortsLimitPrefs.isOverLimit(this)) {
                    if (canActOnShorts()) {
                        Log.w(TAG, "YouTube Shorts daily limit reached — bouncing out")
                        maybeLogShortsLimitReached()
                        Toast.makeText(this, SHORTS_LIMIT_MESSAGE, Toast.LENGTH_LONG).show()
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                } else {
                    // Heads-up once, when the budget is about to run out.
                    val remaining = ShortsLimitPrefs.remainingSeconds(this)
                    if (remaining in 1..SHORTS_WARNING_SEC && !ShortsLimitPrefs.wasWarnedToday(this)) {
                        ShortsLimitPrefs.markWarnedToday(this)
                        logEvent("SHORTS_WARN_5MIN", SHORTS_PSEUDO_PKG)
                        Toast.makeText(this, SHORTS_WARNING_MESSAGE, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                if (shortsWatchingSince != 0L) {
                    Log.i(TAG, "Left Shorts (today used=${ShortsLimitPrefs.usedSecondsToday(this)}s)")
                }
                shortsWatchingSince = 0L
                // Debug tuning aid: on a new (non-Shorts) YouTube screen, dump visible ids so the real
                // Shorts ids of a given build can be confirmed from logcat if detection ever misses.
                if (BuildConfig.DEBUG && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    Log.d(TAG, "YouTube screen (not Shorts)")
                    debugDumpYoutubeIds()
                }
            }
            return
        }
    }

    /**
     * Returns a short marker string if the active WhatsApp window is the Status viewer or a
     * Channel ("newsletter") screen, else null. Resource/class detection is language-independent;
     * a Hebrew/English follow-button and follower-count fallback covers generic chat layouts.
     */
    private fun whatsAppStatusOrChannelMarker(event: AccessibilityEvent, root: AccessibilityNodeInfo): String? {
        event.className?.toString()?.lowercase()?.let { cls ->
            WHATSAPP_STATUS_CHANNEL_TOKENS.firstOrNull { cls.contains(it) }?.let { return "class:$it" }
        }
        findStatusChannelMarker(root, 0)?.let { return it }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<String>()
        collectWhatsAppLabels(root, 0, false, labels, actions)
        return if (WhatsAppScreenRules.isChannelSurface(labels, actions)) "channel:follow-and-followers" else null
    }

    private fun isWhatsAppShieldActive(): Boolean =
        TetherPolicyManager.isWhatsAppShieldEnabled(this) ||
            (!AdminSession.isLoggedIn(this) && TetherPolicyManager.loadPolicy(this)?.blockWhatsAppChannels == true)

    private fun isWhatsAppChatsList(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 25) return false
        val id = node.viewIdResourceName?.substringAfter("id/")?.lowercase().orEmpty()
        if (node.isVisibleToUser && (
                id.startsWith("conversations_row_") ||
                (node.isSelected && id in setOf("chats_tab", "conversations_tab", "navigation_chats"))
            )) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = try { isWhatsAppChatsList(child, depth + 1) } finally { child.recycle() }
            if (found) return true
        }
        return false
    }

    private fun enforceWhatsAppExit(packageName: String) {
        if (whatsAppExitJob?.isActive == true) return
        // Back unwinds the search itself before the channel. The latch is not cleared just because
        // search hides the channel identifiers, Home is pressed, or no more UI events arrive.
        whatsAppExitJob = serviceScope.launch(Dispatchers.Main.immediate) {
            var attempts = 0
            while (whatsAppAccessGuard.isPending(packageName)) {
                if (!isWhatsAppShieldActive()) {
                    whatsAppAccessGuard.clear(packageName)
                    break
                }
                val root = rootInActiveWindow
                val foreground = root?.packageName?.toString()
                val chats = try { isWhatsAppChatsList(root, 0) } finally { root?.recycle() }
                if (foreground == null) {
                    delay(200)
                    continue
                }
                if (foreground != packageName) {
                    val keyboardPackage = android.provider.Settings.Secure.getString(contentResolver,
                        android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
                    val whatsappBehindKeyboard = foreground == keyboardPackage && windows.any { window ->
                        val windowRoot = window.root
                        try { windowRoot?.packageName?.toString() == packageName }
                        finally { windowRoot?.recycle() }
                    }
                    if (whatsappBehindKeyboard) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(200)
                        continue
                    }
                    break
                }
                if (chats) {
                    whatsAppAccessGuard.reachedChats(packageName)
                    break
                }
                performGlobalAction(if (attempts++ < 8) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
                delay(200)
            }
        }
    }

    private fun collectWhatsAppLabels(
        node: AccessibilityNodeInfo?, depth: Int, clickableParent: Boolean,
        labels: MutableList<String>, actions: MutableList<String>
    ) {
        if (node == null || depth > 25 || labels.size >= 250) return
        val actionable = node.isClickable || clickableParent
        if (node.isVisibleToUser) {
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).forEach {
                labels.add(it)
                if (actionable) actions.add(it)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectWhatsAppLabels(child, depth + 1, actionable, labels, actions) }
            finally { child.recycle() }
        }
    }

    private fun findStatusChannelMarker(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 25) return null
        if (node.isVisibleToUser) {
            node.viewIdResourceName?.lowercase()?.let { id ->
                WhatsAppScreenRules.resourceMarker(id, node.isSelected)?.let { return "id:$it" }
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
     * Returns true if the active YouTube window is the Shorts ("reel") player. LANGUAGE-INDEPENDENT:
     * inspects YouTube's internal resource-ids / class names rather than localized text.
     */
    /** Returns the matched marker (e.g. "id:reel_recycler") if the Shorts player is on screen, else null. */
    private fun isYoutubeShortsOnScreen(event: AccessibilityEvent, root: AccessibilityNodeInfo): String? {
        event.className?.toString()?.lowercase()?.let { cls ->
            YOUTUBE_SHORTS_TOKENS.firstOrNull { cls.contains(it) }?.let { return "class:$it" }
        }
        return findShortsMarker(root, 0)
    }

    private fun findShortsMarker(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 25) return null
        if (node.isVisibleToUser) {
            node.viewIdResourceName?.lowercase()?.let { id ->
                YOUTUBE_SHORTS_TOKENS.firstOrNull { id.contains(it) }?.let { return "id:$it" }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findShortsMarker(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun canScanShorts(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShortsScanTs < SHORTS_SCAN_COOLDOWN_MS) return false
        lastShortsScanTs = now
        return true
    }

    private fun canActOnShorts(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShortsActionTs < SHORTS_ACTION_COOLDOWN_MS) return false
        lastShortsActionTs = now
        return true
    }

    // ── חבילת חסימה helpers ──

    /** Rate-limits the bundle's Home/Back bounces so one blocked window can't queue a burst. */
    private fun canActOnPack(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPackActionTs < PACK_ACTION_COOLDOWN_MS) return false
        lastPackActionTs = now
        return true
    }

    /** Rate-limits address-bar scans — they walk the node tree, so they can't run on every tick. */
    private fun canScanUrl(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUrlScanTs < PACK_URL_SCAN_COOLDOWN_MS) return false
        lastUrlScanTs = now
        return true
    }

    /**
     * Shows the bundle's block message, honouring the admin's blockedActionBehavior: SILENT stays
     * silent (the app just closes), and REQUEST_APPROVAL is handled by maybeRequestApproval, whose
     * own popup would otherwise be duplicated here.
     */
    private fun showPackMessage(message: String, behavior: BlockedActionBehavior) {
        if (behavior != BlockedActionBehavior.SHOW_MESSAGE) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** Emits a SHORTS_DETECTED telemetry event at most once per calendar day. */
    private fun logShortsDetectedOncePerDay() {
        val today = LocalDate.now().toString()
        if (shortsDetectedLogDate == today) return
        shortsDetectedLogDate = today
        logEvent("SHORTS_DETECTED", SHORTS_PSEUDO_PKG)
    }

    /** Emits a SHORTS_LIMIT_REACHED telemetry event, throttled so it can't flood the admin log. */
    private fun maybeLogShortsLimitReached() {
        val now = System.currentTimeMillis()
        if (now - lastShortsLimitLogTs < SHORTS_LIMIT_LOG_COOLDOWN_MS) return
        lastShortsLimitLogTs = now
        logEvent("SHORTS_LIMIT_REACHED", SHORTS_PSEUDO_PKG)
    }

    /**
     * On-device tuning aid (debug builds only): dumps the visible YouTube resource-ids to logcat so
     * a build's real Shorts ids can be confirmed and locked into YOUTUBE_SHORTS_TOKENS if detection
     * ever misses on a particular YouTube version.
     */
    private fun debugDumpYoutubeIds() {
        val now = System.currentTimeMillis()
        if (now - lastShortsDebugTs < SHORTS_DEBUG_COOLDOWN_MS) return
        lastShortsDebugTs = now
        val root = rootInActiveWindow ?: return
        try {
            val ids = mutableListOf<String>()
            collectVisibleIds(root, 0, ids)
            if (ids.isNotEmpty()) Log.d(TAG, "YT visible ids: ${ids.distinct().take(40).joinToString()}")
        } finally {
            root.recycle()
        }
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
