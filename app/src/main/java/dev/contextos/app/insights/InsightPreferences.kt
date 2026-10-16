package dev.contextos.app.insights

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId

enum class InsightFeedback { NONE, HELPFUL, NOT_HELPFUL }

/** Device-private choices. No user sensor values are read or persisted here. */
class InsightPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("contextos_insight_preferences", Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications", false)
        set(v) { prefs.edit().putBoolean("notifications", v).apply(); revision++ }

    var quietStart: Int
        get() = prefs.getInt("quiet_start", 22)
        set(v) { require(v in 0..23); prefs.edit().putInt("quiet_start", v).apply(); revision++ }
    var quietEnd: Int
        get() = prefs.getInt("quiet_end", 8)
        set(v) { require(v in 0..23); prefs.edit().putInt("quiet_end", v).apply(); revision++ }
    var dailyLimit: Int
        get() = prefs.getInt("daily_limit", 1)
        set(v) { require(v in 0..3); prefs.edit().putInt("daily_limit", v).apply(); revision++ }

    fun feedback(id: String): InsightFeedback = runCatching {
        InsightFeedback.valueOf(prefs.getString("feedback_$id", "NONE")!!)
    }.getOrDefault(InsightFeedback.NONE)

    fun submitFeedback(id: String, feedback: InsightFeedback) {
        prefs.edit().putString("feedback_$id", feedback.name).apply()
        revision++
    }

    fun resetFeedback() {
        listOf("light", "focus", "move", "steps", "review", "check-in").forEach { id ->
            prefs.edit().remove("feedback_$id").apply()
        }
        prefs.edit().remove("last_insight_id").remove("last_insight_title")
            .remove("last_insight_at").apply()
        revision++
    }

    fun applyFeedback(input: List<DeviceInsight>): List<DeviceInsight> {
        revision // Observe preference changes inside Compose.
        return input.filter { feedback(it.id) != InsightFeedback.NOT_HELPFUL }
            .sortedWith(compareByDescending<DeviceInsight> {
                it.priority + if (feedback(it.id) == InsightFeedback.HELPFUL) 25 else 0
            }.thenBy { it.id }).take(3)
    }

    /**
     * A user-visible suggestion may be repeated later as a review prompt,
     * never as a claim about LIVE readings.
     */
    fun rememberSuggestion(insight: DeviceInsight, now: Long = System.currentTimeMillis()) {
        prefs.edit().putString("last_insight_id", insight.id)
            .putString("last_insight_title", insight.title.take(90))
            .putLong("last_insight_at", now).apply()
    }

    fun notificationCandidate(now: Long): String? {
        val at = prefs.getLong("last_insight_at", 0L)
        if (at <= 0 || now - at !in 0..604_800_000L) return null
        val id = prefs.getString("last_insight_id", null) ?: return null
        if (feedback(id) != InsightFeedback.HELPFUL) return null
        return prefs.getString("last_insight_title", null)
    }

    /** Only call after a notification was actually posted. */
    fun recordPosted(now: Long) {
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString()
        val currentDay = prefs.getString("notification_day", "")
        val count = if (currentDay == today) prefs.getInt("notification_count", 0) else 0
        prefs.edit().putString("notification_day", today)
            .putInt("notification_count", count + 1).putLong("last_posted_at", now).apply()
    }

    fun canNotify(now: Long): Boolean {
        if (!notificationsEnabled) return false
        val local = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val today = local.toLocalDate().toString()
        val used = if (prefs.getString("notification_day", "") == today) prefs.getInt("notification_count", 0) else 0
        val last = prefs.getLong("last_posted_at", 0L)
        return NotificationRules.canNotify(local.hour, quietStart, quietEnd, used,
            dailyLimit, if (last > 0) now - last else Long.MAX_VALUE)
    }
}

object NotificationRules {
    const val COOLDOWN_MS = 6L * 60 * 60 * 1000
    /** An identical quiet start/end means 24-hour quiet (safer than surprise alerts). */
    fun isQuiet(hour: Int, start: Int, end: Int): Boolean =
        if (start == end) true else if (start < end) hour in start until end
        else hour >= start || hour < end

    fun canNotify(hour: Int, quietStart: Int, quietEnd: Int, alreadySent: Int,
                  dailyLimit: Int, millisSinceLast: Long): Boolean =
        dailyLimit > 0 && alreadySent < dailyLimit &&
            millisSinceLast >= COOLDOWN_MS && !isQuiet(hour, quietStart, quietEnd)
}
