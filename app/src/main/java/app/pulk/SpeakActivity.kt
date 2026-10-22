package app.pulk

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * «Говорить»: for a person who can move but not speak — made for a conversation with the people close by.
 * Answers first («да», «нет», «подожди»), a choice the adult offered, «Срочно», then the phrase being typed (said
 * aloud by the phone, no internet), then phrases: own, the family's with photos, the moment's, ready ones.
 * Everything for the adult is on its own screen. The screen stays on: while one types, the partner waits.
 */
class SpeakActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    private lateinit var field: EditText
    private lateinit var completions: LinearLayout
    private lateinit var historyBox: LinearLayout
    private lateinit var situationBox: LinearLayout
    private var situation = -1
    private lateinit var cats: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this)
        voice = Voice(this, prefs, onTrouble = { runOnUiThread { if (!isFinishing && !isDestroyed) build() } })   // no voice: say so and where to get one
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        build()
    }
    override fun onResume() { super.onResume(); if (::field.isInitialized) build() }   // back from the adult's screen: a new choice, new words
    override fun onDestroy() { voice.shutdown(); super.onDestroy() }

    internal fun say(text: String) = voice.say(text)
    internal val lastSaid get() = voice.last

    /** Said by the child: aloud, remembered in the history and in the conversation. */
    private fun sayMine(t: String, history: Boolean = true) {
        say(t)
        prefs.journal = Together.logged(prefs.journal, java.time.LocalDate.now().toString(), prefs.phrases.none { it.equals(t.trim(), ignoreCase = true) })
        if (history) prefs.phrases = Phrases.remember(prefs.phrases, t)
        prefs.talk = Together.said(prefs.talk, true, t)
    }

    /** The whole text: said, remembered, the field cleared for the next one. */
    internal fun sayAll() {
        val t = field.text.toString().trim(); if (t.isEmpty()) return
        sayMine(t)
        field.setText("")
        if (::historyBox.isInitialized) paintHistory()
    }

    private fun chip(s: String, strong: Boolean = false, onClick: () -> Unit) = ui.button(s, main = strong) { onClick() }.apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, ui.px(6), ui.px(8), 0) }
        setPadding(ui.px(16), 0, ui.px(16), 0); textSize = 16f
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun scroll(r: LinearLayout) = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(r) }
    private fun <T : android.view.View> weighted(v: T) = v.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(ui.px(3), ui.px(6), ui.px(3), 0) } }

    /** A button with a photo above its word (the family's words, options of a choice). */
    internal fun photoButton(label: String, photo: String?, main: Boolean = false, onClick: () -> Unit): Button {
        val b = ui.button(label, main) { onClick() }
        val bmp = Photos.load(photo)
        if (bmp != null) {
            val d = BitmapDrawable(resources, bmp).apply { setBounds(0, 0, ui.px(72), ui.px(72)) }
            b.setCompoundDrawables(null, d, null, null); b.compoundDrawablePadding = ui.px(4); b.setPadding(ui.px(6), ui.px(10), ui.px(6), ui.px(10))
        }
        return b
    }

    private fun build() {
        val keep = if (::field.isInitialized) field.text.toString() else ""   // what was being typed survives a rebuild
        val body = ui.column(16)
        val family = Together.family(prefs.family)
        voice.trouble?.let { body.addView(ui.card(ui.text(it, 16f, ui.urgent, true), ui.note(Voice.HOW))) }

        // a choice the adult offered: big, first, said with one touch; a family photo shows on its option
        Together.decode(prefs.choice)?.let { c ->
            val card = ui.card()
            if (c.q.isNotEmpty()) card.addView(ui.text(c.q, 22f, ui.ink, true))
            c.opts.chunked(2).forEachIndexed { r, pair ->
                val line = row()
                pair.forEachIndexed { k, o ->
                    val i = r * 2 + k
                    line.addView(weighted(photoButton(if (prefs.choicePicked == i) "✓ $o" else o, Together.photoOf(family, o), main = prefs.choicePicked == i) {
                        sayMine(o); prefs.choicePicked = i; build()
                    }.apply { textSize = 22f; minHeight = ui.px(72) }))
                }
                card.addView(line)
            }
            card.addView(ui.button("Готово — убрать выбор") { prefs.choice = ""; prefs.choicePicked = -1; build() })
            body.addView(card)
        }

        // answers first: in a conversation «да», «нет», «подожди» are needed most and fastest
        Phrases.QUICK.chunked(ui.perRow(Phrases.QUICK.map { it.substringBefore(',') }, 16f, 3, inset = 52)).forEach { three ->
            val line = row()
            three.forEach { p -> line.addView(weighted(ui.button(p.substringBefore(',')) { sayMine(p, history = false) })) }
            body.addView(line)
        }
        val r0 = row()
        if (prefs.aboutMe.isNotEmpty()) r0.addView(weighted(ui.button("Про меня", main = true) { sayMine(prefs.aboutMe, history = false); showToPartner(prefs.aboutMe) }))
        r0.addView(weighted(ui.button("Срочно") { situation = situations().indexOfFirst { it.first == "Срочно" }; paintSituation() }.apply {
            setTextColor(ui.urgent); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = ui.px(16).toFloat(); setColor(ui.panel); setStroke(ui.px(2), ui.urgent) }
        }))
        r0.addView(weighted(ui.button("Разговор") { startActivity(Intent(this, TalkActivity::class.java)) }))
        body.addView(r0)

        field = EditText(this).apply {
            id = Ui.ID_SPEAK
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
        if (keep.isNotEmpty()) { field.setText(keep); field.setSelection(keep.length) }

        cats = row()
        situationBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(ui.h("Фразы")); body.addView(scroll(cats)); body.addView(situationBox)
        if (situation < 0 || situation >= situations().size) situation = situations().indexOfFirst { it.first == (if (family.isNotEmpty()) "Семья" else Phrases.now(hour()).first) }.coerceAtLeast(0)
        paintSituation()
        if (!prefs.calm) { body.addView(ui.h("Сказано")); body.addView(historyBox); paintHistory() }

        body.addView(ui.h("Для взрослого"))
        body.addView(ui.button("Предложить выбор") { startActivity(Intent(this, MyPhrasesActivity::class.java).putExtra(MyPhrasesActivity.CHOICE, true)) })
        body.addView(ui.button("Слова семьи с фото, «Про меня», свои фразы") { startActivity(Intent(this, MyPhrasesActivity::class.java)) })
        body.addView(ui.button("Как помочь ребёнку") { startActivity(Intent(this, ParentsActivity::class.java)) })
        body.addView(ui.h("Учиться и играть"))
        body.addView(ui.button(if (prefs.speakLessonDone) "Учиться ещё раз — 3 минуты" else "Научиться — 3 минуты, 8 шагов", main = !prefs.speakLessonDone) {
            startActivity(Intent(this, SpeakLessonActivity::class.java))
        })
        body.addView(ui.button("Тренировки по ситуациям: просьбы, самочувствие, чувства…") { startActivity(Intent(this, DrillsActivity::class.java)) })
        body.addView(ui.button("Мой пруд: ${prefs.drops} капель") { startActivity(Intent(this, PondActivity::class.java)) })
        body.addView(ui.onOff("Спокойный режим", { prefs.calm }) { prefs.calm = it; build() })
        body.addView(ui.note("Мягче и медленнее голос, без звука «пульк» и ряби, меньше кнопок на экране."))
        body.addView(ui.onOff("Говорить по словам, пока печатаю", { prefs.speakWords }) { prefs.speakWords = it })
        body.addView(ui.choice("Темп голоса", listOf("0.8" to "Медленно", "1.0" to "Обычно", "1.3" to "Быстро"), { "%.1f".format(Locale.US, prefs.speakRate) }) { prefs.speakRate = it.toFloat() })

        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сказать", main = true) { sayAll() })
        bottom.addView(ui.button("Показать собеседнику") { showToPartner() })
        setContentView(ui.screen(body, bottom))
        field.requestFocus()
    }

    private fun hour() = java.time.LocalTime.now().hour

    private fun paintCompletions(t: String) {
        completions.removeAllViews()
        for (p in Phrases.complete(t, prefs.phrases, mine = prefs.myPhrases)) completions.addView(chip(p, strong = completions.childCount == 0) {
            field.setText(p); field.setSelection(p.length); sayAll()
        })
    }
    /** Situations: own phrases, the family's words, what fits this part of the day; in calm mode only those and «Срочно». */
    private fun situations(): List<Pair<String, List<String>>> {
        val own = (if (prefs.myPhrases.isNotEmpty()) listOf("Мои" to prefs.myPhrases) else emptyList()) +
            (if (prefs.family.isNotEmpty()) listOf("Семья" to Together.family(prefs.family).map { it.word }) else emptyList()) +
            listOf(Phrases.now(hour()))
        return if (prefs.calm) own + Phrases.SITUATIONS.filter { it.first == "Срочно" } else own + Phrases.SITUATIONS
    }
    private fun paintSituation() {
        // the chosen situation: filled and ticked, not by colour alone
        val all = situations(); if (situation !in all.indices) situation = 0
        cats.removeAllViews()
        all.forEachIndexed { i, (name, _) -> cats.addView(chip(if (i == situation) "✓ $name" else name, strong = i == situation) { situation = i; paintSituation() }) }
        situationBox.removeAllViews()
        val (name, list) = all[situation]
        if (name == "Семья") {
            val fam = Together.family(prefs.family)
            fam.chunked(3).forEach { three ->
                val line = row()
                three.forEach { f -> line.addView(weighted(photoButton(f.word, f.photo) { sayMine(f.word); if (::historyBox.isInitialized) paintHistory() })) }
                repeat(3 - three.size) { line.addView(weighted(android.view.View(this))) }
                situationBox.addView(line)
            }
            return
        }
        for (p in list) situationBox.addView(ui.button(p) { sayMine(p); paintHistory() })
    }
    private fun paintHistory() {
        if (prefs.calm) return
        historyBox.removeAllViews()
        val h = prefs.phrases.take(8)
        if (h.isEmpty()) historyBox.addView(ui.note("Здесь появятся сказанные фразы — касание повторит."))
        for (p in h) historyBox.addView(ui.button("↻ $p") { sayMine(p, history = false) }.apply { contentDescription = "Повторить: $p" })
    }

    /** The text, huge, for the person opposite: it can be turned upside down to face them across a table. */
    private fun showToPartner(given: String? = null) {
        val text = given ?: field.text.toString().trim().ifEmpty { lastSaid.ifEmpty { prefs.phrases.firstOrNull() ?: "" } }
        val big = TextView(this).apply {
            this.text = text; textSize = if (text.length < 16) 52f else if (text.length < 50) 40f else 30f
            setTextColor(ui.ink); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(ui.px(24), ui.px(24), ui.px(24), ui.px(24))
            contentDescription = "Для собеседника: $text"
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
