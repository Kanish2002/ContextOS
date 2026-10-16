package dev.contextos.app.insights

/**
 * Explicitly triggered, local-only suggestions. Inputs are coarse device estimates; never
 * infer health, emotion, identity, travel mode or behavior from them.
 * No permissions, analytics, network calls or background execution are required.
 */
data class InsightInput(
    val enabled: Boolean,
    val sensing: Boolean = false,
    val ambientLux: Float? = null,
    val motionLabel: String = "Unavailable",
    val stepsSinceOpen: Int? = null,
    val appUseMinutes: Long? = null,
    val hasSavedSnapshot: Boolean = false
)

data class DeviceInsight(
    val id: String,
    val title: String,
    val detail: String,
    val basedOn: String,
    val priority: Int
)

object InsightEngine {
    fun recommendations(input: InsightInput): List<DeviceInsight> {
        if (!input.enabled) return emptyList()
        val suggestions = mutableListOf<DeviceInsight>()

        val light = input.ambientLux
        if (input.sensing && light != null && light >= 0f && light < 80f) {
            suggestions += DeviceInsight(
                "light", "Consider a brighter workspace",
                "The recent foreground light reading was low. If you are reading, a brighter spot may be more comfortable.",
                "Recent foreground light reading · " + light.toInt() + " lux", 90
            )
        }

        val minutes = input.appUseMinutes
        if (minutes != null && minutes >= 180L) {
            suggestions += DeviceInsight(
                "focus", "Plan one screen-free pause",
                "Android reports considerable foreground usage over the past 24 hours. A short break may be worth scheduling.",
                "Approximate 24-hour app usage · " + minutes + " min", 75
            )
        }

        if (input.sensing && input.motionLabel == "Low motion" && minutes != null && minutes >= 120L) {
            suggestions += DeviceInsight(
                "move", "Try a brief movement break",
                "The latest foreground reading indicates low phone motion and the daily usage estimate is elevated. This does not establish how long you have been sitting.",
                "Recent foreground motion + 24-hour app usage", 70
            )
        }

        val steps = input.stepsSinceOpen
        if (steps != null && steps >= 1000) {
            suggestions += DeviceInsight(
                "steps", "You have a session milestone",
                "The step counter has recorded at least 1,000 steps since this app session's first reading.",
                "Step counter · session only", 45
            )
        }

        if (input.hasSavedSnapshot) {
            suggestions += DeviceInsight(
                "review", "Review your saved context",
                "A previous snapshot is available on your device. Compare it with the current readings if you want to.",
                "Saved local snapshots", 30
            )
        }

        if (suggestions.isEmpty()) {
            suggestions += DeviceInsight(
                "check-in", "Start with a simple check-in",
                "Enable a foreground sensor or explicitly refresh another source in Context to see grounded suggestions.",
                "No sufficient live signals yet", 10
            )
        }

        return suggestions.sortedWith(
            compareByDescending<DeviceInsight> { it.priority }.thenBy { it.id }
        ).take(3)
    }
}
