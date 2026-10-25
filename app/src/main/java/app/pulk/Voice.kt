package app.pulk

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * The phone's voice for «Говорить», its lesson and drills: one place for the language (by the letters of the
 * text), the person's pace, pitch and chosen voice, and the calm mode. Only voices of the phone itself are
 * offered: a voice that needs the network would send the phrase out, and Пульк has no internet.
 */
class Voice(ctx: Context, private val prefs: Prefs, private val onReady: (() -> Unit)? = null, private val onTrouble: (() -> Unit)? = null) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var ready = false
    private var gone = false
    /** Why the phone cannot speak, in the person's words; null while starting and when all is well. */
    var trouble: String? = null; private set
    private val tts: TextToSpeech? = try {
        TextToSpeech(ctx.applicationContext) { status ->
            // later, never inside the constructor: with no engine at all Android answers at once, before the screen
            // that asked has its Voice, and the screen would fall reading it
            main.post {
                if (gone) return@post
                ready = status == TextToSpeech.SUCCESS
                trouble = if (!ready) NO_ENGINE else if (!hasRussian()) NO_RUSSIAN else null
                if (ready) { onReady?.invoke(); pending?.let { say(it) } }
                pending = null
                if (trouble != null) onTrouble?.invoke()
            }
        }
    } catch (_: Exception) { trouble = NO_ENGINE; null }
    private fun hasRussian() = ruVoices().isNotEmpty()
    /** What was said last (also when the voice is not ready yet — the screens and tests read it). */
    var last = ""; private set
    /** Said while the engine was still starting (the first touch on a screen): spoken as soon as it is ready. */
    private var pending: String? = null

    /** Russian voices on this phone, by name, the best first; empty until the engine is ready. */
    fun ruVoices(): List<String> = if (!ready) emptyList() else try {
        (tts?.voices ?: emptySet()).filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }.sortedByDescending { it.quality }.map { it.name }
    } catch (_: Exception) { emptyList() }

    fun say(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        last = t
        val tts = tts; if (!ready || gone || tts == null) { if (!gone && tts != null && trouble == null) pending = t; return }
        try {   // an engine can throw on a language or voice it half-knows: then this phrase is silent, the app goes on
            val ru = t.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
            if (!useLocalVoice(tts, if (ru) "ru" else "en", if (ru) prefs.voice else "")) {
                val reason = if (ru) NO_RUSSIAN else "На телефоне нет английского голоса"
                if (trouble != reason) { trouble = reason; onTrouble?.invoke() }
                return
            }
            if (trouble != null) { trouble = null; onTrouble?.invoke() }
            tts.setSpeechRate(if (prefs.calm) minOf(prefs.speakRate, 0.9f) else prefs.speakRate)
            tts.setPitch(if (prefs.calm) 0.95f * prefs.pitch else prefs.pitch)
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pulk-voice")
        } catch (_: Exception) {}
    }

    fun shutdown() { gone = true; try { tts?.shutdown() } catch (_: Exception) {} }

    companion object {
        /** Never let setLanguage or the engine's default choose a network voice. Recheck on every phrase. */
        internal fun useLocalVoice(tts: TextToSpeech, lang: String, preferred: String = ""): Boolean {
            val local = (tts.voices ?: emptySet()).filter { it.locale.language == lang && !it.isNetworkConnectionRequired }
            val voice = local.firstOrNull { it.name == preferred } ?: local.maxByOrNull { it.quality } ?: return false
            return tts.setVoice(voice) == TextToSpeech.SUCCESS
        }
        const val NO_ENGINE = "На телефоне не найден синтез речи"
        const val NO_RUSSIAN = "На телефоне нет русского голоса"
        /** Where to get one: the phone's own settings, no internet for Пульк. */
        const val HOW = "Настройки → Специальные возможности → Синтез речи: выберите нужный язык и скачайте голос. Пока фразу можно «Показать собеседнику»."
    }
}
