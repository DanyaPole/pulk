package app.pulk

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
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
class Ui(private val a: Context) {
    private val dark = a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val bg = c(if (dark) "#0B1114" else "#EEF3F5")
    val panel = c(if (dark) "#1B262C" else "#FFFFFF")
    val ink = c(if (dark) "#EDF3F6" else "#0F1E26")
    val muted = c(if (dark) "#A3B4BC" else "#4A5B64")
    val accent = c(if (dark) "#4CC2DE" else "#0A6680")
    val onAccent = c(if (dark) "#032029" else "#FFFFFF")
    val line = c(if (dark) "#5E737D" else "#7F929C")
    /** «Срочно» and warnings: warm, never used for «wrong». */
    val urgent = c(if (dark) "#FF8A75" else "#A8321F")
    private fun c(h: String) = Color.parseColor(h)
    private val dp = a.resources.displayMetrics.density
    /** «Крупный текст»: every size on the screen a fifth bigger, as the web version's. */
    private val k = if (Prefs(a).bigText) 1.2f else 1f
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
        text = s; textSize = size * k; setTextColor(color); setLineSpacing(0f, 1.18f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, px(6), 0, px(6))
    }
    /** Titles and headings are marked for TalkBack, so a person can jump between them. */
    fun title(s: String) = text(s, 28f, ink, true).apply { if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
    fun h(s: String) = text(s, 19f, ink, true).apply { setPadding(0, px(20), 0, px(4)); if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
    fun note(s: String) = text(s, 14f, muted)

    /** A button: [main] is the screen's one big action (64 dp, filled), the others 52 dp outlined. */
    fun button(s: String, main: Boolean = false, onClick: (View) -> Unit) = Button(a).apply {
        text = s; isAllCaps = false; textSize = (if (main) 18f else 16f) * k
        setTypeface(typeface, if (main) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (main) onAccent else ink)
        background = GradientDrawable().apply { cornerRadius = px(16).toFloat(); setColor(if (main) accent else panel); setStroke(px(if (main) 0 else 1), line) }
        val h = px(if (main) 64 else 52); minHeight = h; minimumHeight = h
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) }
        setOnClickListener { onClick(it) }
    }

    /**
     * How many buttons of [labels] fit side by side (at most [most]) so that no word breaks in the middle: on a small
     * phone with large text, a row of four becomes two rows of two, or a column.
     */
    fun perRow(labels: List<String>, size: Float, most: Int, inset: Int = 72): Int {
        val m = a.resources.displayMetrics
        val p = android.graphics.Paint().apply { textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size * k, m) }
        // the widest word; the first may carry «✓ » when chosen
        val word = labels.maxOf { l -> l.split(' ').mapIndexed { i, w -> p.measureText(if (i == 0) "✓ $w" else w) }.max() }
        val avail = m.widthPixels - px(inset)
        return (most downTo 1).first { n -> word + px(16) <= avail / n - px(4) }
    }

    /** A row of choices; the chosen one is filled and marked with ✓ (not by colour alone). */
    fun choice(title: String, options: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit): View {
        val wrap = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(text(title, 16f, ink, true))
        val buttons = ArrayList<Triple<String, String, Button>>()
        fun paint() {
            val cur = get()
            for ((v, label, b) in buttons) {
                val on = v == cur
                b.text = if (on) "✓ $label" else label
                b.background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(if (on) accent else panel); setStroke(px(1), if (on) accent else line) }
                b.setTextColor(if (on) onAccent else ink)
                // TalkBack hears «Звук: Вкл, выбрано», not «галочка Вкл».
                b.contentDescription = "$title: $label"; b.isSelected = on
            }
        }
        val n = perRow(options.map { it.second }, 14f, options.size)
        var row = LinearLayout(a)
        for ((j, o) in options.withIndex()) {
            val (v, label) = o
            if (j % n == 0) { row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; if (j > 0) setPadding(0, px(4), 0, 0) }; wrap.addView(row) }
            val b = Button(a).apply {
                isAllCaps = false; textSize = 14f * k
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(2), 0, px(2), 0) }
                minHeight = px(52); minimumHeight = px(52); setPadding(px(6), px(8), px(6), px(8))
                setOnClickListener { set(v); paint() }
            }
            buttons.add(Triple(v, label, b)); row.addView(b)
        }
        repeat((n - options.size % n) % n) { row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)) }   // a short last row keeps the sizes
        paint()
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
        /**
         * Fixed ids for the fields people type in: with an id Android keeps the text when it closes a screen in
         * the background to free memory and opens it again (no id, no memory of it).
         */
        const val ID_SPEAK = 0x5501
        const val ID_TALK_ADULT = 0x5502
        const val ID_TALK_CHILD = 0x5503
        const val ID_ADULT = 0x5510   // «Для взрослого»: its fields in order, from here

        /** Is Пульк switched on in Android's list of keyboards? */
        fun enabled(ctx: Context) = (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.any { it.packageName == ctx.packageName }
        /** And is it the keyboard in use now? */
        fun selected(ctx: Context) = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").startsWith(ctx.packageName + "/")
    }
}
