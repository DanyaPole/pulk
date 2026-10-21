package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText

/**
 * «Проверка скорости»: three short phrases, typed with Пульк. Signs per minute, accuracy and how often the person
 * erased — the numbers the interface project asks for when testing on people. Results stay on the phone,
 * by mode, and can be sent by the person themself.
 */
class SpeedActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private val phrases = Speed.pick()
    private var i = 0
    private val runs = ArrayList<Speed.Run>()

    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = Prefs(this); ui = Ui(this); intro() }

    private fun intro() {
        val body = ui.column(40)
        body.addView(ui.title("Проверка скорости"))
        body.addView(ui.text("Три короткие фразы. Печатай как обычно — быстро, но без спешки. Ошибки можно исправлять."))
        body.addView(ui.note("Сейчас способ набора: ${Speed.modeName(prefs.mode)}. Сменить можно в настройках — и сравнить."))
        history(body)
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Начать", main = true) { phrase() })
        bottom.addView(ui.button("Назад") { finish() })
        setContentView(ui.screen(body, bottom))
    }

    private fun phrase() {
        val want = phrases[i]
        val body = ui.column(40)
        body.addView(ui.note("Фраза ${i + 1} из ${phrases.size}"))
        body.addView(ui.text(want, 26f, ui.ink, true))
        var start = 0L; var erases = 0; var last = 0
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 22f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Печатай здесь"; gravity = Gravity.TOP; minLines = 2
        }
        val bottom = ui.bottomBar()
        fun finishPhrase() {
            val typed = field.text.toString().trim()
            val ms = if (start == 0L) 0L else SystemClock.uptimeMillis() - start
            runs.add(Speed.Run(want, typed, ms, erases))
            i++
            if (i < phrases.size) phrase() else results()
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = s?.length ?: 0
                if (start == 0L && n > 0) start = SystemClock.uptimeMillis()
                if (n < last) erases++
                last = n
                // the phrase is there (one slip allowed): on to the next by itself
                if (n >= want.length && Lessons.closeEnough(s.toString().trim(), want)) field.post { finishPhrase() }
            }
        })
        body.addView(field)
        bottom.addView(ui.button("Готово", main = true) { finishPhrase() })
        setContentView(ui.screen(body, bottom))
        field.requestFocus()
    }

    private fun results() {
        val r = Speed.summary(runs, prefs.mode)
        prefs.speedHistory = (listOf(r.line()) + prefs.speedHistory).take(30)
        val body = ui.column(40)
        body.addView(ui.title("Получилось"))
        body.addView(ui.card(
            ui.text("${r.cpm} знаков в минуту", 24f, ui.ink, true),
            ui.text("Точность ${r.accuracy} %", 18f),
            ui.text("Стираний: ${r.erases}", 18f),
            ui.note("Способ набора: ${Speed.modeName(r.mode)}")))
        body.addView(ui.note("Для сравнения: одной рукой на обычной клавиатуре обычно 60–120 знаков в минуту, со свайпом — больше. Главное — как меняется у тебя от раза к разу."))
        history(body)
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Ещё раз", main = true) { i = 0; runs.clear(); phrase() })
        bottom.addView(ui.button("Отправить результаты") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Speed.report(prefs.speedHistory)), "Отправить"))
        })
        bottom.addView(ui.button("Готово") { finish() })
        setContentView(ui.screen(body, bottom))
    }

    private fun history(body: android.widget.LinearLayout) {
        val h = prefs.speedHistory
        if (h.isEmpty()) return
        body.addView(ui.h("Прошлые проверки"))
        for (line in h.take(8)) Speed.parse(line)?.let { body.addView(ui.note("${it.date} · ${Speed.modeName(it.mode)} · ${it.cpm} зн/мин · ${it.accuracy} %")) }
    }
}
