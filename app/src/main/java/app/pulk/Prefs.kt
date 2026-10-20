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

    val left get() = hand == "left"
}
