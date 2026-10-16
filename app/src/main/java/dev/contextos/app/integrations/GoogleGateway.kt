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
                    KnowledgeEntry(title = subject.take(150),
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
                    KnowledgeEntry(title = f.optString("name", "Drive file").take(150),
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
                    KnowledgeEntry(title = e.optString("summary", "Calendar event").take(150),
                        body = "Scheduled " + start + ". " + e.optString("description", "").take(1000),
                        source = "Selected Google Calendar event", category = "CAREER")
                }
            }
        }
    }

    private fun get(address: String, token: String): JSONObject {
        require(address.startsWith("https://www.googleapis.com/") ||
            address.startsWith("https://gmail.googleapis.com/"))
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 12000
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299)
                error("Google returned HTTP " + connection.responseCode + "; check API configuration and consent.")
            val bytes = connection.inputStream.use { it.readNBytes(256 * 1024) }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
