package com.sterni.dailystudy.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.google.gson.Gson
import com.sterni.dailystudy.data.model.BlockedActionBehavior
import com.sterni.dailystudy.data.model.CommunityPolicy

import android.content.Intent
import com.sterni.dailystudy.data.model.WebFilterMode

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

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner — policy enforcement limited")
        }

        try {
            applyInstallRestriction(dpm, context, policy.blockInstallApps)
            applySafeBootRestriction(dpm, context, policy.blockSafeBoot)
            applyFactoryResetRestriction(dpm, context, policy.blockFactoryReset)
            applyUsbRestriction(dpm, context, policy.blockUsbTransfer)
            applyHiddenApps(dpm, context, policy.hideGooglePlay, policy.blockedApps)
            applyAntiBypassRestrictions(dpm, context, true)

            // Prevent uninstallation of Tether itself (requires Device Owner)
            val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
            dpm.setUninstallBlocked(admin, context.packageName, true)

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
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
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
        
        // List of common store and installer packages to block if hideGooglePlay is true
        val criticalPackages = listOf(
            "com.android.vending",                    // Google Play Store
            "com.sec.android.app.samsungapps",        // Samsung Galaxy Store
            "com.android.packageinstaller",           // System Package Installer
            "com.google.android.packageinstaller",    // Google Package Installer
            "com.amazon.venezia",                     // Amazon Appstore
            "com.huawei.appmarket",                   // Huawei AppGallery
            "com.oppo.market",                        // Oppo App Market
            "com.vivo.appstore"                       // Vivo App Store
        )

        criticalPackages.forEach { pkg ->
            try {
                // If hideGooglePlay is true, we hide all these installers/stores
                dpm.setApplicationHidden(admin, pkg, hideGooglePlay)
                Log.i(TAG, "Setting $pkg hidden=$hideGooglePlay")
            } catch (e: Exception) {
                // Not all devices have all these stores, so we log as warning
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

    fun applyWebFilter(context: Context, policy: CommunityPolicy) {
        if (policy.webFilterMode != WebFilterMode.NONE) {
            context.startForegroundService(Intent(context, TetherVpnService::class.java))
            context.startForegroundService(Intent(context, TetherOverlayService::class.java))
        } else {
            context.startService(Intent(context, TetherVpnService::class.java).apply {
                action = "STOP"
            })
        }
    }

    private fun applyAntiBypassRestrictions(dpm: DevicePolicyManager, context: Context, block: Boolean) {
        val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)

        if (block) {
            // Prevent creating a "guest" user that would bypass restrictions
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

            // Prevent enabling developer mode and ADB debugging
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // Critical! Prevents user from clearing app data or force-stopping the app in Settings
            dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
        }
    }
}
