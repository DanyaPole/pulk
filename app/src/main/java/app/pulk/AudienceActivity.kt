package app.pulk

import android.app.Activity
import android.os.Bundle

/** «Кому и как помогает»: one screen, a card per group — what it gives and how to set it up. */
class AudienceActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ui = Ui(this)
        val body = ui.column()
        body.addView(ui.title("Кому и как помогает"))
        body.addView(ui.note("Пульк сделан для всех, кому обычная клавиатура неудобна. Вот что он даёт и как его настроить."))
        for (g in Audience.GROUPS) {
            body.addView(ui.h(g.title))
            body.addView(ui.card(*g.gives.map { ui.text("• $it", 15f) }.toTypedArray()))
            body.addView(ui.text("Как включить", 15f, ui.ink, true))
            for (h in g.how) body.addView(ui.note("→ $h"))
        }
        body.addView(ui.card(ui.text(Audience.NOTE, 15f)))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Понятно", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }
}
