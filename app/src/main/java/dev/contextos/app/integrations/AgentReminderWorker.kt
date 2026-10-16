package dev.contextos.app.integrations

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import dev.contextos.app.insights.InsightPreferences
import dev.contextos.app.insights.NotificationRules
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Local opt-in deadline watcher: never syncs Gmail, calendar, health or messages
 * in background. Reads only user-approved encrypted entries. OS/quiet-hours guard.
 */
class AgentReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("contextos_knowledge_choices", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("agent_reminders", false) || !prefs.getBoolean("agent", false))
            return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return Result.success()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
        val insights = InsightPreferences(context)
        val hour = java.time.LocalTime.now().hour
        val today = LocalDate.now()
        val sent = prefs.getInt("sent_" + today, 0)
        val since = System.currentTimeMillis() - prefs.getLong("last_alert", 0L)
        if (!NotificationRules.canNotify(hour, insights.quietStart, insights.quietEnd, sent,
                insights.dailyLimit, since)) return Result.success()
        val entries = runCatching { KnowledgeVault(context).read() }.getOrElse {
            return Result.success() // Vault unavailable; never overwrite it.
        }
        val candidate = entries.filter { !it.done && it.dueAt != null }.mapNotNull { item ->
            val date = Instant.ofEpochMilli(item.dueAt!!).atZone(ZoneId.systemDefault()).toLocalDate()
            val days = ChronoUnit.DAYS.between(today, date)
            if (days in 0L..1L && prefs.getString("reminded_" + item.id, "") != today.toString())
                item to days else null
        }.minByOrNull { it.second }?.first ?: return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("contextos_agent_deadlines", "Personal deadline reminders",
                    NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = Intent(context, dev.contextos.app.MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 49, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "contextos_agent_deadlines")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Saved deadline approaching")
            .setContentText(candidate.title.take(100))
            .setContentIntent(pending).setAutoCancel(true).build()
        return try {
            NotificationManagerCompat.from(context).notify(4220, notification)
            prefs.edit().putString("reminded_" + candidate.id, today.toString())
                .putInt("sent_" + today, sent + 1)
                .putLong("last_alert", System.currentTimeMillis()).apply()
            Result.success()
        } catch (_: SecurityException) { Result.success() }
    }
}

object AgentReminderScheduler {
    private const val WORK = "contextos_user_approved_deadline_watch"
    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) wm.cancelUniqueWork(WORK)
        else wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AgentReminderWorker>(12, TimeUnit.HOURS).build())
    }
}
