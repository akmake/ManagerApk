package com.sterni.tether.admin

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.sterni.tether.data.model.AppUsageSession

/**
 * Per-device app-usage reporting: which app was in the foreground, when, and for how long.
 *
 * Built on [UsageStatsManager] rather than on the accessibility service, for three reasons:
 *  1. it keeps working when the accessibility service is off, so a device that stopped enforcing
 *     still reports what is happening on it — which is exactly when a manager needs to know;
 *  2. Android already retains ~7 days of history, so granting the permission answers "what has
 *     this device been running all week" immediately, with no waiting period;
 *  3. no polling and no background service — the OS collected this anyway, we just read it.
 *
 * The manager reads the result on the device page and can block any package straight from the list.
 *
 * Requires the "Usage access" special permission ([hasPermission]); a Device Owner cannot grant it
 * silently, so it is offered in the permissions centre like the other special accesses.
 */
object AppUsageTracker {

    private const val TAG = "TetherUsage"
    private const val PREFS = "tether_usage"
    private const val KEY_WATERMARK = "last_collected_ts"

    /** Ignore sub-second flickers — task switches and transient activities are not "usage". */
    private const val MIN_SESSION_MS = 1_000L

    /** Safety valve on a single upload (a week of backlog can be thousands of switches). */
    private const val MAX_SESSIONS_PER_UPLOAD = 500

    /** How far back to reach on the very first collection — the full window Android keeps. */
    private const val FIRST_RUN_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Packages that say nothing about behaviour and would drown the report: the launcher (visible
     * between every two apps), the system UI shell, and ourselves.
     */
    private val IGNORED_PACKAGES = setOf(
        "com.sterni.tether",
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.microsoft.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher2"
    )

    /** True when the "Usage access" special permission has been granted to us. */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Usage-access check failed: ${e.message}")
            false
        }
    }

    /**
     * Foreground sessions since the last successful collection.
     *
     * Returns them oldest-first. The watermark is NOT advanced here — call [commit] only after the
     * sessions have actually reached the server, so a failed upload is retried rather than lost.
     */
    fun collect(context: Context): List<AppUsageSession> {
        if (!hasPermission(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_WATERMARK, 0L)
            .takeIf { it > 0L } ?: (now - FIRST_RUN_LOOKBACK_MS)

        return try {
            buildSessions(context, usm, since, now)
        } catch (e: Exception) {
            Log.e(TAG, "Usage collection failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildSessions(
        context: Context,
        usm: UsageStatsManager,
        since: Long,
        now: Long
    ): List<AppUsageSession> {
        val events = usm.queryEvents(since, now)
        val event = UsageEvents.Event()
        val openedAt = HashMap<String, Long>()
        val sessions = mutableListOf<AppUsageSession>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg in IGNORED_PACKAGES) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[pkg] = event.timeStamp

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = openedAt.remove(pkg) ?: continue
                    val duration = event.timeStamp - start
                    if (duration < MIN_SESSION_MS) continue
                    sessions += AppUsageSession(
                        packageName = pkg,
                        appName = appLabel(context, pkg),
                        startTs = start,
                        endTs = event.timeStamp,
                        durationMs = duration
                    )
                }
            }
        }

        // An app still in the foreground has no closing event yet. Rewinding the watermark to where
        // it opened means the session is picked up whole on the next run, instead of being reported
        // as two halves or lost entirely.
        val stillOpenSince = openedAt.values.minOrNull()
        pendingWatermark = stillOpenSince ?: now

        sessions.sortBy { it.startTs }
        return if (sessions.size > MAX_SESSIONS_PER_UPLOAD) {
            // Keep the most recent — a backlog that large is a first run, and recent behaviour is
            // what the manager is looking at. The watermark still advances past the dropped ones.
            sessions.takeLast(MAX_SESSIONS_PER_UPLOAD)
        } else {
            sessions
        }
    }

    /** Watermark that [commit] will store — set by the last [collect]. */
    @Volatile private var pendingWatermark: Long = 0L

    /** Marks the last collected batch as delivered. Call only after a successful upload. */
    fun commit(context: Context) {
        val mark = pendingWatermark
        if (mark <= 0L) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_WATERMARK, mark)
            .apply()
    }

    private fun appLabel(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        null
    }
}
