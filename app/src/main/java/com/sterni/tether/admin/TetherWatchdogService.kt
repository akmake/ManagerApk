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
import com.sterni.tether.data.api.HeartbeatRequest
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.TetherApiService
import com.sterni.tether.data.model.WebFilterMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

        private const val CHECK_INTERVAL_MS = 30_000L       // 30 seconds
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L // 5 minutes
        private const val RESTART_DELAY_MS = 8_000L          // restart after 8 s if killed

        @Volatile var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, TetherWatchdogService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastHeartbeatAt = 0L

    // ── Lifecycle ────────────────────────────────────────────────────────────

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
        handler.removeCallbacksAndMessages(null)
        scheduleAlarmRestart()   // fallback in case START_STICKY is delayed
        Log.w(TAG, "Watchdog destroyed — scheduled alarm restart")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Periodic check ───────────────────────────────────────────────────────

    private val checkRunnable = object : Runnable {
        override fun run() {
            runChecks()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private fun runChecks() {
        val accessibilityOk = isAccessibilityEnabled()
        val adminOk = isDeviceAdminActive()
        val vpnNeeded = isVpnNeeded()
        val vpnOk = !vpnNeeded || TetherVpnService.isRunning

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

        // Heartbeat every 5 minutes
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat(accessibilityOk, adminOk, vpnOk && vpnNeeded)
            lastHeartbeatAt = now
        }
    }

    // ── Status checks ────────────────────────────────────────────────────────

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

    private fun isVpnNeeded(): Boolean =
        TetherPolicyManager.loadPolicy(this)?.webFilterMode != WebFilterMode.NONE

    // ── Heartbeat ────────────────────────────────────────────────────────────

    private fun sendHeartbeat(accessibilityOk: Boolean, adminOk: Boolean, vpnOk: Boolean) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: return
        CoroutineScope(Dispatchers.IO).launch {
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

    // ── Alarm-based restart fallback ─────────────────────────────────────────

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

    // ── Notifications ────────────────────────────────────────────────────────

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
