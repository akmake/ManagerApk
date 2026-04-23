package com.sterni.tether.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log
import com.google.gson.Gson
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
            
            // טיפול באפליקציות - החרגות
            applyAppSuspension(dpm, context, policy.allowedApps, policy.blockedApps)
            
            applyHiddenApps(dpm, context, policy.hideGooglePlay, policy.blockedApps)
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
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            // חסימת ה-Package Installer עצמו - מונע הרצת קבצי APK
            dpm.setApplicationHidden(admin, "com.android.packageinstaller", true)
            dpm.setApplicationHidden(admin, "com.google.android.packageinstaller", true)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.setApplicationHidden(admin, "com.android.packageinstaller", false)
            dpm.setApplicationHidden(admin, "com.google.android.packageinstaller", false)
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
        additionalBlockedApps: List<String>
    ) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
        
        // רשימת חנויות ומתקינים קריטיים שחייבים להיחסם כדי למנוע התקנות
        val criticalPackages = listOf(
            "com.android.vending",                    // Google Play Store
            "com.sec.android.app.samsungapps",        // Samsung Galaxy Store
            "com.android.packageinstaller",           // System Package Installer
            "com.google.android.packageinstaller",    // Google Package Installer
            "com.miui.packageinstaller",              // MIUI Installer
            "com.coloros.packageinstaller",           // ColorOS Installer
            "com.android.settings.Settings\$StorageDashboardActivity" // מניעת התקנה דרך סייר הקבצים
        )

        criticalPackages.forEach { pkg ->
            try {
                // חסימה כפויה אם hideGooglePlay הוא true
                dpm.setApplicationHidden(admin, pkg, hideGooglePlay)
                // בנוסף, נבטל את האפליקציה (Disable) כדי להיות בטוחים
                dpm.setApplicationRestrictions(admin, pkg, null)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set hidden state for $pkg: ${e.message}")
            }
        }

        // Additional apps defined in the policy
        additionalBlockedApps.forEach { pkg ->
            try {
                dpm.setApplicationHidden(admin, pkg, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide additional app $pkg", e)
            }
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
                // קודם ננקה את כל ההגבלות הקריטיות כדי שלא נשאיר את המכשיר נעול
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.setUninstallBlocked(admin, context.packageName, false)

                // מסיר את סמכות ה-Device Owner
                dpm.clearDeviceOwnerApp(context.packageName)
                Log.i(TAG, "Device Owner privileges removed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove Device Owner: ${e.message}", e)
        }
    }

    fun applyWebFilter(context: Context, policy: CommunityPolicy) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        if (policy.webFilterMode != WebFilterMode.NONE) {
            context.startForegroundService(Intent(context, TetherVpnService::class.java))
            context.startForegroundService(Intent(context, TetherOverlayService::class.java))

            // הגדרה כ-Always-on VPN (דורש Device Owner)
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



