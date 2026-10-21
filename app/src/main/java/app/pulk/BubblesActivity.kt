package app.pulk

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * «Пузыри» (as the web version): a letter in a bubble; typing it with the star pops it with a «пульк». No time,
 * no losing: a bubble waits; a miss shows the way on the star. Big letters first, then the ones at the centre,
 * then the turns; letters this hand misses come more often. Ten bubbles — a drop in the pond.
 */
class BubblesActivity : Activity() {
    companion object {
        const val ROUND = 10
        /** Letters for a level: 0 big ones, 1 + the centre ones, 2 + the turns. */
        fun pool(level: Int): List<String> {
            val kinds = listOf(listOf(0), listOf(0, 3), listOf(0, 3, 1, 2))[level.coerceIn(0, 2)]
            val t = Pulse.table("ru", false)
            return (0 until 8).flatMap { p -> kinds.mapNotNull { k -> t[p][k].takeIf { it.length == 1 && it[0] in 'а'..'я' } } }
        }
        fun level(popped: Int) = if (popped < 15) 0 else if (popped < 40) 1 else 2
        /** «а:3:1;н:5:0» — hits and misses per letter. */
        fun stats(s: String): MutableMap<String, IntArray> = s.split(';').mapNotNull { e -> e.split(':').takeIf { it.size == 3 }?.let { it[0] to intArrayOf(it[1].toIntOrNull() ?: 0, it[2].toIntOrNull() ?: 0) } }.toMap().toMutableMap()
        fun statsLine(m: Map<String, IntArray>) = m.entries.joinToString(";") { "${it.key}:${it.value[0]}:${it.value[1]}" }
        /** The next letter: hard letters (more misses than hits) are likelier; never the same twice in a row. */
        fun next(level: Int, stats: Map<String, IntArray>, prev: String, r: Double): String {
            val ls = pool(level).filter { it != prev }
            val w = ls.map { c -> val v = stats[c] ?: intArrayOf(0, 0); 1.0 + 3.0 * v[1] / (v[0] + v[1] + 1) }
            var x = r * w.sum()
            for (i in ls.indices) { x -= w[i]; if (x <= 0) return ls[i] }
            return ls.last()
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    private val sounds by lazy { Feedback(this, prefs) }
    internal var letter = ""
    internal var n = 0
    private var miss = 0
    private lateinit var bubble: TextView
    private lateinit var note: TextView
    private lateinit var count: TextView
    internal lateinit var field: EditText

    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = Prefs(this); ui = Ui(this); start() }
    override fun onDestroy() { Coach.reset(); sounds.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onStop() { Coach.pause(); super.onStop() }
    override fun onStart() { super.onStart(); Coach.resume() }

    private fun start() {
        n = 0; miss = 0
        val lv = level(prefs.popped)
        val body = ui.column(28)
        body.addView(ui.title("Пузыри"))
        body.addView(ui.text(listOf("Крупные буквы: прямой смах.", "Добавились буквы у центра: туда и обратно.", "Все буквы: и с поворотом.")[lv] + " Пузырь ждёт — спешить некуда.", 17f, ui.muted))
        count = ui.text("", 18f, ui.muted); body.addView(count)
        bubble = TextView(this).apply {
            textSize = 64f; setTypeface(typeface, Typeface.BOLD); setTextColor(ui.ink); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ui.panel); setStroke(ui.px(3), ui.accent) }
            layoutParams = LinearLayout.LayoutParams(ui.px(140), ui.px(140)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.px(16) }
        }
        body.addView(bubble)
        note = ui.text("", 17f, ui.muted); body.addView(note)
        field = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; textSize = 22f; hint = "Печатай звездой здесь"; setTextColor(ui.ink); setHintTextColor(ui.muted) }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim()?.lowercase() ?: ""
                if (t.isEmpty()) return
                val got = t.last().toString()
                s?.clear()   // the field only carries one letter at a time
                typed(got)
            }
        })
        body.addView(field)
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("К тренировкам", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
        letter = next(lv, stats(prefs.letters), "", Math.random()); paint(); field.requestFocus()
    }

    private fun paint() {
        count.text = (0 until ROUND).joinToString("") { if (it < n) "●" else "○" } + "   $n из $ROUND"
        bubble.text = letter; bubble.scaleX = 1f; bubble.scaleY = 1f; bubble.alpha = 1f
        // at first the way is lit at once; later only after a miss
        if (level(prefs.popped) == 0 || miss > 0) aim() else Coach.reset()
    }
    private fun aim() {
        Coach.active = true; Coach.help = if (miss >= 2) 3 else 2; Coach.next = letter
        Coach.hint = "«$letter»: ${Lessons.how(letter, "ru", prefs.left)}"; Coach.changed()
    }

    internal fun typed(got: String) {
        val st = stats(prefs.letters)
        val v = st.getOrPut(letter) { intArrayOf(0, 0) }
        if (got == letter) {
            v[0]++; prefs.letters = statsLine(st); prefs.popped = prefs.popped + 1
            sounds.drop(if (prefs.dropKind == "off") "off" else "drip")
            n++; miss = 0; note.text = ""
            bubble.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(320).start()
            handler.postDelayed({
                if (n < ROUND) { letter = next(level(prefs.popped), stats(prefs.letters), letter, Math.random()); paint() }
                else { Coach.reset(); bubble.text = "★"; bubble.alpha = 1f; bubble.scaleX = 1f; bubble.scaleY = 1f; note.setTextColor(ui.accent); note.text = "$ROUND пузырей! +1 капля — в пруду ${Pond.add(prefs)}"; count.text = "" }
            }, 350)
        } else {
            v[1]++; prefs.letters = statsLine(st); miss++
            note.text = "Это «$got». Пузырь «$letter» — путь на звезде подсвечен."
            bubble.animate().translationX(12f).setDuration(80).withEndAction { bubble.animate().translationX(0f).setDuration(80).start() }.start()
            aim()
        }
    }
}
