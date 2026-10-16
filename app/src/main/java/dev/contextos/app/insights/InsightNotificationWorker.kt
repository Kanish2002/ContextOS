package dev.contextos.app.insights

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
import dev.contextos.app.MainActivity
import dev.contextos.app.R
import java.util.concurrent.TimeUnit

/**
 * Opt-in reminder for a suggestion explicitly marked helpful; never reads sensors,
 * location, app usage or runs the local LLM in background.
 */
class InsightNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = InsightPreferences(applicationContext)
        val now = System.currentTimeMillis()
        if (!prefs.canNotify(now)) return Result.success()
        val title = prefs.notificationCandidate(now) ?: return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val service = applicationContext.getSystemService(NotificationManager::class.java)
            service.createNotificationChannel(NotificationChannel(CHANNEL, "Private suggestions",
                NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 42, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Revisit a suggestion")
            .setContentText("You marked this helpful: " + title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "You previously marked this helpful: " + title +
                    ". This is a reminder, not a new sensor reading."))
            .setContentIntent(pending).setAutoCancel(true).build()
        return try {
            manager.notify(4102, notification)
            prefs.recordPosted(now)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
    companion object { const val CHANNEL = "contextos_suggestions_v1" }
}

object InsightNotificationScheduler {
    private const val WORK_NAME = "contextos_opt_in_suggestion_reminders"
    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManager batches delivery; hourly/exact alarm promises are intentionally avoided.
        val request = PeriodicWorkRequestBuilder<InsightNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
