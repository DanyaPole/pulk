package app.pulk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Learning «Говорить», made for a child (and anyone) for whom predictability matters: six tiny steps, one thing
 * on the screen at a time, a visible schedule, the same words every time. Nothing is ever wrong: whatever is
 * typed is said; a step just waits, and help is offered, not forced. Success is calm: «Получилось».
 */
class SpeakLessonActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    private val handler = Handler(Looper.getMainLooper())
    internal var step = 0
    private var tries = 0

    /** One step: what to do, in one sentence, and what shows on the screen for it. */
    internal data class Step(val say: String, val kind: String, val want: String = "")
    internal val steps = listOf(
        Step("Коснись «Да». Телефон скажет «да».", "tap", "Да"),
        Step("Теперь коснись «Нет».", "tap", "Нет"),
        Step("Напечатай «мама» и нажми ⏎ — стрелку справа внизу.", "type", "мама"),
        Step("Выбери фразу «Хочу пить».", "pick", "Хочу пить"),
        Step("Покажи фразу маме: нажми «Показать».", "show", "Привет"),
        Step("Теперь скажи что-нибудь своё. Любое.", "free"),
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this)
        voice = Voice(this, prefs)
        show()
    }
    override fun onDestroy() { Coach.reset(); voice.shutdown(); sounds.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onStop() { Coach.pause(); super.onStop() }
    override fun onStart() { super.onStart(); Coach.resume() }

    internal val lastSaid get() = voice.last
    private fun say(t: String) = voice.say(t)

    /** The schedule: where we are, as dots of two shapes and words (not by colour alone). */
    private fun schedule() = ui.text(steps.indices.joinToString(" ") { if (it < step) "●" else if (it == step) "◉" else "○" } + "   шаг ${step + 1} из ${steps.size}", 18f, ui.muted)

    internal fun show() {
        tries = 0
        if (step >= steps.size) { done(); return }
        val st = steps[step]
        val body = ui.column(32)
        body.addView(schedule())
        body.addView(ui.text(st.say, 24f, ui.ink, true))
        val status = ui.text("", 18f, ui.accent, true)
        val bottom = ui.bottomBar()
        when (st.kind) {
            "tap" -> {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (w in listOf("Да", "Нет").take(if (step == 0) 1 else 2)) row.addView(ui.button(w, main = w == st.want) { say(w); if (w == st.want) success(status) }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ui.px(120), 1f).apply { setMargins(ui.px(4), ui.px(16), ui.px(4), 0) }; textSize = 28f
                })
                body.addView(row)
            }
            "type", "free" -> {
                val field = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEND
                    textSize = 28f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = if (st.kind == "type") "мама" else "Печатай здесь"
                }
                fun sayIt() {
                    val t = field.text.toString().trim(); if (t.isEmpty()) return
                    say(t)
                    if (st.kind == "free" || Lessons.closeEnough(t, st.want)) success(status)
                    else {
                        // nothing is wrong: it was said; the step just waits, and after two tries help is offered
                        tries++; field.setText("")
                        status.setTextColor(ui.muted); status.text = "Сказано «$t». Попробуй «${st.want}»."
                        if (tries >= 2 && !Coach.active) offerHelp(body, st.want)
                    }
                }
                field.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { sayIt(); true } else false }
                body.addView(field)
                bottom.addView(ui.button("Сказать", main = true) { sayIt() })
                field.requestFocus()
            }
            "pick" -> for (p in listOf("Хочу есть", "Хочу пить", "Мне холодно")) body.addView(ui.button(p) { say(p); if (p == st.want) success(status) })
            "show" -> {
                body.addView(ui.text(st.want, 32f, ui.ink, true).apply { gravity = Gravity.CENTER })
                bottom.addView(ui.button("Показать", main = true) { partner(st.want) { success(status) } })
            }
        }
        body.addView(status)
        bottom.addView(ui.button("Выйти") { Coach.reset(); finish() })
        setContentView(ui.screen(body, bottom))
    }

    /** Help is a question, not a correction: yes shows the way on the star, no is remembered as fine. */
    private fun offerHelp(body: LinearLayout, word: String) {
        if (body.findViewWithTag<View>("help") != null) return
        val card = ui.card(ui.text("Показать, как набрать «$word»?", 18f),
            ui.button("Да, показать") {
                Coach.active = true; Coach.help = 2; Coach.next = word.first().toString()
                Coach.hint = "«${word.first()}»: ${Lessons.how(word.first().toString(), prefs.lang, prefs.left)}"; Coach.changed()
                body.findViewWithTag<View>("help")?.visibility = View.GONE
            },
            ui.button("Не сейчас") { body.findViewWithTag<View>("help")?.visibility = View.GONE }).apply { tag = "help" }
        body.addView(card)
    }

    /** The phrase big, for the person opposite. */
    private fun partner(text: String, then: () -> Unit) {
        val body = ui.column(40)
        body.addView(ui.text(text, 56f, ui.ink, true).apply { gravity = Gravity.CENTER })
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Вернуться", main = true) { then() })
        setContentView(ui.screen(body, bottom))
    }

    /** Calm success: one word, then the next step on its own after a short pause. */
    private fun success(status: TextView) {
        Coach.reset()
        val n = Pond.add(prefs)
        status.setTextColor(ui.accent); status.text = "Получилось. +1 капля — в пруду $n"
        drop()
        handler.postDelayed({ step++; show() }, 1100)
    }

    private val sounds by lazy { Feedback(this, prefs) }
    private fun drop() = sounds.drop(if (prefs.dropKind == "off") "off" else "drip")

    private fun done() {
        prefs.speakLessonDone = true
        val body = ui.column(40)
        body.addView(ui.title("Ты умеешь говорить с Пульком"))
        body.addView(ui.text("Печатай — телефон скажет. ⏎ — сказать. Быстрые ответы — одним касанием. «Показать» — крупно для собеседника."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Готово", main = true) { finish() })
        bottom.addView(ui.button("Ещё раз") { step = 0; show() })
        setContentView(ui.screen(body, bottom))
    }
}
