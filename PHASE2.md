# Phase 2 implementation notes

UI: Four-tab Material 3 interface. Appearance: System / Light / Dark, optionally dynamic wallpaper colors (Android 12+). All preferences stored in device-private SharedPreferences.

Sensors: SensorManager TYPE_ACCELEROMETER, TYPE_LIGHT and optional TYPE_STEP_COUNTER. Only running while Context tab is visible AND toggle on. Steps count starts at first reading in this process; not an all-day health metric. Movement is approximate acceleration intensity, not a verified walking/driving classifier.

Location: Android LocationManager foreground, one-shot COARSE_LOCATION with fallback on older OS versions. Rounded coordinates to 2 decimal places. May be unavailable indoors or when disabled.

Usage: Android UsageStatsManager, last 24h aggregate estimated minutes. Requires user-granted Usage Access special permission; never polled in background.

Storage: android.database.sqlite.SQLiteOpenHelper app-private contextos_private_events.db. Only explicit Save Snapshot persists data; Delete all removes saved rows. No encryption or sync implemented.

AI: Context inclusion opt-in in Settings, off by default. Only the latest currently available summary, bounded to 900 characters, is added to user prompt sent to on-device LiteRT-LM. The visible chat bubble retains the original typed user message.

Next: on-device permission testing, UI screenshots, event persistence and scheduling rules for Phase 3 recommendations.
