package com.sterni.tether.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log
import com.google.gson.Gson
import com.sterni.tether.data.model.AppTimeLock
import com.sterni.tether.data.model.BlockedActionBehavior
import com.sterni.tether.data.model.CommunityPolicy

import android.content.Intent
import com.sterni.tether.data.model.WebFilterMode

object TetherPolicyManager {

    private const val TAG = "TetherPolicy"
    private const val PREFS = "tether_policy"
    private const val KEY_POLICY = "current_policy"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_COMMUNITY_ID = "community_id"
    private const val KEY_COMMUNITY_NAME = "community_name"
    private const val KEY_ALLOW_UNINSTALL = "allow_uninstall"

    private val gson = Gson()

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
            val allowUninstall = isUninstallAllowed(context)
            dpm.setUninstallBlocked(admin, context.packageName, !allowUninstall)
            
            // מניעת השבתת האפליקציה (Disable)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                dpm.setApplicationHidden(admin, context.packageName, false)
            }

            applyInstallRestriction(dpm, context, policy.blockInstallApps)
            applySafeBootRestriction(dpm, context, policy.blockSafeBoot)
            applyFactoryResetRestriction(dpm, context, policy.blockFactoryReset)
            applyUsbRestriction(dpm, context, policy.blockUsbTransfer)
            // טיפול בנעילת זמן לכלל המסך/אפליקציות
            applyTimeLock(dpm, context, policy.lockedUntilTs)
            // נעילת זמן פר-אפליקציה
            applyAppTimeLocks(dpm, context, policy.appTimeLocks)

            // טיפול באפליקציות - החרגות
            val now = System.currentTimeMillis()
            val timeLockActive = policy.lockedUntilTs != null && policy.lockedUntilTs > now
            applyAppSuspension(dpm, context, if (timeLockActive) emptyList() else policy.allowedApps, policy.blockedApps)

            applyHiddenApps(dpm, context, policy.hideGooglePlay, policy.blockAllStores, policy.blockedApps)
            applyAntiBypassRestrictions(dpm, context, true)

            if (allowUninstall) {
                try {
                    dpm.clearDeviceOwnerApp(context.packageName)
                    Log.i(TAG, "Cleared DO app because uninstall is allowed")
                } catch(e: Exception) {
                    Log.w(TAG, "Could not clear DO", e)
                }
            }

            Log.i(TAG, "Policy applied successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "CRITICAL ERROR: This app is NOT a Device Owner. Policy cannot be enforced.", e)
            // DO NOT THROW HERE! Throwing here causes MainActivity to crash instantly on startup.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply policy: ${e.message}", e)
        }
    }

    private fun applyInstallRestriction(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        if (block) {
            // חסימה גורפת של התקנות (כולל התקנות מרחוק מהמחשב ומהדפדפן)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            
            // חסימת כל אפשרות להגדרת חשבונות (מונע הוספת חשבון גוגל חדש למעקף)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            
            // חסימת ה-Package Installer עצמו - מונע הרצת קבצי APK באופן פיזי
            val installers = listOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.managedprovisioning"
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
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller"
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
            .apply()
    }

    fun getDeviceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_ID, null)

    fun getCommunityId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMUNITY_ID, null)

    fun getCommunityName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMUNITY_NAME, null)

    fun isEnrolled(context: Context): Boolean = getDeviceId(context) != null

    fun saveAllowUninstall(context: Context, allow: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALLOW_UNINSTALL, allow)
            .apply()
    }

    fun isUninstallAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_UNINSTALL, false)

    fun clearEnrollment(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
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

    fun releaseAllAndUninstall(context: Context) {
        // Signal accessibility service to stand down FIRST — otherwise it blocks the uninstall dialog
        saveAllowUninstall(context, true)
        removeDeviceOwner(context)
        // Clear enrollment data but keep allowUninstall=true so the service stays inactive
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_COMMUNITY_ID)
            .remove(KEY_COMMUNITY_NAME)
            .remove(KEY_POLICY)
            .apply()
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch uninstall: ${e.message}", e)
        }
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

    private fun applyAppSuspension(dpm: DevicePolicyManager, context: Context, allowedApps: List<String>, blockedApps: List<String>) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        try {
            // אם המנהל אסר אפליקציה באופן ספציפי - מקפיא (Suspend) אותה.
            // היא הופכת לאפורה ולא לחיצה, ואי אפשר להשתמש בה.
            if (blockedApps.isNotEmpty()) {
                val blockedArray = blockedApps.toTypedArray()
                dpm.setPackagesSuspended(admin, blockedArray, true)
            }
            if (allowedApps.isNotEmpty()) {
                val allowedArray = allowedApps.toTypedArray()
                dpm.setPackagesSuspended(admin, allowedArray, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed suspending apps", e)
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

    private fun applyTimeLock(dpm: DevicePolicyManager, context: Context, lockedUntilTs: Long?) {
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
                }.toTypedArray()

            // המכשיר נמצא בזמן נעילה על ידי המנהל (Time Lock)
            if (lockedUntilTs != null && lockedUntilTs > now) {
                dpm.setPackagesSuspended(admin, allPackages, true)
                Log.i(TAG, "Time Lock Enforced. All non-dialer apps suspended until $lockedUntilTs")
            } else {
                // הזמן פג או שאין נעילה - פתח חזרה הכל (אלא אם זה ברשימה השחורה, שיטופל אחר כך ב-applyAppSuspension)
                dpm.setPackagesSuspended(admin, allPackages, false)
                Log.i(TAG, "Time Lock Lifted")
            }
        } catch (e: Exception) {
             Log.e(TAG, "Failed TimeLock operation", e)
        }
    }
}



