package dev.contextos.app.integrations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Rationale displayed from Health Connect privacy-permission information. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("ContextOS · Health permission", style = MaterialTheme.typography.headlineMedium)
                    Text("ContextOS only requests read access to step counts. You choose when to view a seven-day summary in the Integrations screen.")
                    Text("Step records are not uploaded, added to personal memory or used for medical inferences. Revoking permission in Health Connect stops future reads.")
                    Text("No background Health Connect collection is enabled.")
                    Button(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}
