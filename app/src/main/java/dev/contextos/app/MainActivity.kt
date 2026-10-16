package dev.contextos.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import dev.contextos.app.memory.MemoryKind
import dev.contextos.app.integrations.KnowledgeViewModel
import dev.contextos.app.integrations.IntegrationsActivity
import dev.contextos.app.integrations.OfflineSpeechSpeaker
import android.widget.Toast
import androidx.core.view.WindowCompat
import dev.contextos.app.insights.InsightEngine
import dev.contextos.app.insights.InsightInput
import dev.contextos.app.insights.InsightPreferences
import dev.contextos.app.insights.InsightFeedback
import dev.contextos.app.insights.DeviceInsight
import dev.contextos.app.insights.InsightNotificationScheduler
import dev.contextos.app.ui.InsightSettingsCard
import dev.contextos.app.context.ContextState
import dev.contextos.app.context.ContextViewModel
import dev.contextos.app.model.*
import dev.contextos.app.memory.MemoryState
import dev.contextos.app.memory.MemoryViewModel
import dev.contextos.app.ui.MemoryPage
import dev.contextos.app.ui.AppearanceSettings
import dev.contextos.app.ui.ContextOsTheme
import dev.contextos.app.ui.ThemeChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class Destination(val title: String) { HOME("Home"), CHAT("Chat"), CONTEXT("Context"), MEMORY("Memory"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    private val assistant: AssistantViewModel by viewModels()
    private val contextModel: ContextViewModel by viewModels()
    private val memoryModel: MemoryViewModel by viewModels()
    private val suggestionSettings by lazy { InsightPreferences(applicationContext) }
    private var lastKnowledgeRevision: String? = null

    override fun onResume() {
        super.onResume()
        val revision = getSharedPreferences("contextos_knowledge_choices", MODE_PRIVATE)
            .getString("privacy_revision", "") ?: ""
        if (lastKnowledgeRevision != null && revision != lastKnowledgeRevision) {
            // The user edited/revoked archived context while the integration screen was open.
            // Reset active model state as soon as it is safe; old excerpts must not linger.
            lifecycleScope.launch {
                assistant.state.first { !it.generating && !it.busy }
                assistant.newChat()
            }
        }
        lastKnowledgeRevision = revision
    }
    private val speechDelegate = lazy {
        OfflineSpeechSpeaker(applicationContext) { message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
    }
    private val speech by speechDelegate
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        suggestionSettings.notificationsEnabled = granted
        InsightNotificationScheduler.sync(applicationContext, granted)
    }
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(assistant::importModel)
    }
    private val embeddingPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(memoryModel::importEmbeddingModel)
    }
    private val memoryExporter = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(memoryModel::exportPlaintext)
    }
    private val activityPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        contextModel.refreshPermissions()
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) contextModel.refreshLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ enforces edge-to-edge. Consume status, navigation and IME insets in Compose.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = AppearanceSettings(applicationContext)
        InsightNotificationScheduler.sync(applicationContext, suggestionSettings.notificationsEnabled)
        setContent {
            val ai by assistant.state.collectAsStateWithLifecycle()
            val context by contextModel.state.collectAsStateWithLifecycle()
            val memories by memoryModel.state.collectAsStateWithLifecycle()
            var tab by remember { mutableStateOf(if (intent.hasExtra("voice_prompt")) Destination.CHAT else Destination.HOME) }
            DisposableEffect(tab) {
                if (tab == Destination.CONTEXT) contextModel.onVisible() else contextModel.onHidden()
                onDispose { contextModel.onHidden() }
            }
            ContextOsTheme(settings) {
                ContextApp(
                    tab = tab, navigate = { tab = it }, ai = ai, context = context,
                    prefillChat = intent.getStringExtra("voice_prompt").orEmpty(),
                    speakAssistant = { reply -> speech.speak(reply) },
                    memory = memories, appearance = settings, insightSettings = suggestionSettings,
                    requestSuggestionNotifications = { enabled ->
                        if (!enabled) {
                            suggestionSettings.notificationsEnabled = false
                            InsightNotificationScheduler.sync(applicationContext, false)
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            suggestionSettings.notificationsEnabled = true
                            InsightNotificationScheduler.sync(applicationContext, true)
                        }
                    },
                    explainSuggestion = { insight ->
                        if (ai.initialized && !ai.busy && !ai.generating) {
                            assistant.send("Explain why this locally generated suggestion may be useful: " +
                                insight.title + ". Rule: " + insight.detail +
                                ". Observed evidence: " + insight.basedOn +
                                ". Explain in plain language, distinguish estimates from facts, " +
                                "and give an optional practical next step. Do not infer health or personal traits.")
                        }
                    },
                    importModel = { modelPicker.launch(arrayOf("*/*")) },
                    loadModel = assistant::loadModel, selectBackend = assistant::chooseBackend,
                    send = { prompt ->
                        lifecycleScope.launch {
                            val privateNotes = memoryModel.recallForPromptAsync(prompt)
                            val knowledge = KnowledgeViewModel.recall(applicationContext, prompt)
                            val memoriesForQuestion = listOfNotNull(privateNotes, knowledge).joinToString("\n").take(2800)
                            assistant.send(prompt,
                                if (settings.shareContextWithChat) contextModel.currentSummary() else null,
                                memoriesForQuestion)
                        }
                    },
                    newChat = assistant::newChat, clearError = assistant::clearError,
                    toggleSensors = contextModel::setSensorsEnabled,
                    requestSteps = {
                        if (Build.VERSION.SDK_INT >= 29)
                            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    },
                    activityAllowed = contextModel.hasActivityPermission(),
                    requestLocation = {
                        if (contextModel.hasLocationPermission()) contextModel.refreshLocation()
                        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    usageAllowed = contextModel.hasUsagePermission(),
                    requestUsage = {
                        if (contextModel.hasUsagePermission()) contextModel.refreshUsage()
                        else startActivity(contextModel.usageSettingsIntent())
                    },
                    saveSnapshot = contextModel::saveSnapshot,
                    clearHistory = contextModel::clearHistory,
                    addMemory = memoryModel::add,
                    editMemory = { id, value, kind ->
                        memoryModel.updateMemory(id, value, kind)
                        if (memories.includeInChat) assistant.newChat()
                    },
                    importEmbedding = { embeddingPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    requestMemorySummary = assistant::proposeMemoryFromChat,
                    dismissMemorySummary = assistant::dismissMemoryProposal,
                    removeMemory = { id ->
                        memoryModel.remove(id)
                        if (memories.includeInChat) assistant.newChat()
                    },
                    clearMemories = {
                        memoryModel.clearAll()
                        if (memories.includeInChat) assistant.newChat()
                    },
                    exportMemories = { memoryExporter.launch("ContextOS-private-memories.txt") },
                    setMemoryChatConsent = { enabled ->
                        memoryModel.updateChatConsent(enabled)
                        if (!enabled) assistant.newChat()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (speechDelegate.isInitialized()) speech.close()
        super.onDestroy()
    }
}

@Composable
private fun ContextApp(
    tab: Destination, navigate: (Destination) -> Unit,
    prefillChat: String, speakAssistant: (String) -> Unit,
    ai: AssistantState, context: ContextState, memory: MemoryState, appearance: AppearanceSettings,
    insightSettings: InsightPreferences, requestSuggestionNotifications: (Boolean) -> Unit,
    explainSuggestion: (DeviceInsight) -> Unit,
    importModel: () -> Unit, loadModel: () -> Unit, selectBackend: (DeviceBackend) -> Unit,
    send: (String) -> Unit, newChat: () -> Unit, clearError: () -> Unit,
    toggleSensors: (Boolean) -> Unit, requestSteps: () -> Unit, activityAllowed: Boolean,
    requestLocation: () -> Unit, usageAllowed: Boolean, requestUsage: () -> Unit,
    saveSnapshot: () -> Unit, clearHistory: () -> Unit,
    addMemory: (String, MemoryKind) -> Unit,
    editMemory: (String, String, MemoryKind) -> Unit, removeMemory: (String) -> Unit,
    importEmbedding: () -> Unit, requestMemorySummary: () -> Unit,
    dismissMemorySummary: () -> Unit,
    clearMemories: () -> Unit, exportMemories: () -> Unit,
    setMemoryChatConsent: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!keyboardVisible) NavigationBar(containerColor = scheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Destination.entries.forEach { item ->
                    val icon = when (item) {
                        Destination.HOME -> Icons.Default.Home
                        Destination.CHAT -> Icons.Default.Forum
                        Destination.CONTEXT -> Icons.Default.Sensors
                        Destination.MEMORY -> Icons.Default.Bookmarks
                        Destination.SETTINGS -> Icons.Default.Tune
                    }
                    NavigationBarItem(
                        selected = item == tab, onClick = { navigate(item) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(item.title) }, alwaysShowLabel = true
                    )
                }
            }
        },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(15.dp), color = scheme.primaryContainer) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ContextOS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("ContextOS · Phases 1–6",
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                if (tab == Destination.CHAT) {
                    IconButton(onClick = newChat, enabled = !ai.busy && !ai.generating) {
                        Icon(Icons.Default.AddComment, contentDescription = "New conversation", tint = scheme.primary)
                    }
                } else {
                    Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(50)) {
                        Text(if (ai.initialized) "AI ready" else "AI loading", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = scheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (tab) {
                Destination.HOME -> HomePage(ai, context, appearance, openChat = { navigate(Destination.CHAT) },
                    openContext = { navigate(Destination.CONTEXT) },
                    openMemory = { navigate(Destination.MEMORY) }, savedMemories = memory.notes.size,
                    insightSettings = insightSettings,
                    explainSuggestion = { insight -> explainSuggestion(insight); if (ai.initialized && !ai.busy && !ai.generating) navigate(Destination.CHAT) })
                Destination.CHAT -> ChatPage(ai, send, importModel, loadModel, newChat, clearError,
                    keyboardVisible, prefillChat, speakAssistant)
                Destination.CONTEXT -> ContextPage(context, toggleSensors, requestSteps, activityAllowed,
                    requestLocation, usageAllowed, requestUsage, saveSnapshot, clearHistory)
                Destination.MEMORY -> MemoryPage(memory, addMemory, editMemory, removeMemory,
                    clearMemories, exportMemories, setMemoryChatConsent,
                    modelIdle = !ai.busy && !ai.generating, ai = ai,
                    requestSummary = requestMemorySummary, dismissSummary = dismissMemorySummary,
                    importEmbedding = importEmbedding)
                Destination.SETTINGS -> SettingsPage(appearance, ai, importModel, loadModel, selectBackend, clearHistory,
                    insightSettings, requestSuggestionNotifications)
            }
        }
    }
}

@Composable
private fun HomePage(
    ai: AssistantState, context: ContextState, appearance: AppearanceSettings,
    openChat: () -> Unit, openContext: () -> Unit,
    openMemory: () -> Unit, savedMemories: Int,
    insightSettings: InsightPreferences, explainSuggestion: (DeviceInsight) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Text("A clearer picture of your day", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("A private assistant that lives on your phone. You're always in control of what it knows.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(scheme.primary, Color(0xFF13483D))),
                RoundedCornerShape(28.dp)
            ).padding(23.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFE0FFF2), modifier = Modifier.size(29.dp))
                Text("Your space, your intelligence", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (ai.initialized) "Your offline AI is ready for your questions."
                    else "Your included model is preparing. You can explore context meanwhile.",
                    color = Color(0xFFE0F7EB), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(3.dp))
                FilledTonalButton(onClick = openChat,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Color(0xFF12483E))) {
                    Text("Open assistant"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
                }
            }
        }
        InsightsSection(context, appearance, openContext, insightSettings,
            ai.initialized && !ai.busy && !ai.generating, explainSuggestion)
        Surface(onClick = openMemory, color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmarks, null, tint = scheme.primary, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Private memory", fontWeight = FontWeight.SemiBold)
                    Text(if (savedMemories == 0) "Save preferences and facts on your device"
                         else savedMemories.toString() + " saved encrypted memories",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, null, tint = scheme.onSurfaceVariant)
            }
        }
        SectionTitle("Your signals", "Live when you enable a source")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Movement", context.motionLabel.takeIf { context.sensing } ?: "Off", Icons.Default.DirectionsWalk,
                Modifier.weight(1f))
            MetricTile("Light", context.ambientLux?.let { "${it.toInt()} lux" } ?: "—", Icons.Default.WbSunny,
                Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Steps · session", context.stepsSinceOpen?.toString() ?: "—", Icons.Default.Timeline,
                Modifier.weight(1f))
            MetricTile("Usage · 24h", context.appUseMinutes?.let { "$it min" } ?: "—", Icons.Default.Schedule,
                Modifier.weight(1f))
        }
        Surface(onClick = openContext, color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, null, tint = scheme.primary, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Explore your context", fontWeight = FontWeight.SemiBold)
                    Text("Choose data sources and save a snapshot",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = scheme.onSurfaceVariant)
            }
        }
        Text("Insights are local and rule-based, not medical, behavioral or location predictions.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}


@Composable
private fun InsightsSection(
    context: ContextState, appearance: AppearanceSettings, openContext: () -> Unit,
    insightSettings: InsightPreferences, canExplain: Boolean, explain: (DeviceInsight) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    SectionTitle("Your insights", "Explainable suggestions, computed locally")
    if (!appearance.insightsEnabled) {
        Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your context stays yours", fontWeight = FontWeight.SemiBold)
                Text("Turn on on-device insights to see suggestions from explicitly enabled or saved signals. Off by default.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                TextButton(onClick = { appearance.updateInsightsConsent(true) }) {
                    Text("Enable local insights")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    } else {
        // Sensors stop when leaving Context. An insight may use only a recently captured reading.
        val recentSensorReading = context.sensorsEnabled &&
            (context.lastSensorObservedAtMs?.let { at ->
                System.currentTimeMillis() - at in 0L..300_000L
            } ?: false)
        val insights = InsightEngine.recommendations(
            InsightInput(
                enabled = true,
                sensing = recentSensorReading,
                ambientLux = if (recentSensorReading) context.ambientLux else null,
                motionLabel = context.motionLabel,
                stepsSinceOpen = context.stepsSinceOpen,
                appUseMinutes = context.appUseMinutes,
                hasSavedSnapshot = context.snapshots.isNotEmpty()
            )
        )
        val personalized = insightSettings.applyFeedback(insights)
        if (personalized.isEmpty()) {
            Text("You dismissed these suggestions. They will stay hidden unless feedback is reset.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        personalized.forEach { insight ->
            Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TipsAndUpdates, null, tint = scheme.primary,
                            modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(insight.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text(insight.detail, style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                    Text("Why: " + insight.basedOn, style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            insightSettings.submitFeedback(insight.id, InsightFeedback.HELPFUL)
                            insightSettings.rememberSuggestion(insight)
                        }) { Text(if (insightSettings.feedback(insight.id) == InsightFeedback.HELPFUL) "✓ Helpful" else "Helpful") }
                        TextButton(onClick = { insightSettings.submitFeedback(insight.id, InsightFeedback.NOT_HELPFUL) }) {
                            Text("Not helpful")
                        }
                    }
                    TextButton(onClick = { explain(insight) }, enabled = canExplain) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Explain with offline AI")
                    }
                }
            }
        }
        TextButton(onClick = openContext) {
            Text("Manage data sources")
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, color = scheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatPage(
    state: AssistantState, send: (String) -> Unit, importModel: () -> Unit, loadModel: () -> Unit,
    newChat: () -> Unit, clearError: () -> Unit, keyboardVisible: Boolean,
    prefill: String, speakAssistant: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var draft by rememberSaveable(prefill) { mutableStateOf(prefill) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val submit: () -> Unit = {
        if (draft.isNotBlank()) {
            val prompt = draft
            draft = ""
            send(prompt)
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, keyboardVisible) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        if (!keyboardVisible || !state.initialized) Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.initialized) "Local model connected" else "Preparing local model",
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(state.status, style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (!state.initialized && !state.busy) TextButton(onClick = loadModel, enabled = state.modelName != null) {
                        Text("Load")
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                }
                state.importPercent?.let {
                    LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                }
                if (state.modelName == null) OutlinedButton(onClick = importModel) { Text("Import model") }
            }
        }
        state.error?.let { message ->
            Surface(color = scheme.errorContainer, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f),
                        color = scheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = clearError, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (state.messages.isEmpty() && !keyboardVisible) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
                    Icon(Icons.Default.Psychology, null, Modifier.padding(18.dp).size(34.dp),
                        tint = scheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(17.dp))
                Text("What's on your mind?", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Private, on-device conversations. Your prompts stay on your phone.",
                    textAlign = TextAlign.Center, color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(19.dp))
                listOf("Help me organize this afternoon", "Explain event-driven architecture",
                    "Give me a focused learning plan").forEach { prompt ->
                    OutlinedCard(onClick = { draft = prompt; focusRequester.requestFocus(); keyboard?.show() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = scheme.primary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(prompt, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.NorthEast, null, tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        } else if (state.messages.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                items(state.messages, key = { it.id }) { message -> ChatBubble(message, speakAssistant) }
            }
        }
        if (!keyboardVisible) state.benchmark?.let { b ->
            val facts = buildList {
                b.firstChunkMillis?.let { add("First response ${it}ms") }
                b.generateMillis?.let { add("Total ${it}ms") }
                if (b.characters > 0) add("${"%.1f".format(Locale.US, b.charsPerSecond)} char/s")
            }
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text("Ask anything offline…") }, maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    enabled = state.initialized && !state.busy && !state.generating,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = submit,
                    enabled = draft.isNotBlank() && state.initialized && !state.busy && !state.generating,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
        if (!keyboardVisible) Text("100% on-device model · No cloud inference",
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, speak: (String) -> Unit) {
    val user = message.role == Role.USER
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "YOU" else "CONTEXTOS",
            style = MaterialTheme.typography.labelSmall,
            color = if (user) scheme.onSurfaceVariant else scheme.primary,
            modifier = Modifier.padding(start = 9.dp, bottom = 5.dp, end = 9.dp))
        Surface(
            color = if (user) scheme.primaryContainer else scheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(if (user) 0.88f else 0.96f)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (message.generating && message.content.isEmpty()) "Thinking locally…" else message.content,
                    modifier = Modifier.padding(16.dp),
                    color = if (user) scheme.onPrimaryContainer else scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge)
                if (!user && !message.generating && message.content.isNotBlank()) {
                    TextButton(onClick = { speak(message.content) },
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Read aloud offline")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextPage(
    state: ContextState, toggleSensors: (Boolean) -> Unit, requestSteps: () -> Unit,
    activityAllowed: Boolean, requestLocation: () -> Unit, usageAllowed: Boolean,
    requestUsage: () -> Unit, saveSnapshot: () -> Unit, clearHistory: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Your device, in context", "Nothing is collected until you enable or request a source.")
        SourceCard(Icons.Default.Sensors, "Live device sensors",
            "Motion and ambient light while this tab is open.",
            trailing = { Switch(checked = state.sensorsEnabled, onCheckedChange = toggleSensors) })
        if (state.sensorsEnabled && !activityAllowed) {
            OutlinedButton(onClick = requestSteps, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DirectionsWalk, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Allow step counter")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("Movement", if (state.sensing) state.motionLabel else "Off",
                Icons.Default.DirectionsWalk, Modifier.weight(1f))
            MetricTile("Steps · session", state.stepsSinceOpen?.toString() ?: "—",
                Icons.Default.Timeline, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("Ambient light", state.ambientLux?.let { "${it.toInt()} lux" } ?: "—",
                Icons.Default.WbSunny, Modifier.weight(1f))
            MetricTile("Approx. area", state.locationArea ?: "—",
                Icons.Default.LocationOn, Modifier.weight(1f))
        }
        SourceCard(Icons.Default.LocationOn, "Approximate location",
            "One location lookup, only when you request it. Rounded to two decimals.",
            trailing = { TextButton(onClick = requestLocation) { Text("Check") } })
        SourceCard(Icons.Default.Apps, "App usage",
            "Estimated total foreground time over the past 24 hours. On demand.",
            trailing = { TextButton(onClick = requestUsage) { Text(if (usageAllowed) "Refresh" else "Allow") } })
        if (!usageAllowed) Text("Usage access is granted in Android Settings, not through a normal permission prompt.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        if (state.appUseMinutes != null) {
            Text("Last 24h estimated app foreground time: ${state.appUseMinutes} minutes",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(state.status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        state.error?.let {
            Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = saveSnapshot, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp)); Text("Save current snapshot on device")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Saved snapshots", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = clearHistory, enabled = state.snapshots.isNotEmpty()) { Text("Delete all") }
        }
        if (state.snapshots.isEmpty()) {
            Text("No saved snapshots yet. Your live readings are not persisted automatically.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else {
            state.snapshots.forEach { record ->
                Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(record.atMillis)), style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary)
                        Text(record.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Privacy: sensors stop when you leave this tab. Location and usage are read only on request. " +
            "Saved snapshots remain in the app's private SQLite database until deleted.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String,
    trailing: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = scheme.primaryContainer) {
                Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.padding(11.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsPage(
    appearance: AppearanceSettings, ai: AssistantState, importModel: () -> Unit,
    loadModel: () -> Unit, selectBackend: (DeviceBackend) -> Unit, clearHistory: () -> Unit,
    insightSettings: InsightPreferences, requestSuggestionNotifications: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val settingsContext = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Make it yours", "A calm interface with clear controls.")
        Surface(onClick = { settingsContext.startActivity(Intent(settingsContext, IntegrationsActivity::class.java)) },
            color = scheme.primaryContainer, shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Extension, null, tint = scheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Integrations & Personal Agent", fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer)
                    Text("Gmail · Drive · Calendar · Health · Documents · Commute · Career · Voice",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                }
                Icon(Icons.Default.ChevronRight, null, tint = scheme.onPrimaryContainer)
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = scheme.primary)
                    Spacer(Modifier.width(10.dp)); Text("Appearance", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = appearance.choice == choice,
                            onClick = { appearance.setTheme(choice) },
                            label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Match wallpaper colors")
                        Text("Android 12+ dynamic color", style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = appearance.useDynamicColor, onCheckedChange = appearance::setDynamicColor,
                        enabled = Build.VERSION.SDK_INT >= 31)
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = scheme.primary)
                    Spacer(Modifier.width(10.dp)); Text("Personal context", fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow AI to use live context")
                        Text("When on, new messages include the currently available sensor, " +
                            "location and usage summary. Off by default.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = appearance.shareContextWithChat,
                        onCheckedChange = appearance::setShareContext)
                }
                Text("No background monitoring or cloud sync.",
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TipsAndUpdates, null, tint = scheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Local insights", fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable explainable recommendations")
                        Text("Generate local rules-based suggestions using opted-in context. Any AI explanation runs only when requested.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = appearance.insightsEnabled,
                        onCheckedChange = appearance::updateInsightsConsent)
                }
                Text("Separate from sharing context with AI. Both controls are off by default.",
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = scheme.primary)
                    Spacer(Modifier.width(10.dp)); Text("On-device model", fontWeight = FontWeight.Bold)
                }
                Text(ai.modelName ?: "No local model selected", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ai.status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceBackend.entries.forEach { backend ->
                        FilterChip(selected = ai.backend == backend,
                            enabled = !ai.busy && !ai.generating,
                            onClick = { selectBackend(backend) }, label = { Text(backend.name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = importModel, enabled = !ai.busy && !ai.generating,
                        modifier = Modifier.weight(1f)) { Text("Import") }
                    Button(onClick = loadModel,
                        enabled = ai.modelName != null && !ai.busy && !ai.generating,
                        modifier = Modifier.weight(1f)) { Text("Load model") }
                }
            }
        }
        InsightSettingsCard(insightSettings, requestSuggestionNotifications)
        Text("ContextOS · Offline-first personal intelligence",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
    }
}
