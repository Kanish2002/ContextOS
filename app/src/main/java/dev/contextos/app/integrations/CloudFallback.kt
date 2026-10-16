package dev.contextos.app.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Explicit one-shot opt-in. No automatic calls, URL/key persistence, silent cloud
 * fallback, or uploading memory archives. The question alone is sent after consent.
 */
object CloudFallback {
    suspend fun ask(endpoint: String, apiKey: String, model: String, question: String): String =
        withContext(Dispatchers.IO) {
            require(question.isNotBlank() && question.length <= 2000)
            require(model.isNotBlank() && model.length <= 100)
            val url = URL(endpoint.trim())
            require(url.protocol == "https" && url.host.isNotBlank() &&
                url.userInfo == null && url.port in -1..443) {
                "Only explicit HTTPS endpoints on default TLS port are allowed."
            }
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 12000
            connection.readTimeout = 30000
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer " + apiKey)
            val payload = JSONObject().put("model", model).put("stream", false)
                .put("messages", JSONArray().put(JSONObject().put("role", "user")
                    .put("content", question))).toString()
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode in 200..299) {
                    "Cloud model returned HTTP " + connection.responseCode
                }
                val response = connection.inputStream.bufferedReader().use { it.readText().take(200_000) }
                JSONObject(response).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").take(7000)
            } finally { connection.disconnect() }
        }
}
