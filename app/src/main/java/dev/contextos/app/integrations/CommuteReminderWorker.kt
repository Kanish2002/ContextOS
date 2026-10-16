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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import dev.contextos.app.insights.InsightPreferences
import dev.contextos.app.insights.NotificationRules
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Inexact, opt-in one-shot leave-by reminder based on a manually approved route.
 * WorkManager stores only an opaque archive UUID. The location stays AES-GCM encrypted.
 * No background Google Maps call, API key, or continuous location tracking.
 */
class CommuteReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val id = inputData.getString("entry_id") ?: return Result.success()
        val prefs = context.getSharedPreferences("contextos_knowledge_choices", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("agent", false) || !prefs.getBoolean("agent_reminders", false))
            return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return Result.success()
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return Result.success()
        val due = runCatching {
            KnowledgeVault(context).read().firstOrNull {
                it.id == id && it.category == "COMMUTE_PLAN" && !it.done
            }
        }.getOrNull() ?: return Result.success()
        val now = System.currentTimeMillis()
        val expected = due.dueAt ?: return Result.success()
        if (now < expected - 30 * 60_000L || now > expected + 60 * 60_000L)
            return Result.success()
        val options = InsightPreferences(context)
        val today = LocalDate.now()
        val sent = prefs.getInt("sent_$today", 0)
        val sinceLast = now - prefs.getLong("last_alert", 0L)
        if (!NotificationRules.canNotify(LocalTime.now().hour, options.quietStart,
                options.quietEnd, sent, options.dailyLimit, sinceLast)) return Result.success()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("contextos_commute_reminders", "Saved commute departure reminders",
                NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 62,
            Intent(context, IntegrationsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "contextos_commute_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to leave for your saved commute")
            .setContentText("Review your planned route. Traffic estimates may have changed.")
            .setContentIntent(open).setAutoCancel(true).build()
        return try {
            notifications.notify(4250, notification)
            prefs.edit().putInt("sent_$today", sent + 1)
                .putLong("last_alert", now).apply()
            Result.success()
        } catch (_: SecurityException) { Result.success() }
    }
}

object CommuteReminderScheduler {
    private const val MAX_HORIZON_MS = 14L * 24 * 60 * 60 * 1000

    fun waitMillis(now: Long, departure: Long): Long {
        require(departure > now && departure - now <= MAX_HORIZON_MS) {
            "Departure must be within the next 14 days."
        }
        return (departure - now - 5 * 60_000L).coerceAtLeast(0L)
    }

    fun schedule(context: Context, entryId: String, departAtMs: Long) {
        require(entryId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val wait = waitMillis(System.currentTimeMillis(), departAtMs)
        val request = OneTimeWorkRequestBuilder<CommuteReminderWorker>()
            .setInputData(Data.Builder().putString("entry_id", entryId).build())
            .setInitialDelay(wait, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "contextos_leave_by_$entryId", ExistingWorkPolicy.REPLACE, request)
    }
}
