package dev.contextos.app.integrations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.contextos.app.ui.AppearanceSettings
import dev.contextos.app.ui.ContextOsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

class IntegrationsActivity : ComponentActivity() {
    private val library: KnowledgeViewModel by viewModels()
    private val google by lazy { GoogleGateway(this) }
    private var requestedScope by mutableStateOf<GoogleScope?>(null)
    private var googlePreview by mutableStateOf<List<KnowledgeEntry>>(emptyList())
    private var calendarPreview by mutableStateOf<List<KnowledgeEntry>>(emptyList())
    private var notice by mutableStateOf("")
    private var recognizedVoice by mutableStateOf("")
    private var healthSummary by mutableStateOf("")
    private var cloudResult by mutableStateOf("")
    private var deadlineAlerts by mutableStateOf(false)
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        updateDeadlineAlerts(granted)
    }
    private fun updateDeadlineAlerts(enabled: Boolean) {
        deadlineAlerts = enabled
        getSharedPreferences("contextos_knowledge_choices", MODE_PRIVATE)
            .edit().putBoolean("agent_reminders", enabled).apply()
        AgentReminderScheduler.sync(this, enabled)
    }

    private val documents = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(library::importDocument)
    }
    private val photo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let(library::importPhoto)
    }
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDeviceCalendar() else notice = "Calendar permission denied."
    }
    private val healthPermission = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (DeviceBridge.stepPermission in granted) readHealth()
        else notice = "Health Connect permission denied."
    }
    private val googleResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) google.handleResolution(it.data, requestedScope,
            { message -> notice = message }, { loadGooglePreview() })
        else notice = "Google access not granted."
    }
    private val voiceRecognition = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            recognizedVoice = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (recognizedVoice.isNotBlank()) notice = "Voice text ready for review. Nothing was saved."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = AppearanceSettings(applicationContext)
        deadlineAlerts = getSharedPreferences("contextos_knowledge_choices", MODE_PRIVATE)
            .getBoolean("agent_reminders", false)
        AgentReminderScheduler.sync(this, deadlineAlerts)
        setContent {
            val state by library.state.collectAsStateWithLifecycle()
            ContextOsTheme(theme) {
                IntegrationsScreen(
                    state = state, notice = notice, googlePreview = googlePreview,
                    calendarPreview = calendarPreview, healthSummary = healthSummary,
                    voiceText = recognizedVoice, cloudResult = cloudResult,
                    search = library::updateSearch, save = library::add, remove = library::delete,
                    done = library::toggleDone, clear = library::clearAll,
                    savePreview = library::savePreview, clearMessage = library::clearMessage,
                    shareChat = library::updateChatConsent, setAgent = { enabled ->
                        if (!enabled) updateDeadlineAlerts(false)
                        library.updateAgentConsent(enabled)
                    },
                    chooseDocuments = { documents.launch(arrayOf("application/pdf", "text/*",
                        "application/json", "text/calendar", "text/csv", "application/octet-stream")) },
                    choosePhoto = { photo.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    calendar = ::requestDeviceCalendar,
                    health = ::requestHealth,
                    notifications = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    google = { scope -> requestedScope = scope
                        google.authorize(scope, { launch -> googleResolution.launch(launch) },
                            { message -> notice = message }, { loadGooglePreview() }) },
                    disconnectGoogle = { google.disconnect(); googlePreview = emptyList() },
                    agentReminders = deadlineAlerts,
                    toggleReminders = { value ->
                        if (!value) updateDeadlineAlerts(false)
                        else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else updateDeadlineAlerts(true)
                    },
                    voice = ::startVoice,
                    askOffline = { spoken ->
                        startActivity(Intent(this, dev.contextos.app.MainActivity::class.java)
                            .putExtra("voice_prompt", spoken))
                    },
                    route = ::openRoute,
                    remote = { endpoint, key, model, question ->
                        lifecycleScope.launch {
                            try { cloudResult = "Requesting consented remote completion..."
                                cloudResult = CloudFallback.ask(endpoint, key, model, question) }
                            catch (e: Exception) { cloudResult = "Cloud request failed: " +
                                (e.message ?: "Unknown error") }
                        }
                    }
                )
            }
        }
    }

    private fun requestDeviceCalendar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED) loadDeviceCalendar()
        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    private fun loadDeviceCalendar() {
        lifecycleScope.launch {
            try { calendarPreview = withContext(Dispatchers.IO) {
                DeviceBridge.localCalendarPreview(this@IntegrationsActivity) }
                notice = "Loaded " + calendarPreview.size + " upcoming local calendar entries. Save individually." }
            catch (e: Exception) { notice = "Calendar unavailable: " + e.message }
        }
    }

    private fun requestHealth() {
        if (androidx.health.connect.client.HealthConnectClient.getSdkStatus(this)
            != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
            notice = "Health Connect is unavailable; install or update it on this phone."
            return
        }
        healthPermission.launch(setOf(DeviceBridge.stepPermission))
    }
    private fun readHealth() {
        lifecycleScope.launch {
            try { healthSummary = "Health Connect · 7-day total steps: " +
                DeviceBridge.healthStepsLastWeek(this@IntegrationsActivity).toString() +
                ". Displayed on demand; not saved."
            } catch (e: Exception) { notice = "Health Connect: " + e.message }
        }
    }
    private fun loadGooglePreview() {
        lifecycleScope.launch {
            try {
                googlePreview = google.listPreview()
                notice = "Loaded " + googlePreview.size +
                    " user-authorized previews. Nothing is archived until you tap Save."
            } catch (e: Exception) { notice = "Google API access: " + (e.message ?: "Failed") }
        }
    }
    private fun startVoice() {
        try {
            voiceRecognition.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate a note (offline preferred, not guaranteed)")
            })
        } catch(e: Exception) { notice = "No speech recognition app is installed." }
    }
    private fun openRoute(destination: String) {
        if (destination.isBlank()) { notice = "Enter the destination first.";return }
        val url = "https://www.google.com/maps/dir/?api=1&destination=" +
            URLEncoder.encode(destination.take(150), "UTF-8")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun IntegrationCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun IntegrationsScreen(
    state: KnowledgeState, notice: String, googlePreview: List<KnowledgeEntry>,
    calendarPreview: List<KnowledgeEntry>, healthSummary: String, voiceText: String,
    cloudResult: String, search: (String) -> Unit,
    save: (String, String, String, String?) -> Unit,
    remove: (String) -> Unit, done: (String) -> Unit, clear: () -> Unit,
    savePreview: (KnowledgeEntry) -> Unit, clearMessage: () -> Unit,
    shareChat: (Boolean) -> Unit, setAgent: (Boolean) -> Unit,
    chooseDocuments: () -> Unit, choosePhoto: () -> Unit,
    calendar: () -> Unit, health: () -> Unit, notifications: () -> Unit,
    google: (GoogleScope) -> Unit, disconnectGoogle: () -> Unit,
    agentReminders: Boolean, toggleReminders: (Boolean) -> Unit,
    voice: () -> Unit, askOffline: (String) -> Unit,
    route: (String) -> Unit,
    remote: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("NOTE") }
    var due by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var notificationPackage by remember { mutableStateOf("") }
    var captureNotifications by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var cloudPrompt by remember { mutableStateOf("") }
    var confirmCloud by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val app = androidx.compose.ui.platform.LocalContext.current
    val capture = remember { app.getSharedPreferences("contextos_notification_capture", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        notificationPackage = capture.getString("package", "").orEmpty()
        captureNotifications = capture.getBoolean("enabled", false)
    }
    LaunchedEffect(voiceText) { if (voiceText.isNotBlank()) detail = voiceText }
    if (confirmCloud) AlertDialog(onDismissRequest = { confirmCloud = false },
        title = { Text("Send this question to a remote provider?") },
        text = { Text("The question will leave your phone via the HTTPS endpoint you provided. No archive or other chat context is attached. The provider may retain it.") },
        confirmButton = { TextButton(onClick = { confirmCloud = false
            remote(endpoint, apiKey, model, cloudPrompt) }) { Text("Send once") } },
        dismissButton = { TextButton(onClick = { confirmCloud = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Erase encrypted knowledge?") },
        text = { Text("All manually saved documents, plans and Timeline entries will be deleted.") },
        confirmButton = { TextButton(onClick = { clear(); confirmDelete = false }) { Text("Erase") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("ContextOS · Integrations & Agent", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Opt-in sources · Encrypted archive · No silent import",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(notice.ifBlank { state.error ?: state.message },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null) TextButton(onClick = clearMessage) { Text("Dismiss error") }

        IntegrationCard("1 · Your private knowledge",
            "Search selected notes, documents and conversations locally. Ask about last Tuesday or unfinished projects.") {
            OutlinedTextField(value = state.query, onValueChange = search,
                label = { Text("Search offline knowledge") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Which projects haven't I completed?") }, singleLine = true)
            val shown = if (state.query.isBlank()) state.entries.takeLast(8).asReversed() else state.results
            shown.take(12).forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Text(entry.category + " · " + entry.source +
                        if (entry.done) " · Completed" else "",
                        style = MaterialTheme.typography.labelSmall)
                    Text(entry.body.take(280), style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (entry.category in setOf("PROJECT","LEARNING","CAREER")) TextButton(onClick = {
                            done(entry.id) }) { Text(if (entry.done) "Mark pending" else "Mark complete") }
                        TextButton(onClick = { remove(entry.id) }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include matching knowledge in local AI chat", Modifier.weight(1f))
                Switch(checked = state.includeInChat, onCheckedChange = shareChat)
            }
            TextButton(onClick = { confirmDelete = true }, enabled = state.entries.isNotEmpty()) {
                Text("Delete all imported knowledge")
            }
        }

        IntegrationCard("2 · Save goals, plans and conversations",
            "User-authored entries only. Deadline and completion status drive the career and proactive suggestions.") {
            OutlinedTextField(value = title, onValueChange = { title = it.take(160) },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = detail, onValueChange = { detail = it.take(16000) },
                label = { Text("Details or dictated note") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NOTE","PROJECT","CAREER","LEARNING").forEach { tag ->
                    FilterChip(selected = tag == category, onClick = { category = tag },
                        label = { Text(tag.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            OutlinedTextField(value = due, onValueChange = { due = it.take(10) },
                label = { Text("Optional deadline YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                save(title, detail, category, due)
                title = ""; detail = ""; due = ""
            }, enabled = title.isNotBlank() && detail.isNotBlank() && !state.busy) { Text("Save encrypted") }
            OutlinedButton(onClick = voice) { Text("Dictate note (speech service)") }
            if (voiceText.isNotBlank()) OutlinedButton(onClick = { askOffline(voiceText) }) {
                Text("Ask offline AI about dictated text")
            }
            Text("Android speech services may use the network despite offline preference. Review text before saving.",
                style = MaterialTheme.typography.bodySmall)
        }

        IntegrationCard("3 · Files, PDFs, photos & Maps Timeline",
            "Import only selected items via Android document/photo pickers. Photo text recognition is on-device.") {
            OutlinedButton(onClick = chooseDocuments) { Text("Import document or Timeline export") }
            OutlinedButton(onClick = choosePhoto) { Text("Choose photo and recognize text") }
            Text("Supports PDF text, TXT, Markdown, CSV, JSON, HTML and calendar text. Scanned PDFs without a text layer require photo OCR. Timeline JSON parsing supports several known export layouts.",
                style = MaterialTheme.typography.bodySmall)
        }

        IntegrationCard("4 · Gmail, Google Drive and Google Calendar",
            "Separate Google consent per source. Gmail/Drive read-only scopes may require OAuth verification and Google Cloud configuration. Previews are NOT saved automatically.") {
            GoogleScope.entries.forEach { scope ->
                OutlinedButton(onClick = { google(scope) }) { Text("Authorize " + scope.name.lowercase()) }
            }
            TextButton(onClick = disconnectGoogle) { Text("Forget temporary Google access token") }
            googlePreview.take(20).forEach { entry ->
                Text(entry.title, fontWeight = FontWeight.Medium)
                Text(entry.body.take(220), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { savePreview(entry) }, enabled = !state.busy) { Text("Save this preview locally") }
            }
            Text("The Drive preview indexes metadata only; it does not download private Drive document contents.",
                style = MaterialTheme.typography.bodySmall)
        }

        IntegrationCard("5 · Device calendar and Health Connect",
            "Android READ_CALENDAR and Health Connect READ_STEPS are separate permissions.") {
            OutlinedButton(onClick = calendar) { Text("Read upcoming device calendar events") }
            calendarPreview.take(20).forEach { entry ->
                Text(entry.title, fontWeight = FontWeight.Medium)
                Text(entry.body.take(200), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { savePreview(entry) }) { Text("Save event") }
            }
            OutlinedButton(onClick = health) { Text("Authorize Health Connect steps") }
            if (healthSummary.isNotBlank()) Text(healthSummary)
            Text("Health data is displayed on request, not added to personal memories.", style = MaterialTheme.typography.bodySmall)
        }

        IntegrationCard("6 · Other app notifications and WhatsApp boundaries",
            "Android offers special Notification Access, not other apps' private databases. This listener stores only an allowlisted app's daily notification COUNTS—not content or OTPs.") {
            OutlinedTextField(value = notificationPackage,
                onValueChange = { notificationPackage = it.take(160) },
                label = { Text("Exact allowed app package") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. com.whatsapp") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capture metadata counts", Modifier.weight(1f))
                Switch(checked = captureNotifications, onCheckedChange = {
                    captureNotifications = it
                    capture.edit().putBoolean("enabled", it).putString("package", notificationPackage).apply()
                })
            }
            Button(onClick = {
                capture.edit().putString("package", notificationPackage).apply()
                notifications()
            }) { Text("Open Android Notification Access settings") }
            Text("Today's allowed-app notification count: " +
                capture.getInt("count_" + java.time.LocalDate.now(), 0))
        }

        IntegrationCard("7 · Smart commute",
            "Historical Timeline segments provide typical durations; Google Maps may show live routes when opened explicitly.") {
            val minutes = state.entries.mapNotNull { it.durationMinutes }.filter { it in 1..240 }.sorted()
            Text(if (minutes.size >= 3) "Median imported trip: " +
                minutes[minutes.size / 2] + " min (not a live route estimate)"
                else "Import at least three Timeline travel segments for an offline duration estimate.")
            OutlinedTextField(value = destination, onValueChange = { destination = it },
                label = { Text("Destination for external Maps directions") },
                modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { route(destination) }) { Text("Open Google Maps directions") }
        }

        IntegrationCard("8 · Career & learning companion",
            "Uses only your saved project, interview and learning entries or approved calendar/email previews.") {
            val items = state.entries.filter { it.category in setOf("PROJECT","CAREER","LEARNING") }
            Text("Pending: " + items.count { !it.done } + " · Completed: " + items.count { it.done })
            AgentPlanner.propose(items).take(3).forEach {
                Text(it.title, fontWeight = FontWeight.SemiBold)
                Text(it.reason, style = MaterialTheme.typography.bodySmall)
            }
        }

        IntegrationCard("9 · Proactive personal agent",
            "Explicit opt-in. It ranks what matters from your saved goals, deadlines and historical trips, not secretly collected data.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable local agent suggestions", Modifier.weight(1f))
                Switch(checked = state.agentEnabled, onCheckedChange = setAgent)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opt-in deadline notifications", Modifier.weight(1f))
                Switch(checked = agentReminders, enabled = state.agentEnabled,
                    onCheckedChange = toggleReminders)
            }
            Text("Uses saved deadlines only. Checks about twice daily; quiet hours and daily limits from Settings still apply.",
                style = MaterialTheme.typography.bodySmall)
            if (state.agentEnabled) AgentPlanner.propose(state.entries).forEach {
                Text(it.title, fontWeight = FontWeight.Medium)
                Text(it.reason, style = MaterialTheme.typography.bodySmall)
            }
            Text("No automated external action, unapproved email reply or guaranteed background alert.",
                style = MaterialTheme.typography.bodySmall)
        }

        IntegrationCard("10 · Optional voice and explicit cloud fallback",
            "Local LiteRT-LM remains the default. Remote model calls are separate, one-time actions requiring confirmation.") {
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it },
                label = { Text("HTTPS chat completions endpoint") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it },
                label = { Text("Provider model ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API key (memory-only, not saved)") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(value = cloudPrompt, onValueChange = { cloudPrompt = it.take(2000) },
                label = { Text("Question to send explicitly") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { confirmCloud = true },
                enabled = endpoint.startsWith("https://") && model.isNotBlank() && cloudPrompt.isNotBlank()) {
                Text("Ask cloud model once")
            }
            if (cloudResult.isNotBlank()) Text(cloudResult.take(3000), style = MaterialTheme.typography.bodySmall)
        }
        Text("ContextOS integrations beta · You control each source; third-party verification and device capabilities still apply.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
