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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
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
    private fun wordMode() = prefs.mode in listOf("jabs", "petals")
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    internal val sounds by lazy { Feedback(this, prefs) }
    internal var letter = ""
    internal var n = 0
    internal var words: BubbleWords.Progress? = null
    private var miss = 0
    private var roundLevel = 0
    private var popping = false   // the bubble is bursting: the next letter is not on yet, more typing counts nothing
    private var foreground = false
    private lateinit var bubble: TextView
    internal lateinit var guidance: TextView
    private lateinit var note: TextView
    private lateinit var count: TextView
    internal lateinit var field: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); prefs = Prefs(this); ui = Ui(this)
        val access = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        sounds.exploring = { access.isTouchExplorationEnabled }
        sounds.announcer = { bubble.announceForAccessibility(it) }
        start()
    }
    override fun onDestroy() { Coach.reset(); sounds.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onStop() { foreground = false; Coach.pause(); if (words != null) sounds.shutdown(); super.onStop() }
    override fun onStart() { super.onStart(); foreground = true; Coach.resume(); speakWord() }

    private fun start() {
        handler.removeCallbacksAndMessages(null)
        n = 0; miss = 0; popping = false
        words = null
        val lv = level(prefs.popped); roundLevel = lv
        val body = ui.column(28)
        body.addView(ui.title("Пузыри"))
        val instructions = ui.text(if (prefs.mode == "petals") "Толкай к лепестку нужной буквы и назад. Можно поднимать палец. Пузырь ждёт."
            else if (wordMode()) "Слово толчками: к букве и назад. Можно поднимать палец. Пузырь ждёт."
            else listOf("Крупные буквы: прямой смах.", "Добавились буквы у центра: туда и обратно.", "Все буквы: и с поворотом.")[lv] + " Пузырь ждёт — спешить некуда.", 17f, ui.muted)
        count = ui.text("", 18f, ui.muted); body.addView(count)
        bubble = TextView(this).apply {
            textSize = if (wordMode()) 36f else 64f; setTypeface(typeface, Typeface.BOLD); setTextColor(ui.ink); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ui.panel); setStroke(ui.px(3), ui.accent) }
            accessibilityLiveRegion = if (wordMode()) android.view.View.ACCESSIBILITY_LIVE_REGION_NONE else android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            layoutParams = LinearLayout.LayoutParams(ui.px(140), ui.px(140)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.px(16) }
        }
        val target = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = if (prefs.left) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
        }
        target.addView(bubble)
        guidance = ui.text("", 17f).apply {
            background = GradientDrawable().apply { cornerRadius = ui.px(16).toFloat(); setColor(ui.panel); setStroke(ui.px(2), ui.ink) }
            setPadding(ui.px(12), ui.px(8), ui.px(12), ui.px(8))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.px(12) }
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        target.addView(guidance); body.addView(target)
        body.addView(instructions)
        note = ui.text("", 17f, ui.muted).apply { accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }; body.addView(note)
        field = EditText(this).apply { id = GestureTraces.BUBBLES; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; textSize = 22f; hint = "Печатай звездой здесь"; setTextColor(ui.ink); setHintTextColor(ui.muted) }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim()?.lowercase() ?: ""
                if (t.isEmpty()) { if (words != null && !s.isNullOrEmpty()) s.clear(); return }
                s?.clear()   // the field only carries one letter at a time
                if (words != null) t.forEach { typed(it.toString()) } else typed(t.last().toString())
            }
        })
        body.addView(field)
        val bottom = ui.bottomBar()
        if (wordMode()) {
            body.addView(ui.onOff("Помощь после промахов", { prefs.bubblesHelp }) { prefs.bubblesHelp = it; aim() })
            body.addView(ui.note("Подсказка → медленный пример → после третьей попытки буква за тебя. Помощь можно отключить."))
            body.addView(ui.note(if (prefs.sound == "voice") "Пузырь произносит слово." else "Голос выключен. Его можно выбрать в настройках звука."))
            bottom.addView(ui.button("Повторить слово") { speakWord() })
        }
        bottom.addView(ui.button("К тренировкам", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
        choose(""); paint(); field.requestFocus()
    }

    private fun choose(prev: String) {
        letter = if (wordMode()) BubbleWords.next(level(prefs.popped), stats(prefs.letters), prev, Math.random())
            else next(level(prefs.popped), stats(prefs.letters), prev, Math.random())
        words = if (wordMode()) BubbleWords.Progress(letter) else null
        speakWord()
    }
    private fun speakWord() { if (foreground && words != null && n < ROUND && prefs.sound == "voice") sounds.say(letter) }
    private fun wordPaint() {
        val w = words ?: return
        bubble.text = SpannableString(letter).apply { if (w.done > 0) {
            setSpan(ForegroundColorSpan(ui.accent), 0, w.done, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), 0, w.done, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } }
        bubble.contentDescription = "$letter. Набрано ${w.done} из ${letter.length}" + (w.next?.let { ". Дальше «$it»" } ?: "")
    }

    private fun paint() {
        count.text = (0 until ROUND).joinToString("") { if (it < n) "●" else "○" } + "   $n из $ROUND"
        bubble.text = letter; bubble.scaleX = 1f; bubble.scaleY = 1f; bubble.alpha = 1f
        if (words != null) { wordPaint(); aim(); return }
        // at first the way is lit at once; later only after a miss
        aim()
    }
    private fun aim() {
        if (n >= ROUND) { Coach.reset(); showHint(); return }
        Coach.traceField = GestureTraces.BUBBLES
        words?.let { w ->
            Coach.active = true; Coach.jabs = true; Coach.bubbles = true; Coach.next = w.next
            Coach.petals = prefs.mode == "petals"
            Coach.help = if (!prefs.bubblesHelp || w.complete || (level(prefs.popped) > 0 && w.misses == 0)) 0 else 2
            Coach.example = prefs.bubblesHelp && w.misses >= 2
            Coach.hint = if (Coach.help > 0) w.next?.let {
                val label = if (Coach.petals) Pulse.table("ru", false).firstOrNull { row -> it in row }?.get(0) ?: it else it
                "«$it»: ${Lessons.how(label, "ru", prefs.left, true)}"
            } else null
            showHint()
            // The pop delay may finish after Home: save the new target without teaching or speaking in the background.
            if (foreground) Coach.changed() else Coach.pause()
            return
        }
        Coach.active = true; Coach.help = if (miss >= 2) 3 else if (level(prefs.popped) == 0 || miss > 0) 2 else 0; Coach.next = letter
        Coach.hint = if (Coach.help > 0) "«$letter»: ${Lessons.how(letter, "ru", prefs.left)}" else null; showHint(); Coach.changed()
    }

    private fun showHint() {
        guidance.text = Coach.hint.orEmpty()
        // Keep the bubble in place when help fades after success.
        guidance.visibility = if (Coach.hint == null) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    internal fun typed(got: String) {
        if (popping || n >= ROUND) return
        val st = stats(prefs.letters)
        val w = words
        val want = w?.next ?: letter
        val v = st.getOrPut(want) { intArrayOf(0, 0) }
        if (w != null) {
            val before = w.done
            val correct = w.add(got, prefs.bubblesHelp)
            v[if (correct) 0 else 1]++; prefs.letters = statsLine(st)
            note.text = if (correct) "" else if (w.done > before) "Вышло «$got». Вместе набрали «$want»." else
                "Вышло «$got», ещё раз — без спешки." + if (w.misses >= 2 && prefs.bubblesHelp) " Медленный пример на звезде." else ""
            if (w.done > before) {
                val table = Pulse.table("ru", false)
                val p = table.indexOfFirst { want in it }
                val k = table[p].indexOf(want)
                sounds.letter(Pulse.Slot(p, Pulse.KINDS[k], want), want, "ru", false)
            }
            wordPaint(); aim()
            if (!w.complete) return
        }
        if (w != null || got == letter) {
            if (w == null) { v[0]++; prefs.letters = statsLine(st) }
            prefs.popped = prefs.popped + 1
            if (prefs.mode == "jabs" && w != null) prefs.jabsWords++
            sounds.drop(if (prefs.dropKind == "off") "off" else "drip")
            n++; miss = 0; note.text = ""; popping = true
            bubble.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(320).start()
            handler.postDelayed({
                popping = false
                if (n < ROUND) { choose(letter); paint() }
                else {
                    // the round is over, as in the web: a drop, and «Ещё раунд» when the person wants it
                    Coach.reset(); showHint(); bubble.text = "★"; bubble.alpha = 1f; bubble.scaleX = 1f; bubble.scaleY = 1f; note.setTextColor(ui.accent); count.text = ""
                    if (words != null) bubble.contentDescription = "$ROUND слов. Раунд закончен."
                    note.text = "$ROUND пузырей! +1 капля — в пруду ${Pond.add(prefs)}" + if (level(prefs.popped) > roundLevel) ". Дальше — новые буквы." else ""
                    (note.parent as? android.view.ViewGroup)?.let { b -> b.addView(ui.button("Ещё раунд", main = true) { start() }, b.indexOfChild(note) + 1) }
                }
            }, 350)
        } else {
            v[1]++; prefs.letters = statsLine(st); miss++
            note.text = "Это «$got». Пузырь «$letter» — путь на звезде подсвечен."
            bubble.animate().translationX(12f).setDuration(80).withEndAction { bubble.animate().translationX(0f).setDuration(80).start() }.start()
            aim()
        }
    }
}
