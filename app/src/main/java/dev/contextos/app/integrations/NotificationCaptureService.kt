package dev.contextos.app.integrations

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.LocalDate

/**
 * Special OS-granted Notification access. Only per-day COUNTS for one explicitly
 * allowlisted package are recorded; never notification content, OTP or chat messages.
 */
class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("contextos_notification_capture", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return
        val allowed = prefs.getString("package", "") ?: ""
        if (allowed.isBlank() || sbn.packageName != allowed) return
        val date = LocalDate.now().toString()
        val key = "count_" + date
        prefs.edit().putInt(key, prefs.getInt(key, 0).coerceAtMost(9999) + 1).apply()
    }
}
