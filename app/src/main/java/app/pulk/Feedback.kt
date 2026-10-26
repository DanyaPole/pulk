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

    /** For tests: every vibration, sound and spoken word, as text. */
    var trace: ((String) -> Unit)? = null
    var silent: () -> Boolean = { false }

    // ---- vibration ----
    private fun buzz(vararg pattern: Long) {
        if (silent()) return
        if (!prefs.haptics) return
        trace?.invoke("buzz " + pattern.joinToString(","))
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
                      else VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {}
    }

    /** The finger came down. */
    fun touch() = buzz(6)
    fun replacement() = buzz(18)

    /** When TalkBack is on, speech goes through it (one voice, the person's own settings) instead of our own. */
    var announcer: ((String) -> Unit)? = null
    var exploring: () -> Boolean = { false }

    // ---- the sound compass: while the finger moves, what it would type if lifted now ----
    private var pendingName: Runnable? = null
    fun compass(slot: Pulse.Slot, text: String, lang: String, secure: Boolean) {
        // a password: nothing at all, not even the tick — how often it ticks would show the way the finger went
        if (secure) return
        if (prefs.haptics) {
            trace?.invoke("tick")
            val v = vib
            if (v != null && v.hasVibrator()) try {
                v.vibrate(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) else VibrationEffect.createOneShot(4, 60))
            } catch (_: Exception) {}
        }
        val mode = if (exploring()) "voice" else prefs.compass
        when (mode) {
            "notes" -> if (slot.petal >= 0) tone(listOf(Triple(scale[slot.petal] * (if (slot.kind == 'c') 1.0 else 1.5), 0.0, 0.045)), 0.09)
            "voice" -> {
                // only once the finger settles on a letter for a moment, so it does not chatter
                pendingName?.let { handler.removeCallbacks(it) }
                val name = if (slot.isSpace) (if (lang == "ru") "пробел" else "space") else letterName(text, lang)
                val r = Runnable { say(name, 1.9f) }
                pendingName = r; handler.postDelayed(r, 170)
            }
        }
    }
    /** The finger lifted: what it was about to say is no longer news. */
    fun hush() { pendingName?.let { handler.removeCallbacks(it) }; pendingName = null }
    fun silence() { hush(); try { tts?.stop() } catch (_: Exception) {} }

    fun letter(slot: Pulse.Slot, text: String, lang: String, secure: Boolean) {
        if (secure) {
            // a password: every letter feels and sounds the same, so neither the gesture nor the direction can be heard
            buzz(14)
            if (prefs.sound != "off" || exploring()) tone(listOf(Triple(SECURE_CLICK, 0.0, 0.03)), 0.18)
            return
        }
        when (slot.kind) {
            'l' -> buzz(0, 10, 40, 22)
            'r' -> buzz(0, 22, 40, 10)
            'b' -> buzz(0, 10, 30, 10, 30, 10)
            else -> buzz(14)
        }
        // with TalkBack every letter is spoken through it; otherwise as the sound setting says
        when (if (exploring()) "voice" else prefs.sound) {
            "clicks" -> note(slot)
            "voice" -> say(letterName(text, lang), 1.5f)
        }
    }
    private val SECURE_CLICK = 600.0   // Hz: one neutral click for every letter of a password

    fun space(lastWord: String?, secure: Boolean) {
        buzz(28)
        if (prefs.sound == "off") return
        tone(listOf(Triple(131.0, 0.0, 0.12)), 0.3)
        if (prefs.sound == "voice" && !secure && !lastWord.isNullOrEmpty()) {
            val r = Runnable { pendingName = null; say(lastWord, 1.2f) }
            pendingName = r; handler.postDelayed(r, 140)
        }
    }

    fun backspace() {
        buzz(0, 8, 28, 8)
        if (prefs.sound != "off") tone(listOf(Triple(330.0, 0.0, 0.07), Triple(220.0, 0.07, 0.1)), 0.16)
    }

    /** «Два касания»: a petal opened. Its note, and (voice) the names of its letters — never in a password. */
    fun petal(p: Int, letters: List<String>, lang: String, secure: Boolean) {
        buzz(10)
        if (secure) { if (prefs.sound != "off" || exploring()) tone(listOf(Triple(SECURE_CLICK, 0.0, 0.03)), 0.18); return }
        if (prefs.sound != "off") tone(listOf(Triple(scale[p], 0.0, 0.06)), 0.16)
        if (prefs.sound == "voice" || exploring()) say(letters.joinToString(", ") { letterName(it, lang) }, 1.5f)
    }

    fun mode() { buzz(16); if (prefs.sound != "off") tone(listOf(Triple(440.0, 0.0, 0.05)), 0.14) }

    /**
     * «Пульк»: the sound of a drop as the keyboard comes out. A drop into water rings like a small air bubble —
     * a soft tone whose pitch rises as the bubble rises — and a smaller bubble after it. A gentle low-pass keeps it
     * round and wet rather than a beep. The same synthesis renders the samples in tools/drop-sounds.js.
     * Silent when the phone is on silent or vibrate.
     */
    fun drop(kind: String = prefs.dropKind) {
        if (kind == "off" || prefs.calm) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return
        // bubbles: start s, from Hz, to Hz, rise s, decay s, level
        val (bubbles, dur, vol, soft) = when (kind) {
            "glass" -> Quad(listOf(doubleArrayOf(0.0, 220.0, 560.0, 0.05, 0.09, 1.0), doubleArrayOf(0.1, 450.0, 700.0, 0.03, 0.05, 0.3)), 0.32, 0.45, 0.28)
            "drip" -> Quad(listOf(doubleArrayOf(0.0, 420.0, 1150.0, 0.025, 0.045, 1.0), doubleArrayOf(0.05, 900.0, 1400.0, 0.015, 0.025, 0.25)), 0.18, 0.38, 0.4)
            else -> Quad(listOf(doubleArrayOf(0.0, 300.0, 820.0, 0.035, 0.065, 1.0), doubleArrayOf(0.075, 620.0, 1050.0, 0.02, 0.035, 0.35)), 0.26, 0.42, 0.35)
        }
        val rate = 22050
        val n = (dur * rate).toInt()
        val acc = DoubleArray(n)
        for (bb in bubbles) {
            var phase = 0.0
            for (i in (bb[0] * rate).toInt() until n) {
                val t = i.toDouble() / rate - bb[0]
                val f = bb[1] + (bb[2] - bb[1]) * (1 - Math.exp(-t / bb[3]))
                phase += 2 * PI * f / rate
                acc[i] += sin(phase) * (1 - Math.exp(-t / 0.006)) * Math.exp(-t / bb[4]) * bb[5]
            }
        }
        var y = 0.0
        val fade = (0.02 * rate).toInt()
        val buf = ShortArray(n) { i -> y += soft * (acc[i] - y); val e = if (i > n - fade) (n - i).toDouble() / fade else 1.0
            (y * e * vol * 32767).toInt().coerceIn(-32768, 32767).toShort() }
        play(buf, rate, dur)
    }
    private data class Quad(val a: List<DoubleArray>, val b: Double, val c: Double, val d: Double)

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
        if (silent()) return
        trace?.invoke("tone " + notes.joinToString(";") { "${it.first}/${it.second}/${it.third}" } + " @" + vol)
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
        if (silent()) return
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
        if (silent()) return
        if (text.isBlank()) return
        trace?.invoke("say " + text)
        if (exploring()) { announcer?.invoke(text); return }
        val lang = Voice.languageFor(text, prefs.lang)
        if (tts == null) {
            tts = try {
                TextToSpeech(ctx.applicationContext) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS && tts != null
                    if (ttsReady) speakNow(text, rate, lang)
                }
            } catch (_: Exception) { null }   // no speech engine: the keyboard goes on silently
            return
        }
        if (ttsReady) speakNow(text, rate, lang)
    }

    private fun speakNow(text: String, rate: Float, lang: String) {
        if (silent()) return
        val t = tts ?: return
        try {   // an engine that throws must not take the keyboard down with it
            if (!Voice.useLocalVoice(t, lang, if (lang == "ru") prefs.voice else "")) return
            t.setSpeechRate(rate)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pulk")
        } catch (_: Exception) {}
    }

    fun shutdown() { try { tts?.shutdown() } catch (_: Exception) {}; tts = null; ttsReady = false }

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

    fun letterName(s: String, lang: String): String {
        val c = s.lowercase()
        if (c.length == 1 && lang == "ru") ruNames[c[0]]?.let { return it }
        return punct[lang]?.get(c) ?: s
    }
}
