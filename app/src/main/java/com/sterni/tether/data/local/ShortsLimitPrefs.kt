package com.sterni.tether.data.local

import android.content.Context
import java.time.LocalDate

/**
 * Local, self-imposed daily time budget for YouTube Shorts.
 *
 * Stored on-device only and works independently of the community policy — every user (and the
 * manager, for themselves) can cap how long they scroll Shorts each day. Regular YouTube videos
 * are never counted or blocked; only the Shorts ("reel") player accrues time.
 *
 * Two knobs make up today's budget:
 *  - the permanent [dailyLimitMinutes] (persists day to day), and
 *  - a one-time [bonusMinutesToday] top-up the user can add from inside the app once the base
 *    budget is spent — capped at [MAX_BONUS_MIN] per day and reset every night.
 *
 * All day-scoped counters auto-reset when the calendar day rolls over.
 */
object ShortsLimitPrefs {
    private const val PREFS             = "shorts_limit_prefs"
    private const val KEY_ENABLED       = "enabled"
    private const val KEY_LIMIT_MIN     = "daily_limit_minutes"
    private const val KEY_USAGE_DATE    = "usage_date"       // yyyy-MM-dd of the accrued total
    private const val KEY_USAGE_SECONDS = "usage_seconds"
    private const val KEY_BONUS_DATE    = "bonus_date"       // yyyy-MM-dd of today's bonus
    private const val KEY_BONUS_MINUTES = "bonus_minutes"
    private const val KEY_WARNED_DATE   = "warned_date"      // yyyy-MM-dd the 5-min warning was shown

    const val DEFAULT_LIMIT_MIN = 20
    const val MIN_LIMIT_MIN     = 1
    const val MAX_LIMIT_MIN     = 600
    const val MAX_BONUS_MIN     = 30    // most a user may top up in a single day
    const val BONUS_STEP_MIN    = 10    // one tap adds this many minutes

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().toString()

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun dailyLimitMinutes(context: Context): Int =
        prefs(context).getInt(KEY_LIMIT_MIN, DEFAULT_LIMIT_MIN)

    fun setDailyLimitMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_LIMIT_MIN, minutes.coerceIn(MIN_LIMIT_MIN, MAX_LIMIT_MIN))
            .apply()
    }

    /** Minutes of one-time top-up added today (0 once the day has rolled over). */
    fun bonusMinutesToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString(KEY_BONUS_DATE, null) == today()) p.getInt(KEY_BONUS_MINUTES, 0) else 0
    }

    /** How much more top-up the user may still add today. */
    fun remainingBonusMinutes(context: Context): Int =
        (MAX_BONUS_MIN - bonusMinutesToday(context)).coerceAtLeast(0)

    /**
     * Adds a one-time top-up for today, clamped so the day's total bonus never exceeds
     * [MAX_BONUS_MIN]. Returns the new bonus total for today.
     */
    fun addBonusMinutes(context: Context, minutes: Int): Int {
        val today = today()
        val current = bonusMinutesToday(context)
        val next = (current + minutes).coerceIn(0, MAX_BONUS_MIN)
        prefs(context).edit()
            .putString(KEY_BONUS_DATE, today)
            .putInt(KEY_BONUS_MINUTES, next)
            .apply()
        return next
    }

    /** Today's effective budget = permanent daily limit + any one-time top-up. */
    fun effectiveLimitMinutes(context: Context): Int =
        dailyLimitMinutes(context) + bonusMinutesToday(context)

    /** Shorts seconds watched today. Returns 0 once the day has rolled over. */
    fun usedSecondsToday(context: Context): Long {
        val p = prefs(context)
        return if (p.getString(KEY_USAGE_DATE, null) == today())
            p.getLong(KEY_USAGE_SECONDS, 0L)
        else 0L
    }

    /** Seconds of Shorts still allowed today (0 when the budget is spent). */
    fun remainingSeconds(context: Context): Long =
        (effectiveLimitMinutes(context) * 60L - usedSecondsToday(context)).coerceAtLeast(0L)

    /** Adds watched seconds to today's tally (rolling the counter to a new day if needed). */
    fun addSeconds(context: Context, seconds: Long): Long {
        if (seconds <= 0L) return usedSecondsToday(context)
        val p = prefs(context)
        val today = today()
        val base = if (p.getString(KEY_USAGE_DATE, null) == today) p.getLong(KEY_USAGE_SECONDS, 0L) else 0L
        val total = base + seconds
        p.edit()
            .putString(KEY_USAGE_DATE, today)
            .putLong(KEY_USAGE_SECONDS, total)
            .apply()
        return total
    }

    /** True when the feature is on and today's Shorts time has reached the effective budget. */
    fun isOverLimit(context: Context): Boolean {
        if (!isEnabled(context)) return false
        return usedSecondsToday(context) >= effectiveLimitMinutes(context) * 60L
    }

    /** One-shot per day: has the "5 minutes left" warning already been shown today? */
    fun wasWarnedToday(context: Context): Boolean =
        prefs(context).getString(KEY_WARNED_DATE, null) == today()

    fun markWarnedToday(context: Context) {
        prefs(context).edit().putString(KEY_WARNED_DATE, today()).apply()
    }
}
