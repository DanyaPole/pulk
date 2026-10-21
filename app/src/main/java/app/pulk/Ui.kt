package app.pulk

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The app's screens, built for one thumb (the design system «Пульк», project «Интерфейс»): reading at the top,
 * everything to press at the bottom, the main action 64 dp, nothing pressable in the far top corner.
 * Selection is never colour alone: a chosen option is filled and carries a check mark.
 */
class Ui(private val a: Activity) {
    private val dark = a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val bg = c(if (dark) "#0B1114" else "#EEF3F5")
    val panel = c(if (dark) "#1B262C" else "#FFFFFF")
    val ink = c(if (dark) "#EDF3F6" else "#0F1E26")
    val muted = c(if (dark) "#A3B4BC" else "#4A5B64")
    val accent = c(if (dark) "#4CC2DE" else "#0A6680")
    val onAccent = c(if (dark) "#032029" else "#FFFFFF")
    val line = c(if (dark) "#5E737D" else "#7F929C")
    private fun c(h: String) = Color.parseColor(h)
    private val dp = a.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()

    /** A screen: [body] scrolls at the top, [bottom] stays under the thumb. */
    fun screen(body: LinearLayout, bottom: LinearLayout): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
        addView(ScrollView(a).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(bottom, LinearLayout.LayoutParams(-1, -2))
    }
    fun column(top: Int = 28) = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(top), px(20), px(12)) }
    fun bottomBar() = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(8), px(16), px(20)); setBackgroundColor(bg) }

    fun text(s: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(a).apply {
        text = s; textSize = size; setTextColor(color); setLineSpacing(0f, 1.18f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, px(6), 0, px(6))
    }
    fun title(s: String) = text(s, 28f, ink, true)
    fun h(s: String) = text(s, 19f, ink, true).apply { setPadding(0, px(20), 0, px(4)) }
    fun note(s: String) = text(s, 14f, muted)

    /** A button: [main] is the screen's one big action (64 dp, filled), the others 52 dp outlined. */
    fun button(s: String, main: Boolean = false, onClick: (View) -> Unit) = Button(a).apply {
        text = s; isAllCaps = false; textSize = if (main) 18f else 16f
        setTypeface(typeface, if (main) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (main) onAccent else ink)
        background = GradientDrawable().apply { cornerRadius = px(16).toFloat(); setColor(if (main) accent else panel); setStroke(px(if (main) 0 else 1), line) }
        val h = px(if (main) 64 else 52); minHeight = h; minimumHeight = h
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) }
        setOnClickListener { onClick(it) }
    }

    /** A row of choices; the chosen one is filled and marked with ✓ (not by colour alone). */
    fun choice(title: String, options: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit): View {
        val wrap = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(text(title, 16f, ink, true))
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = ArrayList<Triple<String, String, Button>>()
        fun paint() {
            val cur = get()
            for ((v, label, b) in buttons) {
                val on = v == cur
                b.text = if (on) "✓ $label" else label
                b.background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(if (on) accent else panel); setStroke(px(1), if (on) accent else line) }
                b.setTextColor(if (on) onAccent else ink)
            }
        }
        for ((v, label) in options) {
            val b = Button(a).apply {
                isAllCaps = false; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(2), 0, px(2), 0) }
                minHeight = px(52); minimumHeight = px(52); setPadding(px(6), px(8), px(6), px(8))
                setOnClickListener { set(v); paint() }
            }
            buttons.add(Triple(v, label, b)); row.addView(b)
        }
        paint(); wrap.addView(row)
        return wrap
    }
    fun onOff(title: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        choice(title, listOf("on" to "Вкл", "off" to "Выкл"), { if (get()) "on" else "off" }) { set(it == "on") }

    /** A panel with a frame, for status and notes. */
    fun card(vararg views: View) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setPadding(px(16), px(12), px(16), px(12))
        background = GradientDrawable().apply { cornerRadius = px(16).toFloat(); setColor(panel); setStroke(px(1), line) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
        views.forEach { addView(it) }
    }
    fun gravityCenter(v: TextView) = v.apply { gravity = Gravity.CENTER }

    companion object {
        /** Is Пульк switched on in Android's list of keyboards? */
        fun enabled(ctx: Context) = (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.any { it.packageName == ctx.packageName }
        /** And is it the keyboard in use now? */
        fun selected(ctx: Context) = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").startsWith(ctx.packageName + "/")
    }
}
