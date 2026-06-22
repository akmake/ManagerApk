package com.sterni.tether.admin

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sterni.tether.R
import com.google.gson.JsonParser
import com.sterni.tether.data.api.HeartbeatRequest
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.*
import com.sterni.tether.data.model.WebFilterMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent foreground watchdog that:
 *  - Checks every 30 s that Accessibility, Device Admin, and VPN are active
 *  - Sends a heartbeat (with protection status) to the server every 5 min
 *  - Restarts itself via AlarmManager if Android kills it
 *  - Shows an actionable notification when a protection layer is degraded
 */
class TetherWatchdogService : Service() {

    companion object {
        private const val TAG = "TetherWatchdog"

        const val CHANNEL_ID = "tether_watchdog"
        private const val NOTIF_ID_PERSISTENT = 1001
        private const val NOTIF_ID_WARNING = 1002

        private const val CHECK_INTERVAL_MS = 30_000L           // 30 seconds
        private const val POLICY_SYNC_INTERVAL_MS = 60_000L   // 1 minute fast-sync
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L  // 5 minutes
        private const val UPDATE_CHECK_INTERVAL_MS = 30 * 60_000L // 30 minutes
        private const val RESTART_DELAY_MS = 8_000L            // restart after 8 s if killed
        private const val NOTIF_ID_ADMIN_MSG = 2001

        @Volatile var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, TetherWatchdogService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var lastHeartbeatAt = 0L
    private var lastPolicySyncAt = 0L
    private var lastUpdateCheckAt = 0L

    // ג”€ג”€ Lifecycle ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID_PERSISTENT, buildPersistentNotification())
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        Log.i(TAG, "Watchdog started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: Android re-creates this service after memory pressure
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        scheduleAlarmRestart()   // fallback in case START_STICKY is delayed
        Log.w(TAG, "Watchdog destroyed — scheduled alarm restart")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ג”€ג”€ Periodic check ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private val checkRunnable = object : Runnable {
        override fun run() {
            runChecks()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private fun runChecks() {
        TetherPolicyManager.cleanupExpiredTemporaryApprovals(this)

        val accessibilityOk = isAccessibilityEnabled()
        val adminOk = isDeviceAdminActive()
        val vpnNeeded = isVpnNeeded()
        val vpnOk = !vpnNeeded || TetherVpnService.isRunning

        // Uninstall window expiry — works fully offline, checked every 30 s
        val expiresAt = TetherPolicyManager.getUninstallExpiresAt(this)
        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            TetherPolicyManager.clearUninstallWindow(this)
            TetherPolicyManager.applyStoredPolicy(this)
            Log.i(TAG, "Uninstall window expired — protections restored")
        }

        // Time-lock expiry — device-level lock and per-app locks are epoch-based.
        // The server keeps returning the same lockedUntilTs value even after the time passes,
        // so the policy-change comparison won't trigger a re-apply. We check locally.
        val storedPolicy = TetherPolicyManager.loadPolicy(this)
        if (storedPolicy != null) {
            val now = System.currentTimeMillis()
            val deviceLockExpired = storedPolicy.lockedUntilTs != null &&
                    storedPolicy.lockedUntilTs in 1 until now
            val appLockExpired = storedPolicy.appTimeLocks.any { lock ->
                lock.lockedUntilTs != null && lock.lockedUntilTs in 1 until now
            }
            if (deviceLockExpired || appLockExpired) {
                TetherPolicyManager.applyStoredPolicy(this)
                Log.i(TAG, "Time lock expired — policy re-applied (deviceLock=$deviceLockExpired, appLock=$appLockExpired)")
            }
        }

        // Restart VPN if it died and is still needed
        if (vpnNeeded && !vpnOk) {
            try { startForegroundService(Intent(this, TetherVpnService::class.java)) } catch (_: Exception) {}
        }

        // Surface warnings for layers that require user action
        if (!accessibilityOk) showWarningNotification(
            "נדרשת פעולה — הגנת נגישות",
            "לחץ כדי להפעיל מחדש את שירות הנגישות של Tether",
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        )

        if (!adminOk && !TetherPolicyManager.isDeviceOwner(this)) showWarningNotification(
            "נדרשת פעולה — הרשאות מנהל",
            "Tether איבד הרשאות מנהל המכשיר. לחץ לשחזור.",
            Settings.ACTION_SECURITY_SETTINGS
        )

        val now = System.currentTimeMillis()

        // Fast policy sync every 60 seconds
        if (now - lastPolicySyncAt >= POLICY_SYNC_INTERVAL_MS) {
            lastPolicySyncAt = now
            serviceScope.launch { syncPolicy() }
        }

        // Heartbeat every 5 minutes
        if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat(accessibilityOk, adminOk, vpnOk && vpnNeeded)
            lastHeartbeatAt = now
        }

        // Silent update check every 30 minutes (Device Owner only)
        if (now - lastUpdateCheckAt >= UPDATE_CHECK_INTERVAL_MS) {
            lastUpdateCheckAt = now
            serviceScope.launch { TetherUpdater.checkAndUpdateAll(this@TetherWatchdogService) }
        }
    }

    // ג”€ג”€ Status checks ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName, ignoreCase = true)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, TetherDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    // VPN runs when there is something to filter — matches applyWebFilter logic.
    // Without this, the watchdog would restart a VPN that applyWebFilter intentionally stopped.
    private fun isVpnNeeded(): Boolean {
        if (!TetherPolicyManager.isEnrolled(this)) return false
        val policy = TetherPolicyManager.loadPolicy(this) ?: return true
        return policy.hideGooglePlay || policy.webFilterMode != WebFilterMode.NONE
    }

    // ג”€ג”€ Fast policy sync (every 60 s) ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private suspend fun syncPolicy() {
        if (!TetherPolicyManager.isEnrolled(this)) return
        val deviceId = TetherPolicyManager.getDeviceId(this) ?: return
        try {
            val response = RetrofitClient.create(TetherApiService::class.java).getPolicy(deviceId)
            if (!response.isSuccessful) {
                // 404 = admin deleted this device on the server. Confirmed self-release happens
                // inside onDeviceNotFound after several consecutive strikes (iron link).
                if (response.code() == 404) TetherPolicyManager.onDeviceNotFound(this)
                return
            }
            // Successful fetch = device still exists → reset the revoke-confirmation counter.
            TetherPolicyManager.clearRevokedStrikes(this)
            val body = response.body() ?: return

            val policy = body.policy
            val current = TetherPolicyManager.loadPolicy(this)
            if (current != policy) {
                TetherPolicyManager.applyPolicy(this, policy)
                Log.i(TAG, "Fast sync: policy updated")
            }

            for (command in body.pendingCommands) {
                when (command.type) {
                    "FORCE_SYNC"   -> TetherPolicyManager.applyPolicy(this, policy)
                    "RELEASE_ALL"  -> TetherPolicyManager.releaseAllAndUninstall(this, force = true)
                    "SHOW_MESSAGE" -> if (command.payload.isNotBlank()) showAdminMessage(command.payload)
                    "APPROVAL_GRANTED" -> applyApprovalGrant(command.payload)
                }
            }

            // allowUninstall=true from server starts a 1-hour local window (works offline too)
            if (body.allowUninstall && !TetherPolicyManager.isUninstallWindowActive(this)) {
                TetherPolicyManager.startUninstallWindow(this)
                TetherPolicyManager.applyPolicy(this, policy)
                TetherPolicyManager.showUninstallNotification(this)
                Log.i(TAG, "Uninstall window started — 1h from now")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast sync failed: ${e.message}")
        }
    }

    private fun showAdminMessage(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tether_admin_msg"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "הודעות מנהל", NotificationManager.IMPORTANCE_HIGH)
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("הודעה מהמנהל")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.app_logo)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_ADMIN_MSG, notif)
    }

    private fun applyApprovalGrant(payload: String) {
        runCatching {
            val obj = JsonParser.parseString(payload).asJsonObject
            val packageName = obj.get("packageName")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val expiresAt = obj.get("expiresAt")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            if (packageName.isNotBlank() && expiresAt > System.currentTimeMillis()) {
                TetherPolicyManager.grantTemporaryAppAccess(this, packageName, expiresAt)
                showAdminMessage("הגישה אושרה זמנית עבור $packageName")
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply APPROVAL_GRANTED command: ${it.message}")
        }
    }

    // ג”€ג”€ Heartbeat ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private fun sendHeartbeat(accessibilityOk: Boolean, adminOk: Boolean, vpnOk: Boolean) {
        val deviceId = TetherPolicyManager.getDeviceId(this) ?: return
        serviceScope.launch {
            try {
                RetrofitClient.create(TetherApiService::class.java)
                    .sendHeartbeat(deviceId, HeartbeatRequest(
                        accessibilityEnabled = accessibilityOk,
                        isDeviceAdmin = adminOk,
                        isDeviceOwner = TetherPolicyManager.isDeviceOwner(this@TetherWatchdogService),
                        vpnActive = vpnOk
                    ))
                Log.d(TAG, "Heartbeat sent — a11y=$accessibilityOk admin=$adminOk vpn=$vpnOk")
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat failed: ${e.message}")
            }
        }
    }

    // ג”€ג”€ Alarm-based restart fallback ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private fun scheduleAlarmRestart() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, TetherWatchdogService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAndAllowWhileIdle — no exact-alarm permission needed, fires within ~10 s in normal mode
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pi
        )
    }

    // ג”€ג”€ Notifications ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€

    private fun buildPersistentNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether מגן על המכשיר")
            .setContentText("הגנה פעילה")
            .setSmallIcon(R.drawable.app_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

    private fun showWarningNotification(title: String, body: String, settingsAction: String) {
        val intent = Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, settingsAction.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_WARNING, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tether Protection", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
            )
        }
    }
}
