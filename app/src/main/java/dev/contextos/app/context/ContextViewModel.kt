package dev.contextos.app.context

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class ContextRecord(val atMillis: Long, val summary: String)
data class ContextState(
    val sensorsEnabled: Boolean = false,
    val sensing: Boolean = false,
    val stepsSinceOpen: Int? = null,
    val ambientLux: Float? = null,
    val lastSensorObservedAtMs: Long? = null,
    val motionLabel: String = "Unavailable",
    val locationArea: String? = null,
    val appUseMinutes: Long? = null,
    val snapshots: List<ContextRecord> = emptyList(),
    val status: String = "Enable sensors to see live context.",
    val error: String? = null
)

/** Only summarizes opt-in data. No service, background polling, network calls or raw location trail. */
class ContextViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val app = application
    private val prefs = app.getSharedPreferences("contextos_sources", Context.MODE_PRIVATE)
    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val location = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val database = SnapshotDatabase(app)
    private val _state = MutableStateFlow(ContextState(sensorsEnabled = prefs.getBoolean("sensors", false)))
    val state: StateFlow<ContextState> = _state.asStateFlow()
    private var visible = false
    private var baselineSteps: Int? = null
    private var previousMotionUpdate = 0L

    init { reloadSaved() }

    fun onVisible() {
        visible = true
        syncSensors()
    }

    fun onHidden() {
        visible = false
        sensors.unregisterListener(this)
        _state.value = _state.value.copy(sensing = false)
    }

    fun setSensorsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sensors", enabled).apply()
        _state.value = _state.value.copy(sensorsEnabled = enabled, error = null,
            status = if (enabled) "Reading sensors while this screen is open."
                     else "Sensors are off.",
            stepsSinceOpen = if (enabled) _state.value.stepsSinceOpen else null,
            ambientLux = if (enabled) _state.value.ambientLux else null,
            motionLabel = if (enabled) _state.value.motionLabel else "Unavailable")
        syncSensors()
    }

    fun refreshPermissions() { syncSensors() }

    private fun syncSensors() {
        sensors.unregisterListener(this)
        val enabled = visible && _state.value.sensorsEnabled
        if (enabled) {
            listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LIGHT).forEach { type ->
                sensors.getDefaultSensor(type)?.let {
                    sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            if (hasActivityPermission()) {
                sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                    sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
        }
        _state.value = _state.value.copy(sensing = enabled)
    }

    fun hasActivityPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasUsagePermission(): Boolean {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!visible || !_state.value.sensorsEnabled) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0].toInt()
                if (baselineSteps == null || total < baselineSteps!!) baselineSteps = total
                _state.value = _state.value.copy(stepsSinceOpen = total - (baselineSteps ?: total))
            }
            Sensor.TYPE_LIGHT -> _state.value = _state.value.copy(
                ambientLux = event.values[0], lastSensorObservedAtMs = System.currentTimeMillis())
            Sensor.TYPE_ACCELEROMETER -> {
                val now = SystemClock.elapsedRealtime()
                if (now - previousMotionUpdate < 900L) return
                previousMotionUpdate = now
                val v = event.values
                val magnitude = sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])
                val delta = abs(magnitude - SensorManager.GRAVITY_EARTH)
                _state.value = _state.value.copy(
                    motionLabel = if (delta > 1.8f) "Motion detected" else "Low motion",
                    lastSensorObservedAtMs = System.currentTimeMillis())
            }
        }
    }

    /** One coarse, foreground-only location request. Stored/displayed at ~1km precision. */
    fun refreshLocation() {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(error = "Grant approximate location permission first.")
            return
        }
        try {
            _state.value = _state.value.copy(status = "Checking approximate location...")
            val provider = when {
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                location.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider == null) {
                _state.value = _state.value.copy(status = "Location services are disabled.")
                return
            }
            if (Build.VERSION.SDK_INT >= 30) {
                location.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(app)) { fix ->
                    if (fix == null) {
                        _state.value = _state.value.copy(status = "No location fix available.")
                    } else {
                        val rounded = "%.2f, %.2f".format(Locale.US, fix.latitude, fix.longitude)
                        _state.value = _state.value.copy(locationArea = rounded,
                            status = "Approximate area updated locally.")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val fix = location.getLastKnownLocation(provider)
                _state.value = _state.value.copy(
                    locationArea = fix?.let { "%.2f, %.2f".format(Locale.US, it.latitude, it.longitude) },
                    status = if (fix != null) "Cached area updated locally." else "No cached location available.")
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "Location permission denied.")
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Location unavailable: ${e.message}")
        }
    }

    /** Reading only after user taps Refresh. Stats are estimates supplied by Android. */
    fun refreshUsage() {
        if (!hasUsagePermission()) {
            _state.value = _state.value.copy(error = "Enable usage access in Android Settings first.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val end = System.currentTimeMillis()
                val manager = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val total = manager.queryAndAggregateUsageStats(end - 24L * 60 * 60 * 1000, end)
                    .values.sumOf { it.totalTimeInForeground }
                _state.value = _state.value.copy(appUseMinutes = total / 60000L,
                    status = "App usage refreshed. Android's rolling 24-hour estimate.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Usage statistics unavailable: ${e.message}")
            }
        }
    }

    private fun summary(s: ContextState): String = buildList {
        s.stepsSinceOpen?.let { add("Steps since this session opened: $it") }
        s.ambientLux?.let { add("Ambient light: ${it.toInt()} lux") }
        if (s.sensing) add("Movement sensor: ${s.motionLabel}")
        s.locationArea?.let { add("Approximate area (rounded coordinates): $it") }
        s.appUseMinutes?.let { add("Android estimated app foreground time (last 24h): $it min") }
    }.joinToString("; ")

    fun currentSummary(): String = summary(_state.value)

    fun saveSnapshot() {
        val snapshot = summary(_state.value)
        if (snapshot.isBlank()) {
            _state.value = _state.value.copy(error = "Enable a source or refresh it before saving.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            database.save(snapshot)
            reloadSaved()
            _state.value = _state.value.copy(status = "Snapshot saved privately on this device.")
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clear()
            reloadSaved()
            _state.value = _state.value.copy(status = "Saved history deleted.")
        }
    }

    private fun reloadSaved() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = database.latest()
            _state.value = _state.value.copy(snapshots = records)
        }
    }

    override fun onCleared() {
        sensors.unregisterListener(this)
        database.close()
        super.onCleared()
    }
}

private class SnapshotDatabase(context: Context) :
    SQLiteOpenHelper(context, "contextos_private_events.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT, at_ms INTEGER NOT NULL, summary TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(value: String) {
        writableDatabase.execSQL("INSERT INTO snapshots(at_ms, summary) VALUES(?, ?)",
            arrayOf<Any>(System.currentTimeMillis(), value))
        writableDatabase.execSQL("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY id DESC LIMIT 100)")
    }

    fun latest(): List<ContextRecord> {
        val result = mutableListOf<ContextRecord>()
        readableDatabase.rawQuery("SELECT at_ms, summary FROM snapshots ORDER BY id DESC LIMIT 20", null).use { cursor ->
            while (cursor.moveToNext()) result += ContextRecord(cursor.getLong(0), cursor.getString(1))
        }
        return result
    }

    fun clear() { writableDatabase.delete("snapshots", null, null) }
}
