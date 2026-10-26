package app.pulk

import android.content.Context

/** The few settings, kept on the phone only. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pulk", Context.MODE_PRIVATE)

    var hand: String
        get() = sp.getString("hand", "right") ?: "right"
        set(v) = sp.edit().putString("hand", v).apply()
    /** off | clicks | voice */
    var sound: String
        get() = sp.getString("sound", "off") ?: "off"
        set(v) = sp.edit().putString("sound", v).apply()
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()
    /** Eyes-free: a bigger star, a tap anywhere is a space. */
    var blind: Boolean
        get() = sp.getBoolean("blind", false)
        set(v) = sp.edit().putBoolean("blind", v).apply()
    /** Keyboard size, percent. */
    var size: Int
        get() = sp.getInt("size", 100)
        set(v) = sp.edit().putInt("size", v).apply()
    var lang: String
        get() = sp.getString("lang", "ru") ?: "ru"
        set(v) = sp.edit().putString("lang", v).apply()

    /** Word suggestions above the star. */
    var suggest: Boolean
        get() = sp.getBoolean("suggest", true)
        set(v) = sp.edit().putBoolean("suggest", v).apply()
    /** Space replaces an unknown word with the closest one; ⌫ right after brings the typed one back. */
    var autocorrect: Boolean
        get() = sp.getBoolean("autocorrect", true)
        set(v) = sp.edit().putBoolean("autocorrect", v).apply()
    /** Remember words this person types (kept on the phone only). */
    var learn: Boolean
        get() = sp.getBoolean("learn", true)
        set(v) = sp.edit().putBoolean("learn", v).apply()

    /** Levels of the tutorial passed, as bits. */
    var lessonsDone: Int
        get() = sp.getInt("lessons_done", 0)
        set(v) = sp.edit().putInt("lessons_done", v).apply()
    /** The learner said «not now» to help on these levels (bits): it is not offered there again. */
    var helpNo: Int
        get() = sp.getInt("help_no", 0)
        set(v) = sp.edit().putInt("help_no", v).apply()
    /** Set by the settings screen; the keyboard clears its learned words the next time it opens. */
    var forget: Boolean
        get() = sp.getBoolean("forget", false)
        set(v) = sp.edit().putBoolean("forget", v).apply()

    /** The «пульк» sound when the keyboard comes out: bul · glass · drip · off. */
    var dropKind: String
        get() = sp.getString("drop_kind", null) ?: if (sp.getBoolean("drop", true)) "bul" else "off"
        set(v) = sp.edit().putString("drop_kind", v).apply()

    /** pulse: a stroke per letter · jabs («Толчки»): a whole word per touch, a letter per jab out and back. */
    var mode: String
        get() = sp.getString("mode", "pulse") ?: "pulse"
        set(v) = sp.edit().putString("mode", v).apply()

    /** Ten completed words in «Толчки» before offering the next way. Never switches modes. */
    var jabsWords: Int
        get() = sp.getInt("jabs_words", 0)
        set(v) = sp.edit().putInt("jabs_words", v).apply()
    /** corner: the star in the thumb's corner · screen: a stroke may start anywhere, the star comes to the finger. */
    var zone: String
        get() = sp.getString("zone", "corner") ?: "corner"
        set(v) = sp.edit().putString("zone", v).apply()

    /** The keyboard shows what is being typed in its own window, which can be dragged anywhere. */
    var textWindow: Boolean
        get() = sp.getBoolean("text_window", false)
        set(v) = sp.edit().putBoolean("text_window", v).apply()
    /** Where the text window was left: its left edge as a share of the width, its top in dp above the bottom (-1: default). */
    var fieldX: Float
        get() = sp.getFloat("field_x", -1f)
        set(v) = sp.edit().putFloat("field_x", v).apply()
    var fieldUp: Float
        get() = sp.getFloat("field_up", -1f)
        set(v) = sp.edit().putFloat("field_up", v).apply()

    /** From the calibration: this thumb's stroke length against the textbook one, and how much to smooth tremor (0 off). */
    var tol: Float
        get() = sp.getFloat("tol", 1f)
        set(v) = sp.edit().putFloat("tol", v).apply()
    var smooth: Float
        get() = sp.getFloat("smooth", 0f)
        set(v) = sp.edit().putFloat("smooth", v).apply()
    /** How far off each of the 8 directions this thumb goes, degrees, as "a,b,c,…". */
    var bias: String
        get() = sp.getString("bias", "") ?: ""
        set(v) = sp.edit().putString("bias", v).apply()
    var calibrated: Boolean
        get() = sp.getBoolean("calibrated", false)
        set(v) = sp.edit().putBoolean("calibrated", v).apply()
    fun applyBias() { val p = bias.split(",").mapNotNull { it.trim().toDoubleOrNull() }; for (i in 0 until 8) Pulse.bias[i] = p.getOrElse(i) { 0.0 } }

    /** After a space: the words that usually come next. */
    var predict: Boolean
        get() = sp.getBoolean("predict", true)
        set(v) = sp.edit().putBoolean("predict", v).apply()

    /** The sound compass: off · notes (a quiet note of the direction) · voice (the letter's name), before the finger lifts. */
    var compass: String
        get() = sp.getString("compass", "off") ?: "off"
        set(v) = sp.edit().putString("compass", v).apply()

    /** Простой режим: letters, space, ⌫, ⏎, ?123, hide and one suggestion; no hidden gestures, no autocorrection. */
    var simple: Boolean
        get() = sp.getBoolean("simple", false)
        set(v) = sp.edit().putBoolean("simple", v).apply()

    /** The first-start wizard was gone through (or closed). */
    var wizardDone: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(v) = sp.edit().putBoolean("wizard_done", v).apply()

    /** Тема: auto (the phone's light or dark) · contrast (black and white, amber accent, bold letters — concept «Маяк»). */
    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(v) = sp.edit().putString("theme", v).apply()
    val contrast get() = theme == "contrast"

    /**
     * The extra gestures this person has switched on: "space" (stroke the space key: . , delete word, best word),
     * "hold" (hold for ё ъ !), "cursor" (hold space and slide), "bkspword" (stroke ⌫ towards the star: a word).
     * Not set yet (someone who used Пульк before) means all of them.
     */
    var gestures: Set<String>
        get() = sp.getString("gestures", null)?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: GESTURES
        set(v) = sp.edit().putString("gestures", v.joinToString(",")).apply()
    fun has(g: String) = g in gestures

    /** «Проверка скорости»: the last results, newest first ("date|mode|cpm|accuracy|erases"). */
    var speedHistory: List<String>
        get() = (sp.getString("speed", "") ?: "").split("\n").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("speed", v.joinToString("\n")).apply()

    /** Scanning: how long each thing stays lit, ms. */
    var scanMs: Int
        get() = sp.getInt("scan_ms", 1200)
        set(v) = sp.edit().putInt("scan_ms", v).apply()

    /** «Три дня»: the day (epoch day) the first session was done, -1 not started. */
    var course3Start: Long
        get() = sp.getLong("course3_start", -1)
        set(v) = sp.edit().putLong("course3_start", v).apply()

    /** How the star looks: petals (a flower) or ripples (circles on water from a drop). */
    var look: String
        get() = sp.getString("look", "petals") ?: "petals"
        set(v) = sp.edit().putString("look", v).apply()

    /** «Говорить»: the phrases said, newest first; say each word as it is typed; the voice's pace. */
    var phrases: List<String>
        get() = (sp.getString("phrases", "") ?: "").split("\n").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("phrases", v.joinToString("\n")).apply()
    var speakWords: Boolean
        get() = sp.getBoolean("speak_words", false)
        set(v) = sp.edit().putBoolean("speak_words", v).apply()
    var speakRate: Float
        get() = sp.getFloat("speak_rate", 1.0f)
        set(v) = sp.edit().putFloat("speak_rate", v).apply()

    var speakLessonDone: Boolean
        get() = sp.getBoolean("speak_lesson", false)
        set(v) = sp.edit().putBoolean("speak_lesson", v).apply()

    /** «Говорить»: about me (said and shown with one touch), the person's own phrases, and the calm mode. */
    var aboutMe: String
        get() = sp.getString("about_me", "") ?: ""
        set(v) = sp.edit().putString("about_me", v).apply()
    var myPhrases: List<String>
        get() = (sp.getString("my_phrases", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("my_phrases", v.joinToString("\n")).apply()
    /** Calm: a softer, slower voice, no «пульк», no ripples, fewer things on the «Говорить» screen. */
    var calm: Boolean
        get() = sp.getBoolean("calm", false)
        set(v) = sp.edit().putBoolean("calm", v).apply()

    /** «Тренировки по ситуациям»: themes gone through (bits). */
    var drillsDone: Int
        get() = sp.getInt("drills_done", 0)
        set(v) = sp.edit().putInt("drills_done", v).apply()

    /** «Привычная»: after each letter, a small star shows how it is stroked (the bridge to «Пульс»). */
    var bridge: Boolean
        get() = sp.getBoolean("bridge", true)
        set(v) = sp.edit().putBoolean("bridge", v).apply()

    /** «Мой пруд»: drops gathered for steps that worked. Only ever grows. */
    var drops: Int
        get() = sp.getInt("drops", 0)
        set(v) = sp.edit().putInt("drops", v).apply()

    /** «Предложить выбор» (see [Together]): the choice on screen (empty: none), the option touched, recent choices. */
    var choice: String
        get() = sp.getString("choice", "") ?: ""
        set(v) = sp.edit().putString("choice", v).apply()
    var choicePicked: Int
        get() = sp.getInt("choice_picked", -1)
        set(v) = sp.edit().putInt("choice_picked", v).apply()
    var choices: List<String>
        get() = (sp.getString("choices", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("choices", v.joinToString("\n")).apply()
    /** «Словарь семьи»: word + photo file per line. */
    var family: List<String>
        get() = (sp.getString("family", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("family", v.joinToString("\n")).apply()
    /** «Разговор»: the child's and the adult's lines in turns. */
    var talk: List<String>
        get() = (sp.getString("talk", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("talk", v.joinToString("\n")).apply()
    /** Accidental touches: 0 off, 1 light, 2 strong (a brush too short to be a letter, a bounce right after a touch). */
    var guard: Int
        get() = sp.getInt("guard", 0)
        set(v) = sp.edit().putInt("guard", v).apply()

    /** «Подстроить под человека» (see [Comfort]): the answers, «quick» for «Начать сразу»; a gentle offer made once. */
    var doors: String
        get() = sp.getString("doors", "") ?: ""
        set(v) = sp.edit().putString("doors", v).apply()
    var offered: Boolean
        get() = sp.getBoolean("offered", false)
        set(v) = sp.edit().putBoolean("offered", v).apply()

    /** The voice chosen in «Удобство» → «Речь» (empty: the phone's default) and its pitch (1.3 sounds younger). */
    var voice: String
        get() = sp.getString("voice", "") ?: ""
        set(v) = sp.edit().putString("voice", v).apply()
    var pitch: Float
        get() = sp.getFloat("pitch", 1f)
        set(v) = sp.edit().putFloat("pitch", v).apply()

    /** «Пузыри»: bubbles popped (opens new letters) and hits/misses per letter (hard letters come more often). */
    var popped: Int
        get() = sp.getInt("popped", 0)
        set(v) = sp.edit().putInt("popped", v).apply()
    var letters: String
        get() = sp.getString("letters", "") ?: ""
        set(v) = sp.edit().putString("letters", v).apply()
    var bubblesHelp: Boolean
        get() = sp.getBoolean("bubblesHelp", true)
        set(v) = sp.edit().putBoolean("bubblesHelp", v).apply()

    /** The week's journal (see [Together.logged]). */
    var journal: List<String>
        get() = (sp.getString("journal", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("journal", v.joinToString("\n")).apply()

    /** «Проверка на этом телефоне»: what was marked (see [Check.decode]). */
    var check: String
        get() = sp.getString("check", "") ?: ""
        set(v) = sp.edit().putString("check", v).apply()

    /** Bigger text on the app's screens (on top of the phone's own font size, which Android already applies). */
    var bigText: Boolean
        get() = sp.getBoolean("big_text", false)
        set(v) = sp.edit().putBoolean("big_text", v).apply()

    val left get() = hand == "left"

    companion object {
        val GESTURES = setOf("space", "hold", "cursor", "bkspword")
        /** A newcomer learns one way, «Пульс»; «Толчки» live in «Для опытных». Who chose them keeps them in the list. */
        fun modes(p: Prefs, base: List<Pair<String, String>>): List<Pair<String, String>> {
            val extra = mutableListOf<Pair<String, String>>()
            if (p.mode in listOf("jabs", "petals") || p.jabsWords >= 10) extra.add("jabs" to "Толчки")
            if (p.jabsWords >= 10 || p.mode == "petals") extra.add("petals" to "Лепестки")
            return base.take(1) + extra + base.drop(1)
        }
        const val JABS_NOTE = "Толчки — целое слово одним касанием, не отрывая пальца: толчок к букве и назад. Быстрее, когда «Пульс» уже освоен."
        const val PETALS_NOTE = "Лепестки — толкай к нужному лепестку и назад, точную букву выбирать не надо. Поднял палец — слово с пробелом. Касание звезды — другой вариант; смах без возврата — по буквам для этого слова. Есть и кнопки над звездой. В пароле — Пульс без звука."
    }
}
