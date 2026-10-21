package app.pulk

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The phone's voice for «Говорить», its lesson and drills: one place for the language (by the letters of the
 * text), the person's pace, pitch and chosen voice, and the calm mode. Only voices of the phone itself are
 * offered: a voice that needs the network would send the phrase out, and Пульк has no internet.
 */
class Voice(ctx: Context, private val prefs: Prefs, private val onReady: (() -> Unit)? = null, private val onTrouble: (() -> Unit)? = null) {
    private var ready = false
    private var gone = false
    /** Why the phone cannot speak, in the person's words; null while starting and when all is well. */
    var trouble: String? = null; private set
    private val tts: TextToSpeech? = try {
        TextToSpeech(ctx.applicationContext) { status ->
            if (gone) return@TextToSpeech
            ready = status == TextToSpeech.SUCCESS
            trouble = if (!ready) NO_ENGINE else if (!hasRussian()) NO_RUSSIAN else null
            if (ready) onReady?.invoke()
            if (trouble != null) onTrouble?.invoke()
        }
    } catch (_: Exception) { trouble = NO_ENGINE; null }
    private fun hasRussian() = try { (tts?.isLanguageAvailable(Locale("ru", "RU")) ?: TextToSpeech.LANG_AVAILABLE) >= TextToSpeech.LANG_AVAILABLE } catch (_: Exception) { true }
    /** What was said last (also when the voice is not ready yet — the screens and tests read it). */
    var last = ""; private set

    /** Russian voices on this phone, by name, the best first; empty until the engine is ready. */
    fun ruVoices(): List<String> = if (!ready) emptyList() else try {
        (tts?.voices ?: emptySet()).filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }.sortedByDescending { it.quality }.map { it.name }
    } catch (_: Exception) { emptyList() }

    fun say(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        last = t
        val tts = tts; if (!ready || gone || tts == null) return
        try {   // an engine can throw on a language or voice it half-knows: then this phrase is silent, the app goes on
            val ru = t.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }
            tts.language = if (ru) Locale("ru", "RU") else Locale.US
            if (ru && prefs.voice.isNotEmpty()) try { tts.voices?.firstOrNull { it.name == prefs.voice }?.let { tts.voice = it } } catch (_: Exception) {}
            tts.setSpeechRate(if (prefs.calm) minOf(prefs.speakRate, 0.9f) else prefs.speakRate)
            tts.setPitch(if (prefs.calm) 0.95f * prefs.pitch else prefs.pitch)
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pulk-voice")
        } catch (_: Exception) {}
    }

    fun shutdown() { gone = true; try { tts?.shutdown() } catch (_: Exception) {} }

    companion object {
        const val NO_ENGINE = "На телефоне не найден синтез речи"
        const val NO_RUSSIAN = "На телефоне нет русского голоса"
        /** Where to get one: the phone's own settings, no internet for Пульк. */
        const val HOW = "Настройки → Специальные возможности → Синтез речи: выберите русский язык и скачайте голос. Пока фразу можно «Показать собеседнику»."
    }
}
