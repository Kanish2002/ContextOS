package dev.contextos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.contextos.app.insights.InsightPreferences

@Composable
fun InsightSettingsCard(settings: InsightPreferences, updateNotifications: (Boolean) -> Unit) {
    settings.revision // observe writes
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = c.primary)
                Spacer(Modifier.width(10.dp))
                Text("Suggestion notifications", fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me about helpful suggestions")
                    Text("Off by default. Only reminders you explicitly rated helpful; no background sensor monitoring.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                Switch(checked = settings.notificationsEnabled, onCheckedChange = updateNotifications)
            }
            Text("Quiet hours · " + label(settings.quietStart) + " to " + label(settings.quietEnd),
                style = MaterialTheme.typography.titleSmall)
            Text("Quiet starts at:", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Slider(value = settings.quietStart.toFloat(), onValueChange = { settings.quietStart = it.toInt() },
                valueRange = 0f..23f, steps = 22)
            Text("Quiet ends at:", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Slider(value = settings.quietEnd.toFloat(), onValueChange = { settings.quietEnd = it.toInt() },
                valueRange = 0f..23f, steps = 22)
            Text("Daily notification limit · " + settings.dailyLimit, style = MaterialTheme.typography.titleSmall)
            Slider(value = settings.dailyLimit.toFloat(), onValueChange = { settings.dailyLimit = it.toInt() },
                valueRange = 0f..3f, steps = 2)
            OutlinedButton(onClick = settings::resetFeedback) {
                Text("Reset suggestion feedback")
            }
            Text("Also enforces a six-hour cooldown. Android controls actual delivery times; quiet-hour notifications are skipped, not deferred. Equal start/end means all-day quiet.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}
private fun label(hour: Int): String = "%02d:00".format(java.util.Locale.US, hour)
