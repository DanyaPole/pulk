package app.pulk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import java.util.Locale

/**
 * «Тренировки по ситуациям»: choose a theme (просьбы, самочувствие, чувства, окружение, знакомство, ответы),
 * then four short scenes. Type or choose the answer; it is said aloud. Calm, predictable, nothing wrong.
 */
class DrillsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    private val handler = Handler(Looper.getMainLooper())
    internal var theme = -1
    internal var at = 0
    internal val lastSaid get() = voice.last

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this)
        voice = Voice(this, prefs)
        menu()
    }
    override fun onDestroy() { voice.shutdown(); sounds.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun say(t: String) = voice.say(t)

    private val sounds by lazy { Feedback(this, prefs) }

    /** The themes on offer: the person's own phrases become a theme of their own; «Игра» opens in the pond. */
    internal fun themes(): List<Drills.Theme> {
        val own = prefs.myPhrases.take(6).map { p -> Drills.Task("Скажи: «$p»", p, listOf(p.lowercase().take(4)), listOf(p)) }
        return (if (own.isNotEmpty()) listOf(Drills.Theme("Моё", "Свои фразы и любимые слова", own)) else emptyList()) +
            Drills.THEMES + (if (Pond.playOpen(prefs)) listOf(Drills.PLAY) else emptyList())
    }

    /** A theme keeps its own mark whatever comes before it in the list. */
    private fun bitOf(t: Drills.Theme) = when (t.title) { "Моё" -> 20; Drills.PLAY.title -> 21; else -> Drills.THEMES.indexOfFirst { it.title == t.title } }

    internal fun menu() {
        handler.removeCallbacksAndMessages(null)   // a scene still waiting to move on does not come after the menu
        theme = -1
        val body = ui.column()
        body.addView(ui.title("Тренировки"))
        body.addView(ui.button("Мой пруд: ${prefs.drops} капель") { startActivity(android.content.Intent(this, PondActivity::class.java)) })
        body.addView(ui.note("Короткие жизненные ситуации. Читаешь — отвечаешь: печатаешь или выбираешь. Телефон говорит твой ответ."))
        body.addView(ui.card(ui.text("Пузыри — игра на буквы", 20f, ui.ink, true), ui.note("Лопай пузыри звездой. Трудные буквы попадаются чаще. Без времени и без проигрыша."),
            ui.button("Играть", main = true) { startActivity(android.content.Intent(this, BubblesActivity::class.java)) }))
        themes().forEachIndexed { i, t ->
            val done = prefs.drillsDone and (1 shl bitOf(t)) != 0
            body.addView(ui.card(ui.text((if (done) "✓ " else "") + t.title, 20f, ui.ink, true), ui.note("${t.about} · ${t.tasks.size} ситуации"),
                ui.button(if (done) "Ещё раз" else "Начать") { theme = i; at = 0; scene() }))
        }
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Назад", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }

    internal fun scene() {
        val t = themes()[theme]
        if (at >= t.tasks.size) { finishTheme(); return }
        val task = t.tasks[at]
        var tries = 0
        var moving = false   // the answer is in and the next scene is on its way: more taps change nothing
        val body = ui.column(28)
        body.addView(ui.text(t.tasks.indices.joinToString(" ") { if (it < at) "●" else if (it == at) "◉" else "○" } + "   ${t.title} · ${at + 1} из ${t.tasks.size}", 17f, ui.muted))
        body.addView(ui.text(task.scene, 26f, ui.ink, true))
        val status = ui.text("", 18f, ui.accent, true)
        fun answer(a: String) {
            if (a.isBlank() || moving) return
            say(a)
            if (Drills.fits(a, task) || ++tries >= 2) {
                // about the scene — or, after a second try, the model is shown and the next scene comes anyway
                status.setTextColor(ui.accent)
                if (Drills.fits(a, task)) { val n = Pond.add(prefs); sounds.drop(if (prefs.dropKind == "off") "off" else "drip"); status.text = "Получилось. +1 капля — в пруду $n" }
                else status.text = "Можно и так: «${task.model}». Дальше."
                moving = true
                handler.postDelayed({ at++; scene() }, if (Drills.fits(a, task)) 1100 else 2400)
            } else {
                status.setTextColor(ui.muted); status.text = "Сказано «$a». Можно и так: «${task.model}»."
            }
        }
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; imeOptions = EditorInfo.IME_ACTION_SEND
            textSize = 24f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Напечатай ответ"
            setOnEditorActionListener { v, act, _ -> if (act == EditorInfo.IME_ACTION_SEND) { answer((v as EditText).text.toString().trim()); v.setText(""); true } else false }
        }
        body.addView(field)
        body.addView(ui.note("…или выбери:"))
        for (c in task.choices) body.addView(ui.button(c) { answer(c) })
        body.addView(status)
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сказать", main = true) { answer(field.text.toString().trim()); field.setText("") })
        bottom.addView(ui.button("К темам") { menu() })
        setContentView(ui.screen(body, bottom))
    }

    private fun finishTheme() {
        prefs.drillsDone = prefs.drillsDone or (1 shl bitOf(themes()[theme]))
        val body = ui.column(40)
        body.addView(ui.title("«${themes()[theme].title}» — получилось"))
        body.addView(ui.text("Эти фразы теперь можно говорить в любой момент в «Говорить»."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("К темам", main = true) { menu() })
        bottom.addView(ui.button("Готово") { finish() })
        setContentView(ui.screen(body, bottom))
    }
}
