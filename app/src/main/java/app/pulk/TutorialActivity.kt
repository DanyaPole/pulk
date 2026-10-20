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
    private val bg = Color.parseColor("#0D1215")
    private val panel = Color.parseColor("#141B1F")
    private val line = Color.parseColor("#29343A")
    private val fg = Color.parseColor("#E4EBEE")
    private val muted = Color.parseColor("#8B9AA2")
    private val accent = Color.parseColor("#55BAD7")
    private val accentFg = Color.parseColor("#06222B")
    private lateinit var prefs: Prefs
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
        prefs = Prefs(this); voice = Feedback(this, prefs)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(24), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; addView(root) })
        menu()
    }

    override fun onResume() { super.onResume(); paintIme() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) paintIme() }

    override fun onDestroy() { endLevel(keep = false); voice.shutdown(); super.onDestroy() }

    @Deprecated("Back goes to the menu from a level")
    override fun onBackPressed() { if (level >= 0) { endLevel(keep = false); menu() } else @Suppress("DEPRECATION") super.onBackPressed() }

    // ---- the menu ----
    private fun menu() {
        level = -1; root.removeAllViews(); field = null
        root.addView(text("Обучение «Пульсу»", 28f, fg, true))
        root.addView(text("Четыре коротких уровня, каждый — пара минут. Печатаешь самой клавиатурой Пульк, подсказки появляются прямо на звезде.", 15f, muted))
        val done = prefs.lessonsDone
        for (l in Lessons.LEVELS.indices) {
            if (l % Lessons.PER_COURSE == 0) root.addView(text(if (Lessons.isJabs(l)) "«Толчки» — слово за одно касание" else "«Пульс» — буква за смах", 20f, fg, true).apply { setPadding(0, px(22), 0, 0) })
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(px(16), px(14), px(16), px(14))
                background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(panel); setStroke(px(1), line) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) }
            }
            val mark = if (done and (1 shl l) != 0) "  ✓" else ""
            card.addView(text("${l % Lessons.PER_COURSE + 1}. ${Lessons.LEVELS[l]}$mark", 18f, fg, true))
            card.addView(text(Lessons.ABOUT[l], 14f, muted))
            card.addView(button(if (done and (1 shl l) != 0) "Ещё раз" else "Начать", primary = l == firstUndone(Lessons.courseOf(l))) { startLevel(l) })
            root.addView(card)
        }
    }
    private fun firstUndone(course: Int) = (course * Lessons.PER_COURSE until (course + 1) * Lessons.PER_COURSE).firstOrNull { prefs.lessonsDone and (1 shl it) == 0 } ?: course * Lessons.PER_COURSE

    // ---- a level ----
    private fun startLevel(l: Int) {
        endLevel(keep = false)
        level = l; steps = Lessons.steps(l, prefs.lang, prefs.left); i = 0; help = 0; misses = 0; lock = false
        Coach.active = true; Coach.help = 0; Coach.hideLabels = Lessons.isBlind(l); Coach.jabs = Lessons.isJabs(l)
        if (Lessons.isBlind(l)) {
            // the last level is typed by ear: voice and eyes-free mode, for this level only
            restore = prefs.sound to prefs.blind
            if (prefs.sound == "off") prefs.sound = "voice"
            prefs.blind = true
            voice.say(if (prefs.lang == "ru") "Слушай. Сейчас без экрана." else "Listen. No screen now.")
        }
        build()
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
        top.addView(button("← Меню", small = true) { endLevel(keep = false); menu() }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = 0 })
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
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(10), 0, 0) }
        row.addView(button("Подсказать", small = true) { moreHelp() })
        row.addView(button("Послушать", small = true) { listen() })
        row.addView(button("Пропустить", small = true) { advance(success = false) })
        root.addView(row)
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
        row.addView(text("уровень ${level % Lessons.PER_COURSE + 1} из ${Lessons.PER_COURSE}", 13f, muted).apply { setPadding(px(8), 0, 0, 0) })
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
            status?.apply { setTextColor(accent); text = "Получилось!" }
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
        Coach.next = null; Coach.hint = null; Coach.changed()
        val l = level
        root.removeAllViews(); field = null
        root.addView(text("«${Lessons.LEVELS[l]}» — получилось!", 26f, fg, true))
        if (Lessons.isBlind(l)) {
            root.addView(text("Ты печатаешь, не глядя на звезду. Звук и режим «вслепую» можно оставить: в нём касание в любом месте — пробел, звезда крупнее.", 15f, muted))
            root.addView(button("Оставить звук и «вслепую»", primary = true) { endLevel(keep = true); menu() })
            root.addView(button("Вернуть как было") { endLevel(keep = false); menu() })
        } else {
            root.addView(text("Дальше: «${Lessons.LEVELS[l + 1]}». ${Lessons.ABOUT[l + 1]}", 15f, muted))
            root.addView(button("Дальше", primary = true) { startLevel(l + 1) })
            root.addView(button("В меню") { endLevel(keep = false); menu() })
        }
        level = -1
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
