package dev.contextos.app.integrations

enum class PermissionKind { USER_SELECTED_FILE, RUNTIME_PERMISSION, SPECIAL_ACCESS, GOOGLE_OAUTH, HEALTH_CONNECT, EXPLICIT_EXPORT, OPTIONAL_CLOUD }

data class IntegrationCapability(
    val id: String, val title: String, val permission: PermissionKind,
    val offline: Boolean, val description: String
)

object IntegrationCatalog {
    val capabilities = listOf(
        IntegrationCapability("gmail", "Gmail", PermissionKind.GOOGLE_OAUTH, false, "OAuth authorization and restricted Gmail scopes; not enabled until configured and verified."),
        IntegrationCapability("drive", "Google Drive", PermissionKind.GOOGLE_OAUTH, false, "OAuth and user-selected Drive files; requires Google Cloud configuration."),
        IntegrationCapability("calendar", "Google Calendar", PermissionKind.GOOGLE_OAUTH, false, "Read selected calendars with explicit OAuth scope."),
        IntegrationCapability("health", "Health Connect", PermissionKind.HEALTH_CONNECT, true, "Granular read grants for selected health record types."),
        IntegrationCapability("notifications", "Notification listener", PermissionKind.SPECIAL_ACCESS, true, "User must explicitly enable sensitive notification access in Android settings."),
        IntegrationCapability("documents", "Selected documents", PermissionKind.USER_SELECTED_FILE, true, "Android document picker; never scans all files."),
        IntegrationCapability("photos", "Selected photos", PermissionKind.USER_SELECTED_FILE, true, "Android Photo Picker; only selected items."),
        IntegrationCapability("timeline", "Maps Timeline export", PermissionKind.EXPLICIT_EXPORT, true, "Import user-exported Timeline JSON; no general Timeline history API."),
        IntegrationCapability("whatsapp", "WhatsApp export/share", PermissionKind.EXPLICIT_EXPORT, true, "Import a user-provided chat export or Android share intent; no private app database access."),
        IntegrationCapability("search", "Personal memory and RAG", PermissionKind.USER_SELECTED_FILE, true, "Search authorized notes and imported documents."),
        IntegrationCapability("commute", "Commute assistant", PermissionKind.OPTIONAL_CLOUD, false, "Calendar plus route API and location consent required for live traffic."),
        IntegrationCapability("career", "Career and learning", PermissionKind.USER_SELECTED_FILE, true, "Opted-in goals, plans and interview notes."),
        IntegrationCapability("agent", "Proactive assistant", PermissionKind.RUNTIME_PERMISSION, true, "Opt-in reminders, approval before external actions."),
        IntegrationCapability("ranking", "Adaptive suggestions", PermissionKind.RUNTIME_PERMISSION, true, "Use local feedback to rank rules."),
        IntegrationCapability("multimodal", "Multimodal perception", PermissionKind.USER_SELECTED_FILE, true, "Explicit image selection and compatible on-device model."),
        IntegrationCapability("voice", "Voice interaction", PermissionKind.RUNTIME_PERMISSION, true, "Microphone permission and separately installed offline speech models."),
        IntegrationCapability("cloud", "Optional cloud fallback", PermissionKind.OPTIONAL_CLOUD, false, "Off by default; separate consent and provider configuration.")
    )
}
