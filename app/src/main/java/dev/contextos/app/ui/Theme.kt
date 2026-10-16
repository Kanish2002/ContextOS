package dev.contextos.app.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

class AppearanceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("contextos_appearance", Context.MODE_PRIVATE)
    var choice by mutableStateOf(runCatching {
        ThemeChoice.valueOf(prefs.getString("mode", ThemeChoice.SYSTEM.name) ?: "SYSTEM")
    }.getOrDefault(ThemeChoice.SYSTEM))
        private set
    var useDynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", false))
        private set
    var shareContextWithChat by mutableStateOf(prefs.getBoolean("share_context_with_chat", false))
        private set

    fun setTheme(value: ThemeChoice) {
        choice = value
        prefs.edit().putString("mode", value.name).apply()
    }

    fun setDynamicColor(value: Boolean) {
        useDynamicColor = value
        prefs.edit().putBoolean("dynamic_color", value).apply()
    }

    fun setShareContext(value: Boolean) {
        shareContextWithChat = value
        prefs.edit().putBoolean("share_context_with_chat", value).apply()
    }
}

private val Teal = Color(0xFF006B5B)
private val NightTeal = Color(0xFF7EE2C6)
private val Ink = Color(0xFF12211E)
private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4E8), onPrimaryContainer = Color(0xFF054C41),
    secondary = Color(0xFF49645D), background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8), surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFECF3EF), surfaceContainerHigh = Color(0xFFE3EEE8),
    onBackground = Ink, onSurface = Ink, onSurfaceVariant = Color(0xFF50615B),
    outlineVariant = Color(0xFFCAD9D2)
)
private val Dark = darkColorScheme(
    primary = NightTeal, onPrimary = Color(0xFF003A31),
    primaryContainer = Color(0xFF125246), onPrimaryContainer = Color(0xFFC9F8EA),
    secondary = Color(0xFFADCDC0), background = Color(0xFF0B1412),
    surface = Color(0xFF0B1412), surfaceContainerLow = Color(0xFF13211D),
    surfaceContainer = Color(0xFF192922), surfaceContainerHigh = Color(0xFF22352D),
    onBackground = Color(0xFFE5F0EA), onSurface = Color(0xFFE5F0EA),
    onSurfaceVariant = Color(0xFFACBDB5), outlineVariant = Color(0xFF354D42)
)

@Composable
fun ContextOsTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val dark = when (settings.choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
