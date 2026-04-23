package com.sterni.tether.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootPolicyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        // Re-apply stored policy (Device Owner path) or start layered services (non-DO path)
        TetherPolicyManager.applyStoredPolicy(context)

        // Always ensure the watchdog is running after boot
        try {
            context.startForegroundService(Intent(context, TetherWatchdogService::class.java))
        } catch (_: Exception) {}
    }
}
