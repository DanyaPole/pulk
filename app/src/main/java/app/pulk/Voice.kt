package app.pulk

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The phone's voice for «Говорить», its lesson and drills: one place for the language (by the letters of the
 * text), the person's pace and the calm mode. Works without internet.
 */
class Voice(ctx: Context, private val prefs: Prefs) {
    private var ready = false
    private val tts = TextToSpeech(ctx.applicationContext) { ready = it == TextToSpeech.SUCCESS }
    /** What was said last (also when the voice is not ready yet — the screens and tests read it). */
    var last = ""; private set

    fun say(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        last = t
        if (!ready) return
        tts.language = if (t.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }) Locale("ru", "RU") else Locale.US
        tts.setSpeechRate(if (prefs.calm) minOf(prefs.speakRate, 0.9f) else prefs.speakRate)
        tts.setPitch(if (prefs.calm) 0.95f else 1f)
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pulk-voice")
    }

    fun shutdown() = tts.shutdown()
}
