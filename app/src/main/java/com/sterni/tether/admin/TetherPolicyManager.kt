package com.sterni.tether.admin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sterni.tether.R
import com.sterni.tether.data.model.AppTimeLock
import com.sterni.tether.data.model.BlockedActionBehavior
import com.sterni.tether.data.model.CommunityPolicy

import android.content.Intent
import com.sterni.tether.data.model.WebFilterMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TetherPolicyManager {

    private const val TAG = "TetherPolicy"
    private const val PREFS = "tether_policy"
    private const val KEY_POLICY = "current_policy"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_COMMUNITY_ID = "community_id"
    private const val KEY_COMMUNITY_NAME = "community_name"
    private const val KEY_UNINSTALL_EXPIRES_AT = "uninstall_expires_at"  // epoch ms, 0 = no active window
    private const val KEY_TEMP_APP_APPROVALS = "temp_app_approvals"        // JSON: package -> expiry epoch ms
    private const val KEY_WHATSAPP_SHIELD = "user_shield_whatsapp"          // local user toggle (not admin)
    private const val UNINSTALL_WINDOW_MS = 60 * 60 * 1000L              // 1 hour
    private const val CHANNEL_RELEASE = "tether_release"
    private const val NOTIF_ID_RELEASE = 3001

    private val gson = Gson()

    // רקע לפעולות כבדות (החלת מדיניות) כדי לא לחסום את ה-thread של מי שקרא לנו
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun applyPolicy(context: Context, policy: CommunityPolicy) {
        savePolicy(context, policy)
        applyStoredPolicy(context)
        applyWebFilter(context, policy)
    }

    fun applyStoredPolicy(context: Context) {
        val policy = loadPolicy(context) ?: return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner — policy enforcement limited")
        }

        try {
            // הגדרת האפליקציה כ-Whitelisted ל-Lock Task Mode
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            }

            // הגנה קיצונית: חסימת הסרה ברמת מערכת
            val allowUninstall = isUninstallWindowActive(context)
            dpm.setUninstallBlocked(admin, context.packageName, !allowUninstall)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                dpm.setApplicationHidden(admin, context.packageName, false)
            }

            applyInstallRestriction(dpm, context, policy.blockInstallApps, allowUninstall)
            applySafeBootRestriction(dpm, context, policy.blockSafeBoot)
            applyFactoryResetRestriction(dpm, context, policy.blockFactoryReset)
            applyUsbRestriction(dpm, context, policy.blockUsbTransfer)
            applyTimeLock(dpm, context, policy.lockedUntilTs, policy.blockedApps)
            applyAppTimeLocks(dpm, context, policy.appTimeLocks)

            val now = System.currentTimeMillis()
            val timeLockActive = policy.lockedUntilTs != null && policy.lockedUntilTs > now
            val temporaryAllowed = getActiveTemporaryApprovedPackages(context)
            val effectiveAllowed = (policy.allowedApps + temporaryAllowed).distinct()
            
            // חשוב: אם יש נעילת זמן פעילה, לא משנה מה ה-allowedApps, הכל חסום (חוץ מחייגן)
            // אם אין נעילה, אנחנו מחילים את החסימות הרגילות.
            applyAppSuspension(dpm, context, if (timeLockActive) emptyList() else effectiveAllowed, policy.blockedApps, policy.maxInstalledApps)

            applyHiddenApps(dpm, context, policy.hideGooglePlay, policy.blockAllStores, policy.blockedApps)
            applyAntiBypassRestrictions(dpm, context, true)

            Log.i(TAG, "Policy applied successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "CRITICAL ERROR: This app is NOT a Device Owner. Policy cannot be enforced.", e)
            // DO NOT THROW HERE! Throwing here causes MainActivity to crash instantly on startup.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply policy: ${e.message}", e)
        }
    }

    private fun applyInstallRestriction(
        dpm: DevicePolicyManager,
        context: Context,
        block: Boolean,
        allowUninstall: Boolean
    ) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) {
            // חסימה גורפת של התקנות (כולל התקנות מרחוק מהמחשב ומהדפדפן)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            
            // חסימת כל אפשרות להגדרת חשבונות (מונע הוספת חשבון גוגל חדש למעקף)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            
            // חסימת אפשרות למחיקת אפליקציות באופן גורף
            if (allowUninstall) dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            else dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            
            // חסימת ה-Package Installer עצמו - מונע הרצת קבצי APK באופן פיזי
            val installers = listOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.managedprovisioning",
                "com.samsung.android.packageinstaller",
                "com.samsung.android.app.installserv"
            )
            installers.forEach { pkg ->
                try {
                    dpm.setApplicationHidden(admin, pkg, true)
                    dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
                } catch (e: Exception) {}
            }
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            
            val installers = listOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"
            )
            installers.forEach { pkg ->
                try {
                    dpm.setApplicationHidden(admin, pkg, false)
                    dpm.setPackagesSuspended(admin, arrayOf(pkg), false)
                } catch (e: Exception) {}
            }
        }
    }

    private fun applySafeBootRestriction(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        else dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
    }

    private fun applyFactoryResetRestriction(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        else dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
    }

    private fun applyUsbRestriction(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
        else dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
    }

    private fun applyHiddenApps(
        dpm: DevicePolicyManager,
        context: Context,
        hideGooglePlay: Boolean,
        blockAllStores: Boolean,
        additionalBlockedApps: List<String>
    ) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        // Google Play + its backend — blocked when hideGooglePlay OR blockAllStores
        val playPackages = listOf(
            "com.android.vending",
            "com.google.android.gms.policy_sidecar_aps",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )

        // Third-party stores — blocked only when blockAllStores
        val otherStorePackages = listOf(
            "com.sec.android.app.samsungapps",
            "com.samsung.android.app.installserv",
            "com.sec.android.app.installserv",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.huawei.appmarket",
            "com.amazon.venezia",
            "com.xiaomi.market",
            "com.oppo.market",
            "com.vivo.appstore"
        )

        val blockPlay = hideGooglePlay || blockAllStores

        playPackages.forEach { pkg ->
            runCatching {
                dpm.setApplicationHidden(admin, pkg, blockPlay)
                dpm.setPackagesSuspended(admin, arrayOf(pkg), blockPlay)
            }.onFailure { Log.w(TAG, "Could not restrict $pkg: ${it.message}") }
        }

        otherStorePackages.forEach { pkg ->
            runCatching {
                dpm.setApplicationHidden(admin, pkg, blockAllStores)
                dpm.setPackagesSuspended(admin, arrayOf(pkg), blockAllStores)
            }.onFailure { Log.w(TAG, "Could not restrict $pkg: ${it.message}") }
        }

        additionalBlockedApps.forEach { pkg ->
            runCatching {
                dpm.setApplicationHidden(admin, pkg, true)
                dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
            }.onFailure { Log.e(TAG, "Failed to restrict $pkg: ${it.message}") }
        }
    }

    fun savePolicy(context: Context, policy: CommunityPolicy) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POLICY, gson.toJson(policy))
            .apply()
    }

    fun loadPolicy(context: Context): CommunityPolicy? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POLICY, null) ?: return null
        return try {
            gson.fromJson(json, CommunityPolicy::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveEnrollment(context: Context, deviceId: String, communityId: String, communityName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_COMMUNITY_ID, communityId)
            .putString(KEY_COMMUNITY_NAME, communityName)
            .putLong(KEY_UNINSTALL_EXPIRES_AT, 0L)  // always start fresh — no leftover windows
            .apply()
    }

    fun setAdminAuthenticated(context: Context, authenticated: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("admin_auth", authenticated)
            .putLong("admin_auth_time", if (authenticated) System.currentTimeMillis() else 0L)
            .apply()
    }

    fun isAdminAuthenticated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val authenticated = prefs.getBoolean("admin_auth", false)
        val authTime = prefs.getLong("admin_auth_time", 0L)
        
        // Session valid for 24 hours of inactivity for better UX since it's a management app
        val isValid = (System.currentTimeMillis() - authTime) < (24 * 60 * 60 * 1000)
        if (authenticated && !isValid) {
            setAdminAuthenticated(context, false)
            return false
        }
        return authenticated
    }

    fun getDeviceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_ID, null)

    /** Per-device secret token (issued at join). Sent as X-Device-Token on every device call. */
    fun saveDeviceToken(context: Context, token: String?) {
        if (token.isNullOrEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEVICE_TOKEN, token)
            .apply()
    }

    fun getDeviceToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_TOKEN, null)

    fun getCommunityId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMUNITY_ID, null)

    fun getCommunityName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMUNITY_NAME, null)

    fun isEnrolled(context: Context): Boolean = getDeviceId(context) != null

    /**
     * מתג מקומי בשליטת המשתמש (לא המנהל): חסימת סטטוס וערוצים בוואטסאפ.
     * נשמר במכשיר בלבד ועובד באופן עצמאי מהמדיניות שמגיעה מהשרת.
     */
    fun isWhatsAppShieldEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WHATSAPP_SHIELD, false)

    fun setWhatsAppShieldEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WHATSAPP_SHIELD, enabled)
            .apply()
    }

    /** Starts a 1-hour local uninstall window. Works fully offline. */
    fun startUninstallWindow(context: Context) {
        val expiresAt = System.currentTimeMillis() + UNINSTALL_WINDOW_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNINSTALL_EXPIRES_AT, expiresAt)
            .apply()
        Log.i(TAG, "Uninstall window started, expires in 1h (at $expiresAt)")
    }

    fun clearUninstallWindow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNINSTALL_EXPIRES_AT, 0L)
            .apply()
    }

    fun isUninstallWindowActive(context: Context): Boolean {
        val expiresAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UNINSTALL_EXPIRES_AT, 0L)
        return expiresAt > 0L && System.currentTimeMillis() < expiresAt
    }

    fun getUninstallExpiresAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UNINSTALL_EXPIRES_AT, 0L)

    fun clearEnrollment(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun loadTempApprovals(context: Context): MutableMap<String, Long> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TEMP_APP_APPROVALS, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            gson.fromJson<MutableMap<String, Long>>(json, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveTempApprovals(context: Context, map: Map<String, Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEMP_APP_APPROVALS, gson.toJson(map))
            .apply()
    }

    fun grantTemporaryAppAccess(context: Context, packageName: String, expiresAt: Long) {
        val pkg = packageName.trim().lowercase()
        if (pkg.isEmpty() || expiresAt <= System.currentTimeMillis()) return

        val approvals = loadTempApprovals(context)
        approvals[pkg] = expiresAt
        saveTempApprovals(context, approvals)

        // Release this package immediately so approval is usable right away.
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setPackagesSuspended(admin, arrayOf(pkg), false)
                dpm.setApplicationHidden(admin, pkg, false)
            }
        }
        Log.i(TAG, "Temporary app approval granted for $pkg until $expiresAt")
    }

    fun isAppTemporarilyAllowed(context: Context, packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val approvals = loadTempApprovals(context)
        pruneExpiredApprovals(context, approvals, now)
        return (approvals[packageName.trim().lowercase()] ?: 0L) > now
    }

    fun cleanupExpiredTemporaryApprovals(context: Context) {
        val approvals = loadTempApprovals(context)
        pruneExpiredApprovals(context, approvals, System.currentTimeMillis())
    }

    private fun getActiveTemporaryApprovedPackages(context: Context): List<String> {
        val now = System.currentTimeMillis()
        val approvals = loadTempApprovals(context)
        pruneExpiredApprovals(context, approvals, now)
        return approvals.entries
            .asSequence()
            .filter { it.value > now }
            .map { it.key.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
            .toList()
    }

    private fun pruneExpiredApprovals(context: Context, approvals: MutableMap<String, Long>, now: Long) {
        var changed = false
        val iterator = approvals.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
                changed = true
            }
        }
        if (!changed) return
        saveTempApprovals(context, approvals)
        // Re-apply base policy to restore restrictions after expiry.
        // נריץ ברקע — הפונקציה הזו נקראת גם מה-thread הראשי של שירות הנגישות,
        // ו-applyStoredPolicy סורקת את כל האפליקציות (פעולה כבדה).
        val appContext = context.applicationContext
        bgScope.launch { applyStoredPolicy(appContext) }
    }

    /**
     * מסיר את מעמד ה-Device Owner מהאפליקציה ומאפשר מחיקה שלה.
     */
    fun removeDeviceOwner(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // Device Owner — מנקה את כל ההגבלות לפני הסרה
                listOf(
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_USB_FILE_TRANSFER,
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    UserManager.DISALLOW_APPS_CONTROL,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS
                ).forEach { restriction ->
                    try { dpm.clearUserRestriction(admin, restriction) } catch (_: Exception) {}
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    try { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS) } catch (_: Exception) {}
                }
                dpm.setUninstallBlocked(admin, context.packageName, false)
                dpm.clearDeviceOwnerApp(context.packageName)
                Log.i(TAG, "Device Owner removed successfully")
            } else if (dpm.isAdminActive(admin)) {
                // Device Admin בלבד — מספיק להסיר את עצמנו
                dpm.removeActiveAdmin(admin)
                Log.i(TAG, "Device Admin removed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove device admin: ${e.message}", e)
        }
    }

    fun releaseAllAndUninstall(context: Context, force: Boolean = false): Boolean {
        if (!force && !isUninstallWindowActive(context)) {
            Log.w(TAG, "Blocked releaseAllAndUninstall without active uninstall window")
            return false
        }

        clearUninstallWindow(context)
        removeDeviceOwner(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_COMMUNITY_ID)
            .remove(KEY_COMMUNITY_NAME)
            .remove(KEY_POLICY)
            .apply()
        // Can't startActivity from background (Android 10+) — show notification instead.
        showUninstallNotification(context)
        return true
    }

    fun showUninstallNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RELEASE, "שחרור הגנה", NotificationManager.IMPORTANCE_HIGH)
        )
        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, NOTIF_ID_RELEASE, uninstallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_RELEASE)
            .setContentTitle("ניתן להסיר את Tether")
            .setContentText("המנהל אישר הסרת האפליקציה. לחץ כדי להסיר.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("המנהל אישר הסרת האפליקציה. לחץ על ההתראה כדי לפתוח את דיאלוג המחיקה."))
            .setSmallIcon(R.drawable.app_logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID_RELEASE, notif)
        Log.i(TAG, "Uninstall notification shown")
    }

    fun applyWebFilter(context: Context, policy: CommunityPolicy) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        // VPN needed for DNS-level Play Store blocking OR web filter
        val vpnNeeded = policy.hideGooglePlay || policy.webFilterMode != WebFilterMode.NONE

        if (vpnNeeded) {
            context.startForegroundService(Intent(context, TetherVpnService::class.java))
            context.startForegroundService(Intent(context, TetherOverlayService::class.java))

            try {
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setAlwaysOnVpnPackage(admin, context.packageName, true)
                    Log.i(TAG, "Always-on VPN enabled for ${context.packageName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set Always-on VPN", e)
            }
        } else {
            try {
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setAlwaysOnVpnPackage(admin, null, false)
                }
            } catch (e: Exception) {}

            context.startService(Intent(context, TetherVpnService::class.java).apply {
                action = "STOP"
            })
        }
    }

    private fun applyAntiBypassRestrictions(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS)
            }
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS)
            }
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
    }

    private fun applyAppSuspension(
        dpm: DevicePolicyManager,
        context: Context,
        allowedApps: List<String>,
        blockedApps: List<String>,
        maxInstalledApps: Int?
    ) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        try {
            val pm = context.packageManager
            val allApps = pm.getInstalledApplications(0)
            
            // אפליקציות מערכת קריטיות
            val criticalSystemApps = setOf(
                context.packageName,
                "com.android.settings",
                "com.android.phone",
                "com.android.server.telecom",
                "com.android.systemui",
                "com.android.dialer",
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.android.chrome",
                "com.sec.android.app.launcher"
            )

            // רשימת חנויות ומתקינים שחייבים להיעלם לחלוטין (Hidden)
            val storesToHide = setOf(
                "com.android.vending",
                "com.sec.android.app.samsungapps",
                "com.samsung.android.app.installserv",
                "com.sec.android.app.installserv",
                "com.samsung.android.app.settings.bixby"
            )

            val toSuspend = mutableListOf<String>()
            val toRelease = mutableListOf<String>()
            val allowedCandidates = mutableListOf<String>()

            for (app in allApps) {
                val pkg = app.packageName
                
                // 1. העלמת חנויות
                if (storesToHide.contains(pkg)) {
                    dpm.setApplicationHidden(admin, pkg, true)
                    continue
                }

                if (criticalSystemApps.contains(pkg)) {
                    toRelease.add(pkg)
                    dpm.setApplicationHidden(admin, pkg, false)
                    continue
                }
                
                // 2. לוגיקת Whitelist
                if (allowedApps.contains(pkg) && !blockedApps.contains(pkg)) {
                    toRelease.add(pkg)
                    allowedCandidates.add(pkg)
                    dpm.setApplicationHidden(admin, pkg, false)
                } else {
                    toSuspend.add(pkg)
                }
            }

            if (maxInstalledApps != null && maxInstalledApps >= 0) {
                val maxAllowed = maxInstalledApps.coerceAtLeast(0)
                if (allowedCandidates.size > maxAllowed) {
                    val overflow = allowedCandidates.sorted().drop(maxAllowed)
                    overflow.forEach { pkg ->
                        toRelease.remove(pkg)
                        if (!toSuspend.contains(pkg)) toSuspend.add(pkg)
                    }
                    Log.w(TAG, "maxInstalledApps enforced: allowed=${allowedCandidates.size} max=$maxAllowed")
                }
            }

            if (toSuspend.isNotEmpty()) {
                dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true)
            }
            if (toRelease.isNotEmpty()) {
                dpm.setPackagesSuspended(admin, toRelease.toTypedArray(), false)
            }
            
            Log.i(TAG, "Security lockdown applied: Stores hidden, Whitelist enforced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed applying security policy", e)
        }
    }

    private fun applyAppTimeLocks(dpm: DevicePolicyManager, context: Context, locks: List<AppTimeLock>) {
        if (locks.isEmpty()) return
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        val now = System.currentTimeMillis()
        for (lock in locks) {
            val shouldBlock = lock.lockedUntilTs == null || lock.lockedUntilTs > now
            try {
                dpm.setPackagesSuspended(admin, arrayOf(lock.packageName), shouldBlock)
                Log.d(TAG, "App time-lock ${lock.packageName}: suspended=$shouldBlock")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set time-lock for ${lock.packageName}: ${e.message}")
            }
        }
    }

    private fun applyTimeLock(dpm: DevicePolicyManager, context: Context, lockedUntilTs: Long?, permanentlyBlocked: List<String>) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        val now = System.currentTimeMillis()
        
        try {
            val pm = context.packageManager
            val allPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .filter { 
                    // רשימת אפליקציות החרגה - החייגן (Phone) כדי לאפשר קבלת שיחות ואת האפליקציה שלנו!
                    it != context.packageName && 
                    it != "com.android.server.telecom" && 
                    it != "com.android.dialer" && 
                    it != "com.samsung.android.dialer" &&
                    it != "com.android.phone"
                }

            // המכשיר נמצא בזמן נעילה על ידי המנהל (Time Lock)
            if (lockedUntilTs != null && lockedUntilTs > now) {
                dpm.setPackagesSuspended(admin, allPackages.toTypedArray(), true)
                Log.i(TAG, "Time Lock Enforced. All non-dialer apps suspended until $lockedUntilTs")
            } else {
                // הזמן פג או שאין נעילה - פתח חזרה הכל חוץ ממה שחסום לצמיתות!
                val appsToRelease = allPackages.filter { it !in permanentlyBlocked }
                dpm.setPackagesSuspended(admin, appsToRelease.toTypedArray(), false)
                
                // וודא שמה שחסום לצמיתות נשאר חסום
                if (permanentlyBlocked.isNotEmpty()) {
                    dpm.setPackagesSuspended(admin, permanentlyBlocked.toTypedArray(), true)
                }
                Log.i(TAG, "Time Lock Lifted. Permanently blocked apps remain suspended.")
            }
        } catch (e: Exception) {
             Log.e(TAG, "Failed TimeLock operation", e)
        }
    }
}




