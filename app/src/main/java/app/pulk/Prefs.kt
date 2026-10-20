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

    val left get() = hand == "left"
}
