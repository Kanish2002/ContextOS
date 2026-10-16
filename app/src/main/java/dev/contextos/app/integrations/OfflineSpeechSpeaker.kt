package dev.contextos.app.integrations

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Opt-in speech playback. Never selects a voice that reports requiring a network.
 * Speak only after the user taps an assistant message's read-aloud control.
 */
class OfflineSpeechSpeaker(context: Context, private val report: (String) -> Unit) {
    private var ready = false
    private var pending: String? = null
    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // TTS implementation and voice availability belong to Android's installed engine.
            val available = runCatching {
                tts.voices?.filter { !it.isNetworkConnectionRequired }.orEmpty()
            }.getOrDefault(emptyList())
            val selected = available.firstOrNull {
                it.locale.language == Locale.getDefault().language
            } ?: available.firstOrNull { it.locale.language == "en" }
            if (selected == null || tts.setVoice(selected) == TextToSpeech.ERROR) {
                report("No locally installed speech voice is available. Install an offline voice in Android Settings.")
                ready = false
            } else {
                ready = true
                pending?.let { phrase -> pending = null; start(phrase) }
            }
        } else report("Android text-to-speech is unavailable.")
    }

    fun speak(raw: String) {
        val value = raw.trim().take(2500)
        if (value.isBlank()) return
        if (!ready) {
            pending = value
            report("Preparing the installed offline speech voice...")
        } else start(value)
    }

    private fun start(text: String) {
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "contextos-offline-speech") ==
            TextToSpeech.ERROR) report("Could not speak with this offline voice.")
    }

    fun stop() { if (ready) tts.stop() }
    fun close() { pending = null; tts.stop(); tts.shutdown() }
}
