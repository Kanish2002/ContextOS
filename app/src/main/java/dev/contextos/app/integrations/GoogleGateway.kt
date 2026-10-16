package dev.contextos.app.integrations

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

enum class GoogleScope(val url: String, val label: String) {
    GMAIL("https://www.googleapis.com/auth/gmail.readonly", "Gmail read-only (restricted scope)"),
    DRIVE("https://www.googleapis.com/auth/drive.readonly", "Drive read-only (restricted scope)"),
    CALENDAR("https://www.googleapis.com/auth/calendar.readonly", "Google Calendar read-only")
}

/** The access token is only kept in RAM for a user-triggered request; never persisted or logged. */
class GoogleGateway(private val activity: Activity) {
    private val api = Identity.getAuthorizationClient(activity)
    var ephemeralToken: String? = null
        private set
    var grantedScope: GoogleScope? = null
        private set

    fun authorize(
        scope: GoogleScope, launchResolution: (IntentSenderRequest) -> Unit,
        message: (String) -> Unit, onGranted: () -> Unit
    ) {
        ephemeralToken = null
        grantedScope = null
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope.url))).build()
        api.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) {
                val pending = result.pendingIntent
                if (pending != null) launchResolution(IntentSenderRequest.Builder(pending.intentSender).build())
                else message("Google did not return a consent dialog.")
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) message("Google did not return an access token.")
                else { ephemeralToken = token; grantedScope = scope; onGranted() }
            }
        }.addOnFailureListener { message("Google authorization failed: " + (it.message ?: "Unknown error")) }
    }

    fun handleResolution(data: Intent?, scope: GoogleScope?, message: (String) -> Unit, onGranted: () -> Unit) {
        if (scope == null || data == null) return
        try {
            val result = api.getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) message("No access token was granted.")
            else { ephemeralToken = token; grantedScope = scope; onGranted() }
        } catch(e: Exception) { message("Consent failed: " + (e.message ?: "Unknown error")) }
    }

    fun disconnect() { ephemeralToken = null; grantedScope = null }

    suspend fun listPreview(): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        val token = ephemeralToken ?: error("First authorize this Google service.")
        when (grantedScope ?: error("No Google scope authorized")) {
            GoogleScope.GMAIL -> {
                val listing = get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15", token)
                val refs = listing.optJSONArray("messages")
                if (refs == null) emptyList() else (0 until minOf(refs.length(), 15)).mapNotNull { i ->
                    val id = refs.optJSONObject(i)?.optString("id") ?: return@mapNotNull null
                    val msg = get("https://gmail.googleapis.com/gmail/v1/users/me/messages/" +
                        URLEncoder.encode(id, "UTF-8") + "?format=metadata", token)
                    val header = msg.optJSONObject("payload")?.optJSONArray("headers")
                    var subject = "Email"
                    if (header != null) for (k in 0 until header.length()) {
                        val o = header.optJSONObject(k) ?: continue
                        if (o.optString("name").equals("subject", true)) subject = o.optString("value", "Email")
                    }
                    KnowledgeEntry(id = id, title = subject.take(150),
                        body = msg.optString("snippet", "(No preview)").take(1400),
                        source = "Selected Gmail preview", category = "CAREER")
                }
            }
            GoogleScope.DRIVE -> {
                val response = get("https://www.googleapis.com/drive/v3/files?page_size=25&fields=" +
                    URLEncoder.encode("nextPageToken,files(id,name,mimeType,description,modifiedTime,webViewLink)", "UTF-8"),
                    token)
                val arr = response.optJSONArray("files")
                if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                    val f = arr.optJSONObject(i) ?: return@mapNotNull null
                    KnowledgeEntry(id = f.optString("id"), title = f.optString("name", "Drive file").take(150),
                        body = "Drive metadata: " + f.optString("description", "") +
                            ". Type: " + f.optString("mimeType") +
                            ". Updated: " + f.optString("modifiedTime") +
                            ". Open: " + f.optString("webViewLink"),
                        source = "Selected Google Drive metadata", category = "DOCUMENT")
                }
            }
            GoogleScope.CALENDAR -> {
                val response = get("https://www.googleapis.com/calendar/v3/calendars/primary/events?" +
                    "maxResults=20&singleEvents=true&orderBy=startTime&timeMin=" +
                    URLEncoder.encode(java.time.Instant.now().toString(), "UTF-8"), token)
                val arr = response.optJSONArray("items")
                if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                    val e = arr.optJSONObject(i) ?: return@mapNotNull null
                    val start = e.optJSONObject("start")?.let {
                        it.optString("dateTime", it.optString("date"))
                    } ?: ""
                    val dueAt = runCatching {
                        if (start.length == 10)
                            java.time.LocalDate.parse(start).atStartOfDay(
                                java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        else java.time.OffsetDateTime.parse(start).toInstant().toEpochMilli()
                    }.getOrNull()
                    KnowledgeEntry(title = e.optString("summary", "Calendar event").take(150),
                        body = "Scheduled " + start + ". " + e.optString("description", "").take(1000),
                        source = "Selected Google Calendar event", category = "CAREER",
                        dueAt = dueAt)
                }
            }
        }
    }

    /**
     * Explicit user gesture only: fetches the selected Gmail message or text-capable
     * Drive file and returns it to the local encrypted archive. No bulk sync.
     */
    suspend fun readSelectedContent(entry: KnowledgeEntry): KnowledgeEntry =
        withContext(Dispatchers.IO) {
            val token = ephemeralToken ?: error("Google access expired; authorize again.")
            val id = entry.id
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,200}"))) {
                "Google file identifier is invalid."
            }
            val original = when (grantedScope) {
                GoogleScope.GMAIL -> {
                    require(entry.source == "Selected Gmail preview") {
                        "This entry is not from Gmail."
                    }
                    val message = get("https://gmail.googleapis.com/gmail/v1/users/me/messages/" +
                        id + "?format=full", token)
                    val payload = message.optJSONObject("payload")
                    val snippet = message.optString("snippet")
                    // Prefer plain text to HTML. Nested multi-part emails are visited
                    // only within this one explicitly selected message.
                    val content = collectGmailPlainText(payload, 0).ifBlank { snippet }
                    content.take(16000)
                }
                GoogleScope.DRIVE -> {
                    require(entry.source == "Selected Google Drive metadata") {
                        "This entry is not from Google Drive."
                    }
                    val meta = get("https://www.googleapis.com/drive/v3/files/" + id +
                        "?fields=id,name,mimeType,size", token)
                    val mime = meta.optString("mimeType")
                    val url = when(mime) {
                        "application/vnd.google-apps.document" ->
                            "https://www.googleapis.com/drive/v3/files/$id/export?mimeType=text%2Fplain"
                        "application/vnd.google-apps.spreadsheet" ->
                            "https://www.googleapis.com/drive/v3/files/$id/export?mimeType=text%2Fcsv"
                        "text/plain", "text/markdown", "text/csv", "text/html",
                        "application/json", "application/xml" ->
                            "https://www.googleapis.com/drive/v3/files/$id?alt=media"
                        else -> error(
                            "This file is not directly readable as text. Select it via Android's file picker instead."
                        )
                    }
                    fetchText(url, token).take(16000)
                }
                else -> error("Authorize Gmail or Drive before importing full content.")
            }
            require(original.isNotBlank()) { "This selected Google item contains no readable text." }
            KnowledgeEntry(title = entry.title,
                body = original, source = "Explicitly imported " +
                    (if (grantedScope == GoogleScope.GMAIL) "Gmail message" else "Drive text document"),
                category = entry.category)
        }

    private fun collectGmailPlainText(payload: JSONObject?, depth: Int): String {
        if (payload == null || depth > 8) return ""
        if (payload.optString("mimeType") == "text/plain") {
            val encoded = payload.optJSONObject("body")?.optString("data").orEmpty()
            if (encoded.isNotBlank()) return runCatching {
                String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            }.getOrDefault("").take(16000)
        }
        val children = payload.optJSONArray("parts") ?: return ""
        for (i in 0 until minOf(children.length(), 40)) {
            val text = collectGmailPlainText(children.optJSONObject(i), depth + 1)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun fetchText(address: String, token: String): String {
        require(address.startsWith("https://www.googleapis.com/drive/v3/files/"))
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer " + token)
        try {
            check(connection.responseCode in 200..299) {
                "Google Drive returned HTTP " + connection.responseCode
            }
            val bytes = connection.inputStream.use { it.readBoundedBytes(100_000) }
            require(bytes.size <= 100_000) { "Document exceeds 100 KB import limit." }
            return String(bytes, Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    private fun get(address: String, token: String): JSONObject {
        require(address.startsWith("https://www.googleapis.com/") ||
            address.startsWith("https://gmail.googleapis.com/"))
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 12000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299)
                error("Google returned HTTP " + connection.responseCode + "; check API configuration and consent.")
            val body = connection.inputStream.bufferedReader().use { it.readText().take(256 * 1024) }
            return JSONObject(body)
        } finally { connection.disconnect() }
    }
}
