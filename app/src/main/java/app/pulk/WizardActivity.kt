package app.pulk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

/**
 * The first start, step by step: which hand → «Начать сразу» or «Подстроить под человека» (five doors, see
 * [Comfort]) → switch the keyboard on → choose it → fit it to the hand (or later) → the first word.
 * Back from Android's settings the step checks itself. With [DOORS_ONLY] it asks the five questions again and ends.
 */
class WizardActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    companion object { const val DOORS_ONLY = "doors_only" }
    private var step = 0
    internal var door = -1                      // -1: the fork; 0..4: a question; 5: what is on now
    private val answers = LinkedHashMap<String, Int>()
    private val doorsOnly get() = intent.getBooleanExtra(DOORS_ONLY, false)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); prefs = Prefs(this); ui = Ui(this)
        if (doorsOnly) { step = 1; door = 0 }
        show()
    }

    override fun onResume() {
        super.onResume()
        // came back from Android's list of keyboards: move on by itself when the step is done
        if (step == 2 && Ui.enabled(this)) { step = 3; show() }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && step == 3 && Ui.selected(this)) { step = 4; show() }
    }

    private fun head(n: Int, title: String, body: LinearLayout) {
        body.addView(ui.note("Шаг ${n + 1} из 6"))
        body.addView(ui.title(title))
    }

    internal fun show() {
        val body = ui.column(40); val bottom = ui.bottomBar()
        when (step) {
            0 -> {
                head(0, "Какой рукой печатаешь?", body)
                body.addView(ui.note("Звезда встанет под большой палец этой руки. Поменять можно в настройках."))
                // two halves of the screen: the hand's own side is the button
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for ((v, label) in listOf("left" to "Левой", "right" to "Правой")) row.addView(Button(this).apply {
                    text = label; isAllCaps = false; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(ui.ink)
                    background = GradientDrawable().apply { cornerRadius = ui.px(20).toFloat(); setColor(ui.panel); setStroke(ui.px(2), ui.line) }
                    layoutParams = LinearLayout.LayoutParams(0, ui.px(300), 1f).apply { setMargins(ui.px(4), ui.px(16), ui.px(4), 0) }
                    setOnClickListener { prefs.hand = v; step = 1; show() }
                })
                body.addView(row)
            }
            1 -> when {
                door < 0 -> {   // the fork: most people go straight on; the rest get five questions, not a list of switches
                    head(1, "Как начнём?", body)
                    body.addView(ui.text("«Начать сразу» — обычные настройки, поменять можно потом в «Удобстве»."))
                    body.addView(ui.note("«Подстроить» — пять вопросов о том, что трудно и что мешает. Настраиваете для ребёнка или ученика — выберите это."))
                    bottom.addView(ui.button("Начать сразу", main = true) { prefs.doors = "quick"; step = 2; show() })
                    bottom.addView(ui.button("Подстроить под человека — 2 минуты") { door = 0; show() })
                }
                door < Comfort.DOORS.size -> {   // one question at a time, big answers; nothing is a diagnosis
                    val d = Comfort.DOORS[door]
                    body.addView(ui.note("Вопрос ${door + 1} из ${Comfort.DOORS.size}"))
                    body.addView(ui.title(d.title))
                    body.addView(ui.note(d.note))
                    for ((label, v) in d.answers) body.addView(ui.button(label) { answers[d.key] = v; door++; if (door == Comfort.DOORS.size) Comfort.apply(prefs, answers); show() }.apply { textSize = 19f; minHeight = ui.px(64) })
                    if (door > 0) bottom.addView(ui.button("← Назад") { door--; show() })
                }
                else -> {   // what is on now, in words
                    val on = Comfort.summary(prefs)
                    body.addView(ui.title("Готово — подстроено"))
                    body.addView(ui.text(if (on.isEmpty()) "Обычные настройки." else "Включено: ${on.joinToString(", ")}."))
                    if (answers["hands"] == 1 || answers["hands"] == 2 || answers["hands"] == Comfort.UNKNOWN)
                        body.addView(ui.note("Дальше — «Под твою руку»: 30 секунд, и Пульк узнает дрожь и длину смахов."))
                    bottom.addView(ui.button(if (doorsOnly) "Готово" else "Дальше", main = true) { if (doorsOnly) finishDoors() else { step = 2; show() } })
                    bottom.addView(ui.button("← Изменить ответы") { answers.clear(); door = 0; show() })
                }
            }
            2 -> {
                head(2, "Включи клавиатуру", body)
                body.addView(ui.text("Откроется список клавиатур Android. Включи в нём «Пульк» и вернись сюда — шаг засчитается сам."))
                body.addView(ui.note("Android предупредит, что клавиатура видит набранное. У Пулька нет доступа к интернету: набранное не покидает телефон."))
                bottom.addView(ui.button("Открыть список клавиатур", main = true) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
                if (Ui.enabled(this)) bottom.addView(ui.button("Уже включено — дальше") { step = 3; show() })
            }
            3 -> {
                head(3, "Выбери Пульк", body)
                body.addView(ui.text("В списке, который откроется, выбери «Пульк»."))
                bottom.addView(ui.button("Выбрать клавиатуру", main = true) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
                bottom.addView(ui.button("Дальше") { step = 4; show() })
            }
            4 -> {
                head(4, "Под твою руку — 30 секунд", body)
                body.addView(ui.text("Подержи палец неподвижно, потом смахни 16 раз, как тебе удобно. Клавиатура узнает длину смахов, дрожь и куда палец на самом деле ведёт."))
                bottom.addView(ui.button("Настроить", main = true) { step = 5; show(); startActivity(Intent(this, CalibrationActivity::class.java)) })
                bottom.addView(ui.button("Позже") { step = 5; show() })
            }
            else -> {
                head(5, "Первое слово", body)
                body.addView(ui.text("Набери «он»: смахни вверх-вправо — «о», потом вправо — «н». Ошибся — ничего страшного, просто ещё раз."))
                val field = EditText(this).apply { hint = "Здесь"; textSize = 24f; setTextColor(ui.ink); setHintTextColor(ui.muted) }
                val done = ui.text("", 18f, ui.accent, true)
                var tries = 0; var lastLen = 0
                val offer = ui.card(ui.text("Можно и без смахов: «Два касания» — коснись лепестка, потом буквы. Попробовать?"),
                    ui.button("Попробовать касаниями") { prefs.mode = "taps"; (it as Button).text = "✓ Включено — набери «он» касаниями" }).apply { visibility = android.view.View.GONE }
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val t = s.toString().trim()
                        if (Lessons.closeEnough(t, "он") && t.length >= 2) { done.text = "✓ Получилось! Дальше — в «Обучении»."; offer.visibility = android.view.View.GONE; return }
                        if (t.length < lastLen) tries++   // erased: one more try
                        lastLen = t.length
                        if (tries >= 3 && prefs.mode == "pulse") offer.visibility = android.view.View.VISIBLE
                    }
                })
                body.addView(field); body.addView(done); body.addView(offer)
                bottom.addView(ui.button("Готово", main = true) { finishWizard() })
            }
        }
        setContentView(ui.screen(body, bottom))
    }

    /** After the doors: speaking is hard → «Говорить» opens; an adult beside → their minute of reading on top of it. */
    private fun afterDoors() {
        val a = Comfort.decode(prefs.doors)
        if (a["speech"] == 1) startActivity(Intent(this, SpeakActivity::class.java))
        if (a["helper"] == 1) startActivity(Intent(this, ParentsActivity::class.java))
    }
    private fun finishDoors() { afterDoors(); finish() }

    /** Back out of the wizard: it was seen; it does not come back every time the app opens. */
    @Deprecated("Back skips the rest of the wizard")
    override fun onBackPressed() { finishWizard() }

    private fun finishWizard() {
        // a newcomer starts with the basic gestures; the others are offered after the lessons (or «Ещё» → «Новые жесты»)
        if (!prefs.calibrated && prefs.lessonsDone == 0 && !prefs.wizardDone) prefs.gestures = emptySet()
        val first = !prefs.wizardDone
        prefs.wizardDone = true
        if (first) afterDoors()
        finish()
    }
}
