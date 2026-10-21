package app.pulk

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The phone's voice for «Говорить», its lesson and drills: one place for the language (by the letters of the
 * text), the person's pace, pitch and chosen voice, and the calm mode. Only voices of the phone itself are
 * offered: a voice that needs the network would send the phrase out, and Пульк has no internet.
 */
class Voice(ctx: Context, private val prefs: Prefs, private val onReady: (() -> Unit)? = null) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { ready = it == TextToSpeech.SUCCESS; if (ready) onReady?.invoke() }
    /** What was said last (also when the voice is not ready yet — the screens and tests read it). */
    var last = ""; private set

    /** Russian voices on this phone, by name, the best first; empty until the engine is ready. */
    fun ruVoices(): List<String> = if (!ready) emptyList() else try {
        (tts.voices ?: emptySet()).filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }.sortedByDescending { it.quality }.map { it.name }
    } catch (_: Exception) { emptyList() }

    fun say(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        last = t
        if (!ready) return
        val ru = t.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }
        tts.language = if (ru) Locale("ru", "RU") else Locale.US
        if (ru && prefs.voice.isNotEmpty()) try { tts.voices?.firstOrNull { it.name == prefs.voice }?.let { tts.voice = it } } catch (_: Exception) {}
        tts.setSpeechRate(if (prefs.calm) minOf(prefs.speakRate, 0.9f) else prefs.speakRate)
        tts.setPitch(if (prefs.calm) 0.95f * prefs.pitch else prefs.pitch)
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pulk-voice")
    }

    fun shutdown() = tts.shutdown()
}
