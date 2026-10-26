package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Learning «Говорить» (as the web version): tiny steps, one new thing each, nothing can go wrong. Letters come
 * with the way lit on the star: first one straight stroke («а»), then a word of straight strokes («нет»), only
 * then a stroke with a turn («мама»). Only the letter shown is typed; a different one is not an error — it just
 * goes, the way is shown again, and after three tries Пульк types it itself, so nobody gets stuck.
 */
class SpeakLessonActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    private val handler = Handler(Looper.getMainLooper())
    internal var step = 0

    /** One step: its title, a line of how, what kind, what it wants. */
    internal data class Step(val title: String, val how: String, val kind: String, val want: String = "")
    internal val steps = listOf(
        Step("Коснись «Да»", "Телефон скажет «да».", "tap", "Да"),
        Step("Теперь коснись «Нет»", "", "tap", "Нет"),
        Step("Первая буква — «а»", "Смотри на звезду внизу: путь подсвечен. Проведи пальцем так же — вверх. Начинать можно где угодно на звезде.", "guide", "а"),
        Step("Слово «нет»", "Три буквы — три движения. Путь каждой буквы подсвечен.", "guide", "нет"),
        Step("Выбери «Хочу пить»", "", "pick", "Хочу пить"),
        Step("Слово «мама»", "Буква «м» — с поворотом: веди вправо и в конце поверни вверх. Путь подсвечен.", "guide", "мама"),
        Step("Покажи фразу маме", "Нажми «Показать» — фраза станет крупной.", "show", "Привет"),
        Step("Своё слово", "Напечатай что хочешь и нажми «Сказать».", "free"),
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
        Coach.reset()
        if (step >= steps.size) { done(); return }
        val st = steps[step]
        val body = ui.column(28)
        body.addView(schedule())
        if (step == 0) body.addView(ui.card(ui.note("Взрослому рядом: как помочь — на одну минуту чтения."),
            ui.button("Как помочь ребёнку") { startActivity(Intent(this, ParentsActivity::class.java)) }))
        body.addView(ui.text(st.title, 24f, ui.ink, true))
        if (st.how.isNotEmpty()) body.addView(ui.text(st.how, 17f, ui.muted))
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
            "guide" -> guide(body, st.want, status)
            "free" -> {
                val field = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEND
                    textSize = 28f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Печатай здесь"
                }
                fun sayIt() { val t = field.text.toString().trim(); if (t.isEmpty()) return; say(t); prefs.phrases = Phrases.remember(prefs.phrases, t); success(status) }
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

    /** The guided word: the keyboard lights the way of the next letter; only that letter stays in the field. */
    private fun guide(body: LinearLayout, word: String, status: TextView) {
        var i = 0; var miss = 0; var busy = false
        val target = ui.text("", 40f, ui.muted, true).apply { gravity = Gravity.CENTER }
        fun paintTarget() {
            val s = SpannableString(word)
            s.setSpan(ForegroundColorSpan(ui.accent), 0, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            target.text = s
        }
        fun aim() {
            val ch = word[i].toString()
            Coach.active = true; Coach.help = if (miss >= 2) 3 else 2; Coach.next = ch; Coach.traceField = GestureTraces.SPEAK_LESSON
            Coach.hint = "«$ch»: ${Lessons.how(ch, prefs.lang, prefs.left)}"; Coach.changed()
            paintTarget()
        }
        val field = EditText(this).apply {
            id = GestureTraces.SPEAK_LESSON
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 30f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = word
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (busy || i >= word.length) return
                val typed = (s?.toString() ?: "").lowercase().replace('ё', 'е')
                val good = word.substring(0, i + 1)
                when {
                    typed == good -> {   // the letter shown: next one, or the word is done
                        i++; miss = 0; status.text = ""
                        if (i == word.length) { Coach.reset(); paintTarget(); say(word); success(status) } else aim()
                    }
                    typed.length > i && typed.startsWith(word.substring(0, i)) -> {   // another letter: it goes, the way shows again
                        val got = typed.substring(i).trim()
                        busy = true; s?.replace(i, s.length, ""); busy = false
                        if (got.isEmpty()) return
                        if (++miss >= 3) {
                            status.setTextColor(ui.muted); status.text = "Я помог с буквой «${word[i]}». Дальше — ты."
                            field.append(word[i].toString())   // counted as typed by the watcher above
                        } else {
                            status.setTextColor(ui.muted); status.text = "Это «$got». Нужна «${word[i]}» — путь на звезде подсвечен."
                            aim()
                        }
                    }
                    typed != word.substring(0, i) -> {   // ⌫ or the cursor moved: not a try (as in the web), what was right comes back
                        busy = true; s?.replace(0, s.length, word.substring(0, i)); busy = false
                        field.setSelection(i); aim()
                    }
                }
            }
        })
        body.addView(target); body.addView(field)
        aim(); field.requestFocus()
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
        val at = step
        handler.postDelayed({ if (step == at) { step++; show() } }, 1100)
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
        bottom.addView(ui.button("Как помочь ребёнку") { startActivity(Intent(this, ParentsActivity::class.java)) })
        setContentView(ui.screen(body, bottom))
    }
}
