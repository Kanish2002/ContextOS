package dev.contextos.app.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LiveRouteEstimate(
    val travelSeconds: Long,
    val typicalSeconds: Long?,
    val distanceMeters: Long,
    val advisory: String
)

/** User-invoked Google Routes API. Addresses and user-supplied API key are never persisted. */
object LiveRoutePlanner {
    fun leaveByMillis(arrivalAtMs: Long, travelSeconds: Long, bufferMinutes: Int): Long {
        require(travelSeconds in 0..86400L && bufferMinutes in 0..120) {
            "Invalid travel time or buffer."
        }
        return arrivalAtMs - travelSeconds * 1000 - bufferMinutes * 60_000L
    }

    fun delayMinutes(liveSeconds: Long, baselineSeconds: Long?): Long? =
        baselineSeconds?.let { ((liveSeconds - it).coerceAtLeast(0) + 59) / 60 }

    private fun seconds(value: String): Long {
        require(value.endsWith("s")) { "Expected Routes API duration in seconds." }
        return value.dropLast(1).toDouble().toLong().coerceIn(0, 86400L)
    }

    suspend fun estimate(origin: String, destination: String, key: String): LiveRouteEstimate =
        withContext(Dispatchers.IO) {
            require(origin.trim().length in 3..180 && destination.trim().length in 3..180) {
                "Enter both origin and destination."
            }
            require(key.isNotBlank() && key.length < 1024) { "Provide your Maps Routes API key." }
            val request = JSONObject()
                .put("origin", JSONObject().put("address", origin.trim()))
                .put("destination", JSONObject().put("address", destination.trim()))
                .put("travelMode", "DRIVE")
                .put("routingPreference", "TRAFFIC_AWARE")
                .put("computeAlternativeRoutes", false)
                .toString()
            val conn = URL("https://routes.googleapis.com/directions/v2:computeRoutes")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", key)
            conn.setRequestProperty("X-Goog-FieldMask",
                "routes.duration,routes.staticDuration,routes.distanceMeters")
            try {
                conn.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
                check(conn.responseCode == 200) {
                    "Routes request failed (HTTP " + conn.responseCode +
                        "). Confirm Routes API, billing, and API key restrictions."
                }
                val bytes = conn.inputStream.use { it.readBoundedBytes(120_000) }
                check(bytes.size <= 120_000) { "Routes reply exceeds size limit." }
                val route = JSONObject(String(bytes, Charsets.UTF_8))
                    .getJSONArray("routes").getJSONObject(0)
                val live = seconds(route.getString("duration"))
                val typical = route.optString("staticDuration", "").takeIf { it.isNotBlank() }
                    ?.let(::seconds)
                LiveRouteEstimate(live, typical, route.optLong("distanceMeters", 0L),
                    "Traffic-aware estimate from Google Routes at request time; not a guarantee.")
            } finally { conn.disconnect() }
        }
}
