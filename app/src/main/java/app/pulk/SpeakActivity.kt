package app.pulk

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * «Говорить»: for a person who can move but not speak. What is typed is said aloud by the phone (no internet),
 * ⏎ says it, a few letters bring a whole phrase, quick replies are one touch, and the text can be shown big
 * to the person opposite. Words can also be said one by one, as they are typed.
 */
class SpeakActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    private lateinit var field: EditText
    private lateinit var completions: LinearLayout
    private lateinit var historyBox: LinearLayout
    private lateinit var situationBox: LinearLayout
    private var situation = 0
    private lateinit var cats: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this)
        voice = Voice(this, prefs)
        build()
    }
    override fun onDestroy() { voice.shutdown(); super.onDestroy() }

    internal fun say(text: String) = voice.say(text)
    internal val lastSaid get() = voice.last

    /** The whole text: said, remembered, the field cleared for the next one. */
    internal fun sayAll() {
        val t = field.text.toString().trim(); if (t.isEmpty()) return
        say(t)
        prefs.phrases = Phrases.remember(prefs.phrases, t)
        field.setText("")
        paintHistory()
    }

    private fun chip(s: String, strong: Boolean = false, onClick: () -> Unit) = ui.button(s, main = strong) { onClick() }.apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, ui.px(6), ui.px(8), 0) }
        setPadding(ui.px(16), 0, ui.px(16), 0); textSize = 16f
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun scroll(r: LinearLayout) = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(r) }

    private fun build() {
        val body = ui.column(20)
        body.addView(ui.button(if (prefs.speakLessonDone) "Учиться ещё раз — 2 минуты" else "Научиться — 2 минуты, 6 шагов", main = !prefs.speakLessonDone) {
            startActivity(android.content.Intent(this, SpeakLessonActivity::class.java))
        })
        body.addView(ui.button("Мой пруд: ${prefs.drops} капель") { startActivity(android.content.Intent(this, PondActivity::class.java)) })
        body.addView(ui.button("Тренировки по ситуациям: просьбы, самочувствие, чувства…") { startActivity(android.content.Intent(this, DrillsActivity::class.java)) })
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND            // ⏎ on the keyboard says it
            textSize = 28f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Печатай — я скажу"
            minLines = 2; gravity = Gravity.TOP
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { sayAll(); true } else false }
        }
        var lastLen = 0
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: ""
                // word by word: a space after a word says that word
                if (prefs.speakWords && t.length == lastLen + 1 && t.endsWith(" ")) Regex("[\\p{L}\\p{N}-]+(?= $)").find(t)?.value?.let { say(it) }
                lastLen = t.length
                paintCompletions(t)
            }
        })
        body.addView(field)
        completions = row(); body.addView(scroll(completions))

        body.addView(ui.h("Быстро"))
        val q1 = row(); val q2 = row()
        if (prefs.aboutMe.isNotEmpty()) q1.addView(chip("Про меня", strong = true) { say(prefs.aboutMe); showToPartner(prefs.aboutMe) })
        Phrases.QUICK.forEachIndexed { i, p -> (if (i < 3) q1 else q2).addView(chip(p) { say(p) }) }
        body.addView(scroll(q1)); body.addView(scroll(q2))

        if (prefs.calm) {   // calm: only what is needed now — the field, the quick replies, the own phrases
            for (p in prefs.myPhrases) body.addView(ui.button(p) { say(p) })
            val bottom = ui.bottomBar()
            bottom.addView(ui.button("Сказать", main = true) { sayAll() })
            bottom.addView(ui.button("Показать собеседнику") { showToPartner() })
            body.addView(ui.onOff("Спокойный режим", { prefs.calm }) { prefs.calm = it; build() })
            setContentView(ui.screen(body, bottom)); field.requestFocus(); return
        }
        body.addView(ui.h("Фразы"))
        cats = row()
        body.addView(scroll(cats))
        situationBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(situationBox); paintSituation()

        body.addView(ui.h("Сказано"))
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(historyBox); paintHistory()

        body.addView(ui.onOff("Спокойный режим", { prefs.calm }) { prefs.calm = it; build() })
        body.addView(ui.note("Мягче и медленнее голос, без звука «пульк» и ряби, меньше кнопок на экране."))
        body.addView(ui.button("Мои фразы и «Про меня» — для взрослого") { startActivity(android.content.Intent(this, MyPhrasesActivity::class.java)) })
        body.addView(ui.onOff("Говорить по словам, пока печатаю", { prefs.speakWords }) { prefs.speakWords = it })
        body.addView(ui.choice("Темп голоса", listOf("0.8" to "Медленно", "1.0" to "Обычно", "1.3" to "Быстро"), { "%.1f".format(Locale.US, prefs.speakRate) }) { prefs.speakRate = it.toFloat() })

        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сказать", main = true) { sayAll() })
        bottom.addView(ui.button("Показать собеседнику") { showToPartner() })
        setContentView(ui.screen(body, bottom))
        field.requestFocus()
    }

    private fun paintCompletions(t: String) {
        completions.removeAllViews()
        for (p in Phrases.complete(t, prefs.phrases, mine = prefs.myPhrases)) completions.addView(chip(p, strong = completions.childCount == 0) {
            field.setText(p); field.setSelection(p.length); sayAll()
        })
    }
    /** Situations, with the person's own phrases first when there are any. */
    private fun situations() = (if (prefs.myPhrases.isNotEmpty()) listOf("Мои" to prefs.myPhrases) else emptyList()) + Phrases.SITUATIONS
    private fun paintSituation() {
        // the chosen situation: filled and ticked, not by colour alone
        cats.removeAllViews()
        situations().forEachIndexed { i, (name, _) -> cats.addView(chip(if (i == situation) "✓ $name" else name, strong = i == situation) { situation = i; paintSituation() }) }
        situationBox.removeAllViews()
        for (p in situations()[situation].second) situationBox.addView(ui.button(p) { say(p); prefs.phrases = Phrases.remember(prefs.phrases, p); paintHistory() })
    }
    private fun paintHistory() {
        historyBox.removeAllViews()
        val h = prefs.phrases.take(8)
        if (h.isEmpty()) historyBox.addView(ui.note("Здесь появятся сказанные фразы — касание повторит."))
        for (p in h) historyBox.addView(ui.button("↻ $p") { say(p) })
    }

    /** The text, huge, for the person opposite: it can be turned upside down to face them across a table. */
    private fun showToPartner(given: String? = null) {
        val text = given ?: field.text.toString().trim().ifEmpty { lastSaid.ifEmpty { prefs.phrases.firstOrNull() ?: "" } }
        val big = TextView(this).apply {
            this.text = text; textSize = 44f; setTextColor(ui.ink); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(ui.px(24), ui.px(24), ui.px(24), ui.px(24))
        }
        val frame = FrameLayout(this).apply { setBackgroundColor(ui.bg); addView(big, FrameLayout.LayoutParams(-1, -1)) }
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Перевернуть к собеседнику") { big.rotation = if (big.rotation == 0f) 180f else 0f })
        bottom.addView(ui.button("Вернуться", main = true) { build() })
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.bg)
            addView(frame, LinearLayout.LayoutParams(-1, 0, 1f)); addView(bottom)
        })
    }
}
