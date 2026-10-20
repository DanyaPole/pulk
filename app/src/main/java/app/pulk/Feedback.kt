package app.pulk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * What the keyboard says back without the screen: a vibration, a note and a spoken letter.
 * Every direction of the star has its own note (a pentatonic scale, any sequence sounds fine) and a turn
 * or an out-and-back changes the rhythm, so the thumb and the ear can tell what was typed.
 * Unlike a web page, a keyboard can use real vibration, which is the strongest eyes-free cue.
 */
class Feedback(private val ctx: Context, private val prefs: Prefs) {
    private val handler = Handler(Looper.getMainLooper())
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsLang = ""

    // ---- vibration ----
    private fun buzz(vararg pattern: Long) {
        if (!prefs.haptics) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
                      else VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {}
    }

    /** The finger came down. */
    fun touch() = buzz(6)

    fun letter(slot: Pulse.Slot, text: String, lang: String, secure: Boolean) {
        when (slot.kind) {
            'l' -> buzz(0, 10, 40, 22)
            'r' -> buzz(0, 22, 40, 10)
            'b' -> buzz(0, 10, 30, 10, 30, 10)
            else -> buzz(14)
        }
        when (prefs.sound) {
            "clicks" -> note(slot)
            "voice" -> if (secure) note(slot) else say(letterName(text, lang), 1.5f)
        }
    }

    fun space(lastWord: String?, secure: Boolean) {
        buzz(28)
        if (prefs.sound == "off") return
        tone(listOf(Triple(131.0, 0.0, 0.12)), 0.3)
        if (prefs.sound == "voice" && !secure && !lastWord.isNullOrEmpty()) handler.postDelayed({ say(lastWord, 1.2f) }, 140)
    }

    fun backspace() {
        buzz(0, 8, 28, 8)
        if (prefs.sound != "off") tone(listOf(Triple(330.0, 0.0, 0.07), Triple(220.0, 0.07, 0.1)), 0.16)
    }

    fun mode() { buzz(16); if (prefs.sound != "off") tone(listOf(Triple(440.0, 0.0, 0.05)), 0.14) }

    /**
     * «Пульк»: the sound of a drop as the keyboard comes out. A short tone whose pitch jumps up fast,
     * which is how a drop falling into water sounds. Silent when the phone is on silent or vibrate.
     */
    fun drop() {
        if (!prefs.dropSound) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return
        val rate = 22050; val dur = 0.16
        val buf = ShortArray((dur * rate).toInt())
        var phase = 0.0
        for (i in buf.indices) {
            val t = i.toDouble() / rate
            val f = 380.0 + 1500.0 * (1 - Math.exp(-t / 0.018))           // the plop: pitch sweeps up
            phase += 2 * PI * f / rate
            val env = minOf(1.0, t / 0.003) * Math.exp(-t / 0.035)
            val blip = if (t > 0.05) 0.35 * sin(2 * PI * 1250 * t) * Math.exp(-(t - 0.05) / 0.02) else 0.0 // a tiny second ring
            buf[i] = ((sin(phase) * env + blip * minOf(1.0, (t - 0.05) / 0.003)) * 0.32 * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        play(buf, rate, dur)
    }

    // ---- sound ----
    private val scale = doubleArrayOf(262.0, 294.0, 330.0, 392.0, 440.0, 523.0, 587.0, 659.0)

    private fun note(slot: Pulse.Slot) {
        if (slot.petal < 0) { tone(listOf(Triple(780.0, 0.0, 0.05)), 0.12); return }
        val f = scale[slot.petal]
        tone(when (slot.kind) {
            'l' -> listOf(Triple(f, 0.0, 0.07), Triple(f * 1.5, 0.07, 0.09))
            'r' -> listOf(Triple(f, 0.0, 0.07), Triple(f * 0.75, 0.07, 0.09))
            'b' -> listOf(Triple(f, 0.0, 0.06), Triple(f, 0.08, 0.08))
            else -> listOf(Triple(f, 0.0, 0.09))
        }, 0.2)
    }

    /** Plays notes (frequency, start, length in seconds) as one short sound. */
    private fun tone(notes: List<Triple<Double, Double, Double>>, vol: Double) {
        val rate = 22050
        val total = notes.maxOf { it.second + it.third } + 0.03
        val buf = ShortArray((total * rate).toInt())
        for ((f, at, dur) in notes) {
            val start = (at * rate).toInt(); val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, t / 0.008) * (1.0 - i.toDouble() / n).let { it * it }
                val idx = start + i
                if (idx < buf.size) buf[idx] = (buf[idx] + sin(2 * PI * f * t) * env * vol * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        play(buf, rate, total)
    }

    private fun play(buf: ShortArray, rate: Int, total: Double) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(buf.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(buf, 0, buf.size)
            track.play()
            handler.postDelayed({ try { track.release() } catch (_: Exception) {} }, (total * 1000).toLong() + 200)
        } catch (_: Exception) {}
    }

    // ---- voice ----
    fun say(text: String, rate: Float = 1.2f) {
        if (text.isBlank()) return
        val lang = if (text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }) "ru" else ttsLang.ifEmpty { prefs.lang }
        if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) speakNow(text, rate, lang)
            }
            return
        }
        if (ttsReady) speakNow(text, rate, lang)
    }

    private fun speakNow(text: String, rate: Float, lang: String) {
        val t = tts ?: return
        if (ttsLang != lang) { t.language = if (lang == "ru") Locale("ru", "RU") else Locale.US; ttsLang = lang }
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pulk")
    }

    fun shutdown() { tts?.shutdown(); tts = null; ttsReady = false }

    private val ruNames = mapOf(
        'а' to "а", 'б' to "бэ", 'в' to "вэ", 'г' to "гэ", 'д' to "дэ", 'е' to "е", 'ё' to "ё", 'ж' to "жэ", 'з' to "зэ", 'и' to "и",
        'й' to "и краткое", 'к' to "ка", 'л' to "эль", 'м' to "эм", 'н' to "эн", 'о' to "о", 'п' to "пэ", 'р' to "эр", 'с' to "эс",
        'т' to "тэ", 'у' to "у", 'ф' to "эф", 'х' to "ха", 'ц' to "цэ", 'ч' to "че", 'ш' to "ша", 'щ' to "ща", 'ъ' to "твёрдый знак",
        'ы' to "ы", 'ь' to "мягкий знак", 'э' to "э", 'ю' to "ю", 'я' to "я",
    )
    private val punct = mapOf(
        "ru" to mapOf("." to "точка", "," to "запятая", "?" to "вопрос", "!" to "восклицание", ":" to "двоеточие", ";" to "точка с запятой",
            "-" to "тире", "—" to "тире", "…" to "многоточие", "(" to "скобка", ")" to "скобка закрыта", "@" to "собака", "#" to "решётка"),
        "en" to mapOf("." to "period", "," to "comma", "?" to "question mark", "!" to "exclamation mark", ":" to "colon", ";" to "semicolon",
            "-" to "dash", "'" to "apostrophe", "@" to "at", "#" to "hash", "(" to "open bracket", ")" to "close bracket"),
    )

    private fun letterName(s: String, lang: String): String {
        val c = s.lowercase()
        if (c.length == 1 && lang == "ru") ruNames[c[0]]?.let { return it }
        return punct[lang]?.get(c) ?: s
    }
}
