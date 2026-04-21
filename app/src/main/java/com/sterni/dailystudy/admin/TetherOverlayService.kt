package com.sterni.dailystudy.admin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sterni.dailystudy.R
import com.sterni.dailystudy.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TetherOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "tether_overlay"
        const val NOTIF_ID = 9002
    }

    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager

    private val domainBlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val domain = intent.getStringExtra(TetherVpnService.EXTRA_DOMAIN) ?: return
            showOverlay(domain)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        LocalBroadcastManager.getInstance(this).registerReceiver(
            domainBlockedReceiver,
            IntentFilter(TetherVpnService.ACTION_DOMAIN_BLOCKED)
        )
    }

    private fun showOverlay(domain: String) {
        // Remove existing overlay if any
        dismissOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = buildOverlayView(domain)
        overlayView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            // SYSTEM_ALERT_WINDOW not granted
        }
    }

    private fun buildOverlayView(domain: String): View {
        val communityName = TetherPolicyManager.getCommunityName(this) ?: "Tether"
        val deviceId = TetherPolicyManager.getDeviceId(this)

        // Build view programmatically (no XML needed)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#CC1A237E"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val icon = TextView(this).apply {
            text = "🔒"
            textSize = 40f
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "גישה חסומה"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        val domainText = TextView(this).apply {
            text = domain
            textSize = 14f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "דומיין זה חסום על ידי $communityName"
            textSize = 13f
            setTextColor(Color.parseColor("#90CAF9"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }

        val requestBtn = Button(this).apply {
            text = "בקש גישה מהמנהל"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                if (deviceId != null) {
                    sendApprovalRequest(deviceId, domain)
                }
                dismissOverlay()
            }
        }

        val dismissBtn = Button(this).apply {
            text = "חזור"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#B0BEC5"))
            setOnClickListener { dismissOverlay() }
        }

        container.addView(icon)
        container.addView(title)
        container.addView(domainText)
        container.addView(subtitle)
        container.addView(requestBtn)
        container.addView(dismissBtn)

        return container
    }

    private fun sendApprovalRequest(deviceId: String, domain: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.tetherApi.requestApproval(
                    deviceId = deviceId,
                    action = "URL_ACCESS",
                    packageName = domain
                )
            } catch (e: Exception) {
                // Silently fail — user already dismissed
            }
        }
    }

    private fun dismissOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onDestroy() {
        dismissOverlay()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(domainBlockedReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Tether Overlay",
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether")
            .setContentText("הגנת אינטרנט פעילה")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
}
