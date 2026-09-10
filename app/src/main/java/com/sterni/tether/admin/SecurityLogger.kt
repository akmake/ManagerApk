package com.sterni.tether.admin

import android.content.Context
import com.sterni.tether.data.api.RetrofitClient
import com.sterni.tether.data.api.SecurityEventRequest
import com.sterni.tether.data.api.TetherApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Central, fire-and-forget audit logger for the admin activity log.
 *
 * Every protective / enforcement action across the whole app funnels through here — accessibility
 * blocks, VPN domain blocks, scheduled blocks, admin-tamper attempts, install events, self-limit
 * telemetry, etc. — so the manager's "לוג פעולות" reflects the entire app, not just one subsystem.
 *
 * A per-(type + target) throttle keeps high-frequency sources (repeated DNS queries, accessibility
 * content-changes) from flooding the log while still recording distinct incidents over time. Pass
 * `throttleMs = 0` for rare, always-report events (admin deactivation, a new install).
 */
object SecurityLogger {
    const val DEFAULT_THROTTLE_MS = 30_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastSent = ConcurrentHashMap<String, Long>()

    fun log(
        context: Context,
        type: String,
        target: String? = null,
        throttleMs: Long = DEFAULT_THROTTLE_MS
    ) {
        val deviceId = TetherPolicyManager.getDeviceId(context) ?: return
        if (throttleMs > 0L) {
            val key = "$type|${target.orEmpty()}"
            val now = System.currentTimeMillis()
            val prev = lastSent[key]
            if (prev != null && now - prev < throttleMs) return
            lastSent[key] = now
        }
        scope.launch {
            runCatching {
                RetrofitClient.create(TetherApiService::class.java)
                    .logSecurityEvent(deviceId, SecurityEventRequest(type, target))
            }
        }
    }
}
