package app.pulk

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Learning «Пульс» on the keyboard itself: a level is a few steps, the text field below is typed with Пульк,
 * and the keyboard shows the help right on the star. Help is a ladder (hint → example → hand in hand),
 * offered after a couple of tries, declinable, and it steps down after each success.
 */
class TutorialActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui   // the design system's colours, light or dark as the phone is
    private val bg get() = ui.bg
    private val panel get() = ui.panel
    private val line get() = ui.line
    private val fg get() = ui.ink
    private val muted get() = ui.muted
    private val accent get() = ui.accent
    private val accentFg get() = ui.onAccent
    private lateinit var voice: Feedback
    private val handler = Handler(Looper.getMainLooper())
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private var level = -1
    private var steps: List<Lessons.Step> = emptyList()
    private var i = 0
    private var help = 0
    private var misses = 0
    private var lock = false
    private var restore: Pair<String, Boolean>? = null   // sound and eyes-free before the last level

    private lateinit var root: LinearLayout
    private var field: EditText? = null
    private var status: TextView? = null
    private var offerRow: View? = null
    private var imeBanner: View? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); voice = Feedback(this, prefs); ui = Ui(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(24), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; addView(root) })
        menu()
    }

    override fun onResume() { super.onResume(); paintIme() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) paintIme() }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); endLevel(keep = false); voice.shutdown(); super.onDestroy() }
    // the level by ear borrows voice and «вслепую»: while the lesson is off screen (Home, a call) the keyboard
    // in other apps is the person's own again, and gets them back if Android closes Пульк meanwhile
    override fun onStop() { Coach.pause(); restore?.let { prefs.sound = it.first; prefs.blind = it.second }; super.onStop() }
    override fun onStart() { super.onStart(); Coach.resume(); if (restore != null) byEar() }

    @Deprecated("Back goes to the menu from a level")
    override fun onBackPressed() { if (level >= 0) { endLevel(keep = false); menu() } else @Suppress("DEPRECATION") super.onBackPressed() }

    // ---- the menu ----
    private fun menu() {
        level = -1; root.removeAllViews(); field = null
        root.addView(text("Обучение", 28f, fg, true))
        root.addView(text("Печатаешь самой клавиатурой Пульк, подсказки появляются прямо на звезде. Быстро — за один раз, или спокойно — по три-пять минут три дня.", 15f, muted))
        val done = prefs.lessonsDone
        // «Три дня» first: short sessions are the gentlest way in
        // «Толчки» levels only once «Толчки» are turned on: a newcomer sees one way to learn
        for (l in Lessons.COURSES[Lessons.THREE_DAYS] + Lessons.COURSES[0] + (if (prefs.mode == "jabs") Lessons.COURSES[1] else IntRange.EMPTY)) {
            val c = Lessons.courseOf(l)
            if (l == Lessons.COURSES[c].first) root.addView(text(when (c) { 0 -> "«Пульс» — буква за смах"; 1 -> "«Толчки» — слово за одно касание"; else -> "Три дня по чуть-чуть" }, 20f, fg, true).apply { setPadding(0, px(22), 0, 0) })
            if (l == Lessons.COURSES[c].first && c == Lessons.THREE_DAYS) root.addView(text(courseNote(), 14f, accent))
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(px(16), px(14), px(16), px(14))
                background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(panel); setStroke(px(1), line) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
            }
            val mark = if (done and (1 shl l) != 0) "  ✓" else ""
            card.addView(text((if (Lessons.dayOf(l) > 0) "" else "${l - Lessons.COURSES[c].first + 1}. ") + "${Lessons.LEVELS[l]}$mark", 18f, fg, true))
            card.addView(text(Lessons.ABOUT[l], 14f, muted))
            card.addView(button(if (done and (1 shl l) != 0) "Ещё раз" else "Начать", primary = l == firstUndone(Lessons.courseOf(l))) { startLevel(l) })
            root.addView(card)
        }
    }
    private fun firstUndone(course: Int) = Lessons.COURSES[course].firstOrNull { prefs.lessonsDone and (1 shl it) == 0 } ?: Lessons.COURSES[course].first

    /** «Три дня»: where the person is — today's sessions, or «tomorrow» after a day is done. */
    private fun courseNote(): String {
        val next = Lessons.COURSES[Lessons.THREE_DAYS].firstOrNull { prefs.lessonsDone and (1 shl it) == 0 } ?: return "✓ Три дня пройдены. Можно повторить любое занятие."
        val day = Lessons.dayOf(next)
        val today = java.time.LocalDate.now().toEpochDay()
        val start = prefs.course3Start
        return if (start < 0 || today >= start + day - 1) "Сегодня: «${Lessons.LEVELS[next]}» — ${Lessons.ABOUT[next]}"
        else "Сегодня всё сделано. Следующее занятие — завтра: привычка крепнет во сне."
    }

    // ---- a level ----
    private fun startLevel(l: Int) {
        endLevel(keep = false)
        level = l; steps = Lessons.steps(l, prefs.lang, prefs.left); i = 0; help = 0; misses = 0; lock = false
        Coach.active = true; Coach.help = 0; Coach.hideLabels = Lessons.isBlind(l); Coach.jabs = Lessons.isJabs(l)
        if (Lessons.isBlind(l)) {
            // the last level is typed by ear: voice and eyes-free mode, for this level only
            byEar()
            voice.say(if (prefs.lang == "ru") "Слушай. Сейчас без экрана." else "Listen. No screen now.")
        }
        build()
    }

    /** Voice and eyes-free mode for the level by ear; what was on before is kept in [restore]. */
    private fun byEar() {
        restore = prefs.sound to prefs.blind
        if (prefs.sound == "off") prefs.sound = "voice"
        prefs.blind = true
    }

    private fun endLevel(keep: Boolean) {
        Coach.reset()
        val r = restore ?: return
        restore = null
        if (!keep) { prefs.sound = r.first; prefs.blind = r.second }
    }

    private fun build() {
        root.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("", 14f, muted).apply { id = ID_STEP; setPadding(px(12), 0, 0, 0) })
        root.addView(top)
        root.addView(schedule().apply { id = ID_SCHEDULE })
        root.addView(text("", 19f, fg).apply { id = ID_SAY; setPadding(0, px(14), 0, px(4)) })
        root.addView(text("", 44f, accent, true).apply { id = ID_TARGET; gravity = Gravity.CENTER })
        status = text("", 15f, muted).apply { minHeight = px(44) }
        root.addView(status)
        imeBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(14), px(10), px(14), px(10))
            background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(panel); setStroke(px(1), accent) }
            addView(text("Сейчас включена другая клавиатура. Для урока нужна Пульк.", 14f, fg))
            addView(button("Выбрать Пульк", primary = true) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
        }
        root.addView(imeBanner)
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(fg); textSize = 24f; setHintTextColor(muted); hint = "Печатай здесь"
            background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(panel); setStroke(px(1), line) }
            setPadding(px(14), px(12), px(14), px(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onTyped(s?.toString() ?: "") }
            })
        }
        root.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        // two rows of two, full width: every button is a big target, «Меню» at the bottom under the thumb
        fun pair(a: Button, b: Button) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (x in listOf(a, b)) addView(x, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(3), px(8), px(3), 0) })
        }
        root.addView(pair(button("Подсказать", small = true) { moreHelp() }, button("Послушать", small = true) { listen() }))
        root.addView(pair(button("Пропустить", small = true) { advance(success = false) }, button("Меню", small = true) { endLevel(keep = false); menu() }))
        offerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; setPadding(0, px(6), 0, 0)
            addView(text("Подсказать, как?", 15f, fg).apply { setPadding(0, 0, px(10), 0) })
            addView(button("Да", primary = true, small = true) { moreHelp() })
            addView(button("Не сейчас", small = true) { prefs.helpNo = prefs.helpNo or (1 shl level); offerRow?.visibility = View.GONE })
        }
        root.addView(offerRow)
        paintStep(); paintIme()
        field?.requestFocus()
        handler.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }, 250)
    }

    /** The visual schedule: where in the level and in the course the learner is. */
    private fun schedule(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(10), 0, 0); gravity = Gravity.CENTER_VERTICAL }
        for (s in steps.indices) {
            val d = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (s < i) accent else panel); setStroke(px(2), if (s <= i) accent else line) }
                layoutParams = LinearLayout.LayoutParams(px(14), px(14)).apply { rightMargin = px(8) }
            }
            row.addView(d)
        }
        row.addView(text((if (Lessons.dayOf(level) > 0) "занятие ${level - 7} из 6" else "уровень ${level % Lessons.PER_COURSE + 1} из ${Lessons.PER_COURSE}"), 13f, muted).apply { setPadding(px(8), 0, 0, 0) })
        return row
    }

    private fun paintStep() {
        val st = steps.getOrNull(i) ?: return
        findViewById<TextView>(ID_STEP).text = "${Lessons.LEVELS[level]} · ${i + 1}/${steps.size}" + if (help > 0) " · ${Lessons.HELP_NAMES[help]}" else ""
        findViewById<TextView>(ID_SAY).text = st.say
        findViewById<TextView>(ID_TARGET).text = when { st.want == " " -> "␣"; st.want != null -> st.want; else -> st.word }
        val sch = findViewById<View>(ID_SCHEDULE); val idx = root.indexOfChild(sch)
        root.removeView(sch); root.addView(schedule().apply { id = ID_SCHEDULE }, idx)
        updateCoach(field?.text?.toString() ?: "")
        if (Lessons.isBlind(level)) listen()
    }

    private fun updateCoach(text: String) {
        val st = steps.getOrNull(i) ?: return
        val nx = Lessons.next(st, text)
        Coach.next = nx; Coach.help = help
        Coach.hint = if (help >= 1 && nx != null) "«${if (nx == " ") "пробел" else nx}»: ${Lessons.how(nx, prefs.lang, prefs.left, Lessons.isJabs(level))}" else null
        Coach.changed()
        if (help >= 1) status?.apply { setTextColor(fg); this.text = Coach.hint ?: "Можно стереть ⌫ — или продолжай, одна ошибка не страшна." }
    }

    private fun onTyped(text: String) {
        if (lock) return
        val st = steps.getOrNull(i) ?: return
        when (val v = Lessons.check(st, text)) {
            Lessons.Verdict.Done -> advance(success = true)
            Lessons.Verdict.Going -> updateCoach(text)
            is Lessons.Verdict.Off -> {
                misses++
                val got = if (v.got == " ") "пробел" else "«${v.got}»"
                status?.apply { setTextColor(muted); this.text = "Вышло $got. Ещё раз — без спешки." }
                if (st.want != null) { lock = true; field?.setText(""); lock = false }
                updateCoach(field?.text?.toString() ?: "")
                if (misses >= 2 && help < 3 && prefs.helpNo and (1 shl level) == 0) offerRow?.visibility = View.VISIBLE
            }
        }
    }

    private fun moreHelp() {
        offerRow?.visibility = View.GONE
        if (help < 3) help++
        misses = 0
        findViewById<TextView>(ID_STEP).text = "${Lessons.LEVELS[level]} · ${i + 1}/${steps.size} · ${Lessons.HELP_NAMES[help]}"
        updateCoach(field?.text?.toString() ?: "")
        if (help == 3) status?.text = "Веди палец примерно туда — Пульк поможет попасть."
    }

    private fun listen() {
        val st = steps.getOrNull(i) ?: return
        voice.say(st.word ?: if (st.want == " ") "пробел" else st.want ?: "", 1.0f)
    }

    private fun advance(success: Boolean) {
        lock = true
        if (success) {
            val n = Pond.add(prefs)
            status?.apply { setTextColor(accent); text = "Получилось! +1 капля — в пруду $n" }
            if (help > 0) help-- // help steps down by itself
        }
        offerRow?.visibility = View.GONE
        misses = 0
        handler.postDelayed({
            i++
            field?.setText(""); lock = false
            if (i >= steps.size) levelDone() else { status?.text = ""; paintStep() }
        }, if (success) 650 else 100)
    }

    private fun levelDone() {
        prefs.lessonsDone = prefs.lessonsDone or (1 shl level)
        if (Lessons.dayOf(level) > 0 && prefs.course3Start < 0) prefs.course3Start = java.time.LocalDate.now().toEpochDay()
        Coach.next = null; Coach.hint = null; Coach.changed()
        val l = level
        root.removeAllViews(); field = null
        root.addView(text("«${Lessons.LEVELS[l]}» — получилось!", 26f, fg, true))
        if (Lessons.isBlind(l)) {
            root.addView(text("Ты печатаешь, не глядя на звезду. Звук и режим «вслепую» можно оставить: в нём касание в любом месте — пробел, звезда крупнее.", 15f, muted))
            root.addView(button("Оставить звук и «вслепую»", primary = true) { endLevel(keep = true); menu() })
            root.addView(button("Вернуть как было") { endLevel(keep = false); menu() })
        } else if (Lessons.dayOf(l) > 0) {
            // short sessions: a pause is part of learning, so the next one is not pushed
            root.addView(text(if (l % 2 == 0) "Отдохни. Вечернее занятие — когда будет пара минут." else "На сегодня всё. Завтра — следующий день: привычка крепнет во сне.", 15f, muted))
            root.addView(button("Готово", primary = true) { endLevel(keep = false); menu() })
            root.addView(button("Продолжить сейчас") { startLevel(l + 1) })
        } else {
            offerGestures(l)
            root.addView(text("Дальше: «${Lessons.LEVELS[l + 1]}». ${Lessons.ABOUT[l + 1]}", 15f, muted))
            root.addView(button("Дальше", primary = true) { startLevel(l + 1) })
            root.addView(button("В меню") { endLevel(keep = false); menu() })
        }
        level = -1
    }

    /** After a «Пульс» level: the gestures that fit what was just learned, offered (never switched on by themselves). */
    private fun offerGestures(l: Int) {
        val (keys, what) = when (l) {
            0 -> setOf("space") to "смах по пробелу: наружу — точка, внутрь — запятая, вниз по дуге — стереть слово, вверх — лучшая подсказка"
            1 -> setOf("hold") to "задержи палец в конце смаха: е → ё, ь → ъ, ? → !"
            2 -> setOf("cursor", "bkspword") to "удержи пробел и веди — курсор; смах по ⌫ к звезде — стереть слово"
            else -> return
        }
        if (prefs.gestures.containsAll(keys)) return
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(14), px(10), px(14), px(10))
            background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(panel); setStroke(px(1), accent) } }
        card.addView(text("Новое: $what. Включить?", 16f, fg))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Включить", primary = true, small = true) { prefs.gestures = prefs.gestures + keys; card.removeAllViews(); card.addView(text("✓ Включено", 16f, accent, true)) })
        row.addView(button("Не сейчас", small = true) { root.removeView(card) })
        card.addView(row); root.addView(card)
    }

    private fun paintIme() {
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        imeBanner?.visibility = if (cur.startsWith("$packageName/")) View.GONE else View.VISIBLE
    }

    // ---- small builders ----
    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); setLineSpacing(0f, 1.15f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, px(6), 0, px(6))
    }
    private fun button(s: String, primary: Boolean = false, small: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = if (small) 14f else 16f
        setTextColor(if (primary) accentFg else fg)
        background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(if (primary) accent else panel); setStroke(px(1), if (primary) accent else line) }
        setPadding(px(14), 0, px(14), 0); minHeight = px(48); minimumHeight = px(48)
        layoutParams = LinearLayout.LayoutParams(if (small) -2 else -1, -2).apply { topMargin = px(8); rightMargin = if (small) px(8) else 0 }
        setOnClickListener { onClick() }
    }

    companion object {
        private const val ID_STEP = 0x7f0a0001
        private const val ID_SCHEDULE = 0x7f0a0002
        private const val ID_SAY = 0x7f0a0003
        private const val ID_TARGET = 0x7f0a0004
    }
}
