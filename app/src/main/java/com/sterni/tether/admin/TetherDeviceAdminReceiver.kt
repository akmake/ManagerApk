package com.sterni.tether.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager
import android.util.Log
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.SecurityEventRequest
import com.sterni.tether.data.api.TetherApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TetherDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // חסימת כל אפליקציה חדשה שמותקנת אם היא לא ברשימה המותרת
        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, TetherDeviceAdminReceiver::class.java)
            
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val policy = TetherPolicyManager.loadPolicy(context)
                if (policy == null || !policy.allowedApps.contains(packageName)) {
                    try {
                        dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
                        dpm.setApplicationHidden(admin, packageName, true)
                        Log.w(TAG, "Blocked new installation immediately: $packageName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to block new app: $packageName", e)
                    }
                }
            }
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
        val deviceId = TetherPolicyManager.getDeviceId(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.create(TetherApiService::class.java)
                    .logSecurityEvent(deviceId, SecurityEventRequest("ADMIN_DEACTIVATE_ATTEMPT", null))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report admin deactivation: ${e.message}")
            }
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Profile provisioning complete — applying initial policy")
        TetherPolicyManager.applyStoredPolicy(context)
    }

    companion object {
        private const val TAG = "TetherAdmin"
    }
}
