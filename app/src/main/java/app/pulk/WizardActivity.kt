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
 * The first start, step by step: which hand → what would help → switch the keyboard on → choose it →
 * fit it to the hand (or later) → the first word. Back from Android's settings the step checks itself.
 */
class WizardActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private var step = 0
    private val helps = HashSet<String>()

    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = Prefs(this); ui = Ui(this); show() }

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
            1 -> {
                // preferences, not diagnoses: nobody is asked what is wrong with them
                head(1, "Как тебе удобнее?", body)
                body.addView(ui.note("Выбери, что нравится, — или сразу «Как обычно». Всё можно поменять потом."))
                bottom.addView(ui.button("Как обычно", main = true) { step = 2; show() })
                for ((k, label) in listOf("sight" to "Крупнее и контрастнее", "taps" to "Без смахов — касаниями", "shake" to "Сгладить дрожание пальца",
                        "ear" to "На слух, не глядя", "simple" to "Попроще, без лишнего", "speak" to "Говорить с помощью телефона")) {
                    body.addView(ui.button(label) { b ->
                        if (!helps.add(k)) helps.remove(k)
                        (b as Button).text = if (k in helps) "✓ $label" else label
                    })
                }
                bottom.addView(ui.button("С выбранным — дальше") { applyHelps(); step = 2; show() })
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

    /** «Что поможет»: each answer turns on what is meant for it. */
    private fun applyHelps() {
        if ("shake" in helps && prefs.smooth == 0f) prefs.smooth = 0.55f          // until the calibration measures it
        if ("taps" in helps) prefs.mode = "taps"
        if ("sight" in helps) { prefs.size = maxOf(prefs.size, 112); prefs.theme = "contrast"; if (prefs.compass == "off") prefs.compass = "voice"; if (prefs.sound == "off") prefs.sound = "voice" }
        if ("simple" in helps) prefs.simple = true
        if ("speak" in helps) prefs.mode = "jabs"   // a word per touch: the closest to speaking; «Говорить» is on the home screen
        if ("ear" in helps) { if (prefs.sound == "off") prefs.sound = "voice"; if (prefs.compass == "off") prefs.compass = "voice" }
    }

    private fun finishWizard() {
        // a newcomer starts with the basic gestures; the others are offered after the lessons (or «Ещё» → «Новые жесты»)
        if (!prefs.calibrated && prefs.lessonsDone == 0 && !prefs.wizardDone) prefs.gestures = emptySet()
        prefs.wizardDone = true; finish()
    }
}
