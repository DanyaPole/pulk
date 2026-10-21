package app.pulk

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/**
 * For the grown-up who sets «Говорить» up: «Про меня» (said and shown with one touch) and the child's own
 * phrases — names of family, favourite things, «я хочу…». Own words matter more than any ready ones.
 */
class MyPhrasesActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = Prefs(this); ui = Ui(this); build() }

    private fun field(hint: String, value: String, lines: Int = 1) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if (lines > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
        textSize = 20f; setTextColor(ui.ink); setHintTextColor(ui.muted); this.hint = hint; setText(value); minLines = lines
    }

    private fun build() {
        val body = ui.column()
        body.addView(ui.title("Мои фразы"))
        body.addView(ui.note("Для взрослого, который помогает. Свои слова ребёнку важнее готовых: имена близких, любимые вещи, «я хочу…», «мне не нравится…»."))

        body.addView(ui.h("Про меня"))
        body.addView(ui.note("Говорится и показывается крупно одним касанием — для новых людей."))
        val about = field("Меня зовут … Я не говорю, но всё понимаю. Я отвечу, напечатав.", prefs.aboutMe, 3)
        body.addView(about)

        body.addView(ui.h("Фразы"))
        val add = field("Новая фраза, например «Мама, я хочу домой»", "")
        body.addView(add)
        body.addView(ui.button("Добавить") {
            val t = add.text.toString().trim(); if (t.isEmpty()) return@button
            prefs.myPhrases = (prefs.myPhrases + t).distinct(); add.setText(""); paint()
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list); paint()

        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сохранить", main = true) { prefs.aboutMe = about.text.toString().trim(); finish() })
        setContentView(ui.screen(body, bottom))
    }

    private fun paint() {
        list.removeAllViews()
        if (prefs.myPhrases.isEmpty()) list.addView(ui.note("Пока пусто. Добавленные фразы появятся в «Говорить» первым разделом — «Мои»."))
        for (p in prefs.myPhrases) list.addView(ui.button("✕  $p") { prefs.myPhrases = prefs.myPhrases - p; paint() })
    }
}
