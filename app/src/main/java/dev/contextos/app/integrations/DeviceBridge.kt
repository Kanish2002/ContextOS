package dev.contextos.app.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.LocalDate

object DeviceBridge {
    val stepPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    fun localCalendarPreview(context: Context): List<KnowledgeEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) return emptyList()
        val from = System.currentTimeMillis()
        val to = from + 30L * 86400000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from)
            android.content.ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION)
        val data = mutableListOf<KnowledgeEntry>()
        context.contentResolver.query(uri, columns, null, null, CalendarContract.Instances.BEGIN + " ASC")
            ?.use { cursor ->
                while (cursor.moveToNext() && data.size < 30) {
                    val title = cursor.getString(0) ?: "Calendar event"
                    val time = cursor.getLong(1)
                    val place = cursor.getString(2) ?: ""
                    val details = cursor.getString(3) ?: ""
                    data += KnowledgeEntry(title = title.take(150),
                        body = ("Upcoming calendar event: " +
                            Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()) +
                            ". Location: " + place + ". " + details).take(6000),
                        category = "CAREER", source = "Selected Android calendar event",
                        dueAt = time, location = place.take(200))
                }
            }
        return data
    }

    suspend fun healthStepsLastWeek(context: Context): Long {
        require(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            "Health Connect not installed or unavailable on this device."
        }
        val client = HealthConnectClient.getOrCreate(context)
        require(stepPermission in client.permissionController.getGrantedPermissions()) {
            "Grant the Steps read permission in Health Connect first."
        }
        val now = Instant.now()
        val result = client.aggregate(AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now)
        ))
        return result[StepsRecord.COUNT_TOTAL] ?: 0
    }
}
