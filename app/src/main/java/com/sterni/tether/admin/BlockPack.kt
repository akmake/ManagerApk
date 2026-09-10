package com.sterni.tether.admin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.sterni.tether.data.model.CommunityPolicy
import com.sterni.tether.data.model.blockedBrowsers
import com.sterni.tether.data.model.blockedExternalInstalls
import com.sterni.tether.data.model.blockedStores
import com.sterni.tether.data.model.blockedYoutube

/**
 * "חבילת חסימה" — the app-level block bundle.
 *
 * A single source of truth for the four things the bundle covers:
 *   1. every browser on the device (resolved dynamically, so a browser installed tomorrow is
 *      covered without a server-side list update),
 *   2. YouTube in every form — all its app variants, plus youtube.com opened inside a browser,
 *   3. external / sideloaded installs (the package-installer UIs),
 *   4. every app store, including third-party store APKs users install to sideload.
 *
 * Everything here is enforced at the APP level: DPM hide/suspend when we are Device Owner, and a
 * real-time Accessibility bounce otherwise. Nothing in this file depends on the VPN or on DNS, so
 * it cannot be defeated by Private DNS / DNS-over-HTTPS.
 *
 * Shared by [TetherPolicyManager] (persistent DPM enforcement) and
 * [TetherAccessibilityService] (real-time kick-out).
 */
object BlockPack {

    private const val TAG = "TetherBlockPack"

    /** Browsers are re-resolved at most this often — the list only changes on install/uninstall. */
    private const val BROWSER_CACHE_TTL_MS = 5 * 60_000L

    @Volatile private var browserCache: Set<String> = emptySet()
    @Volatile private var browserCacheTs = 0L

    // ─────────────────────────────────────────────────────────────────────────────
    // YouTube — every shipping variant
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * All YouTube app forms. Blocking only com.google.android.youtube leaves Kids / Music / TV and
     * the popular mods wide open, which is the usual way the block gets bypassed.
     */
    val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",            // YouTube
        "com.google.android.apps.youtube.kids",  // YouTube Kids
        "com.google.android.apps.youtube.music", // YouTube Music
        "com.google.android.youtube.tv",         // YouTube for Android TV
        "com.google.android.apps.youtube.gaming",// YouTube Gaming
        "com.google.android.apps.youtube.creator",// YouTube Studio
        "com.google.android.apps.youtube.unplugged", // YouTube TV
        "com.google.android.apps.youtube.mango", // YouTube Go
        "app.revanced.android.youtube",          // ReVanced
        "app.rvx.android.youtube",               // ReVanced Extended
        "com.vanced.android.youtube",            // Vanced (legacy)
        "com.google.android.youtube.music"       // some OEM builds ship this alias
    )

    /** Pseudo-package used when logging a YouTube-in-browser block to the admin activity log. */
    const val YOUTUBE_WEB_PSEUDO_PKG = "com.google.android.youtube.web"

    /**
     * Host fragments that identify a YouTube page inside a browser's address bar.
     * Matched case-insensitively against the URL-bar text only — never against page content, so a
     * page that merely mentions "youtube.com" does not trigger a block.
     */
    private val YOUTUBE_URL_TOKENS = listOf(
        "youtube.com",
        "youtu.be",
        "youtubekids.com",
        "yt.be"
    )

    /**
     * Address-bar resource-ids across the major browser engines. These are internal ids (always
     * English), so detection is language-independent.
     */
    private val BROWSER_URL_BAR_IDS = listOf(
        "url_bar",                          // Chrome / Chromium / Edge / Brave / Opera
        "location_bar",
        "url_field",
        "search_box_text",
        "mozac_browser_toolbar_url_view",   // Firefox / Focus (GeckoView toolbar)
        "url_bar_title",
        "addressbarEdit",                   // Samsung Internet
        "sesl_search_view_edit_text",
        "search_text",                      // UC / Vivaldi variants
        "omnibox"
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Stores & installers
    // ─────────────────────────────────────────────────────────────────────────────

    /** First-party / OEM app stores. */
    val OEM_STORE_PACKAGES = setOf(
        "com.android.vending",                    // Google Play
        "com.google.android.finsky",              // Play backend
        "com.sec.android.app.samsungapps",        // Samsung Galaxy Store
        "com.samsung.android.app.settings.bixby",
        "com.amazon.venezia",                     // Amazon Appstore
        "com.amazon.mShop.android.shopping",
        "com.huawei.appmarket",                   // Huawei AppGallery
        "com.xiaomi.market",                      // Xiaomi GetApps
        "com.oppo.market",                        // Oppo
        "com.heytap.market",                      // Oppo/Realme (newer)
        "com.vivo.appstore",                      // Vivo
        "com.bbk.appstore",
        "com.meizu.mstore",
        "com.transsion.phoenix",
        "com.lenovo.leos.appstore"
    )

    /**
     * Third-party store / sideload-marketplace APKs. These are the real bypass route: the user
     * installs one of these and pulls APKs straight from it, never touching Play. Blocking the
     * OEM stores alone leaves this route open.
     */
    val THIRD_PARTY_STORE_PACKAGES = setOf(
        "cm.aptoide.pt",                    // Aptoide
        "com.aptoide.partners",
        "com.apkpure.aegon",                // APKPure
        "com.apkmirror.helper.prod",        // APKMirror Installer
        "org.fdroid.fdroid",                // F-Droid
        "org.fdroid.basic",
        "com.uptodown",                     // Uptodown
        "com.uptodown.installer",
        "com.mobogenie",                    // Mobogenie
        "com.mobile.indiapp",               // 9Apps
        "me.onemobile.android",
        "com.blackmart.market",             // Blackmart
        "com.happymod.apk",                 // HappyMod
        "com.dsemu.drastic.store",
        "com.appcake",                      // AppCake
        "ru.rustore.app",                   // RuStore
        "com.farsitel.bazaar",              // Cafe Bazaar
        "ir.mservices.market",              // Myket
        "com.tencent.android.qqdownloader", // Tencent MyApp
        "com.qihoo.appstore",               // 360 Mobile Assistant
        "com.baidu.appsearch",
        "com.wandoujia.phoenix2",           // Wandoujia
        "com.pp.assistant",                 // PP Assistant
        "com.taptap",                       // TapTap
        "com.taptap.global",
        "com.setupwizard.store"
    )

    /** Package-installer UIs — the component that actually installs a sideloaded APK. */
    val INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.managedprovisioning",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.samsung.android.app.installserv",
        "com.sec.android.app.installserv",
        "com.huawei.android.packageinstaller",
        "com.oppo.packageinstaller",
        "com.coloros.packageinstaller",
        "com.heytap.packageinstaller",
        "com.vivo.packageinstaller",
        "com.transsion.packageinstaller",
        "com.lenovo.packageinstaller"
    )

    /** Every store the bundle blocks. */
    val ALL_STORE_PACKAGES: Set<String> = OEM_STORE_PACKAGES + THIRD_PARTY_STORE_PACKAGES

    // ─────────────────────────────────────────────────────────────────────────────
    // Browsers — resolved dynamically
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fallback list, unioned with the dynamic result. A handful of browsers register their http
     * handler in ways [resolveBrowsers] can miss (or are hidden by DPM at the moment we query, in
     * which case they no longer resolve as an http handler), so we keep naming the common ones
     * explicitly. The dynamic pass is what covers everything else, including future installs.
     */
    private val KNOWN_BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.android.browser",
        "com.google.android.browser",
        "com.sec.android.app.sbrowser",          // Samsung Internet
        "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.opera.touch",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.microsoft.emmx",                    // Edge
        "com.microsoft.emmx.canary",
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl",
        "com.uc.browser.en",
        "com.ucmobile.lite",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.yandex.browser",
        "com.yandex.browser.lite",
        "org.torproject.torbrowser",
        "com.mi.globalbrowser",                  // Mi Browser
        "com.android.browser.mi",
        "com.heytap.browser",                    // Oppo/Realme
        "com.coloros.browser",
        "com.vivo.browser",
        "com.huawei.browser",
        "com.transsion.phoenix.browser",
        "com.cloudmosa.puffinFree",              // Puffin
        "acr.browser.lightning",                 // Lightning
        "com.jio.web",
        "com.phoenix.browser",
        "com.aloha.browser",
        "com.talpa.hibrowser",
        "info.guardianproject.orfox",
        "com.stoutner.privacybrowser.standard",
        "idm.internet.download.manager.plus",    // ships a full browser
        "com.hidebrowser.dolphin",
        "mobi.mgeek.TunnyBrowser",               // Dolphin
        "com.explore.web.browser",
        "com.ksmobile.cb",                       // CM Browser
        "com.tencent.mtt",                       // QQ Browser
        "sogou.mobile.explorer",
        "com.baidu.browser.apps"
    )

    /**
     * Every browser currently on the device.
     *
     * Resolved by asking the system which activities can open a bare `http://` URL with no host —
     * only real browsers register that, whereas an app with a deep link for its own domain does
     * not. This is why the bundle keeps working for a browser installed after the policy was set.
     *
     * Cached for [BROWSER_CACHE_TTL_MS]; call [invalidateBrowserCache] after an install/uninstall.
     */
    fun browsers(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        val cached = browserCache
        if (cached.isNotEmpty() && now - browserCacheTs < BROWSER_CACHE_TTL_MS) return cached

        val resolved = resolveBrowsers(context) + installedOnly(context, KNOWN_BROWSER_PACKAGES)
        val result = resolved - context.packageName
        browserCache = result
        browserCacheTs = now
        Log.i(TAG, "Resolved ${result.size} browsers on device")
        return result
    }

    /** Drops the cache so the next [browsers] call re-queries — used after an install/uninstall. */
    fun invalidateBrowserCache() {
        browserCacheTs = 0L
        browserCache = emptySet()
    }

    private fun resolveBrowsers(context: Context): Set<String> {
        val pm = context.packageManager
        // Scheme-only URI: matches apps that handle *any* http URL (i.e. browsers), not apps that
        // only claim a specific host via app links.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            val flags = PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS
            val handlers = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, flags)
            }
            handlers.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Browser resolution failed, falling back to the static list: ${e.message}")
            emptySet()
        }
    }

    private fun installedOnly(context: Context, packages: Set<String>): Set<String> {
        val pm = context.packageManager
        return packages.filter { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }.toSet()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Membership checks
    // ─────────────────────────────────────────────────────────────────────────────

    fun isBrowser(context: Context, pkg: String): Boolean = pkg in browsers(context)

    fun isYoutube(pkg: String): Boolean = pkg in YOUTUBE_PACKAGES

    fun isStore(pkg: String): Boolean = pkg in ALL_STORE_PACKAGES

    fun isInstaller(pkg: String): Boolean = pkg in INSTALLER_PACKAGES

    /**
     * Every package the bundle blocks under [policy], for the DPM hide/suspend pass.
     *
     * Note this is computed per-device (browsers are resolved live), so two devices in the same
     * community can end up with different sets from the identical policy — which is the point.
     */
    fun blockedPackages(context: Context, policy: CommunityPolicy): Set<String> {
        val result = mutableSetOf<String>()
        if (policy.blockedBrowsers) result += browsers(context)
        if (policy.blockedYoutube) result += YOUTUBE_PACKAGES
        if (policy.blockedStores) result += ALL_STORE_PACKAGES
        if (policy.blockedExternalInstalls) result += INSTALLER_PACKAGES
        // Never lock ourselves out — Tether must stay reachable to release the policy.
        return result - context.packageName
    }

    /**
     * True when the visible address bar of a browser window is pointed at YouTube.
     *
     * Only the URL bar is inspected (by resource-id), never arbitrary page text, so a search
     * results page that merely mentions YouTube is not treated as a visit. Returns the matched
     * URL for the audit log, or null.
     */
    fun youtubeUrlInAddressBar(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        for (id in BROWSER_URL_BAR_IDS) {
            val nodes = try {
                // findAccessibilityNodeInfosByViewId needs the fully-qualified id; browsers differ
                // in package, so we scan by suffix in findUrlBarText() instead when this misses.
                root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id")
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                for (node in nodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    val hit = text?.let { matchYoutubeUrl(it) }
                    if (hit != null) return hit
                }
            } finally {
                nodes.forEach { runCatching { it.recycle() } }
            }
        }
        // Fallback: walk the tree looking for a node whose id *ends with* a known url-bar id.
        return findUrlBarText(root, 0)
    }

    private fun findUrlBarText(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 20) return null
        if (node.isVisibleToUser) {
            val id = node.viewIdResourceName?.substringAfter("id/")?.lowercase()
            if (id != null && BROWSER_URL_BAR_IDS.any { id == it || id.endsWith(it) }) {
                val text = node.text?.toString() ?: node.contentDescription?.toString()
                val hit = text?.let { matchYoutubeUrl(it) }
                if (hit != null) return hit
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlBarText(child, depth + 1)
            runCatching { child.recycle() }
            if (found != null) return found
        }
        return null
    }

    private fun matchYoutubeUrl(text: String): String? {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return null
        return if (YOUTUBE_URL_TOKENS.any { lower.contains(it) }) lower.take(120) else null
    }
}
