package app.pulk

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * «Разговор»: the child's phrases and the adult's, in turns, big. The adult may write a question too — many
 * autistic children follow written words more easily than spoken ones, and the feed shows whose turn it is.
 * A touch on any line says it again. Kept on the phone only.
 */
class TalkActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var voice: Voice
    internal lateinit var child: EditText
    internal lateinit var adult: EditText
    private var sure = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this); voice = Voice(this, prefs)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        build()
    }
    override fun onDestroy() { voice.shutdown(); super.onDestroy() }
    internal val lastSaid get() = voice.last

    /** [from]: the field it was typed in, emptied once it is said; the other field keeps what is being typed. */
    private fun mine(t: String, from: EditText? = null) { val s = t.trim(); if (s.isEmpty()) return; voice.say(s); from?.setText("")
        prefs.journal = Together.logged(prefs.journal, java.time.LocalDate.now().toString(), prefs.phrases.none { it.equals(s, ignoreCase = true) }); prefs.phrases = Phrases.remember(prefs.phrases, s); prefs.talk = Together.said(prefs.talk, true, s); build() }
    private fun yours(t: String) { val s = t.trim(); if (s.isEmpty()) return; adult.setText(""); prefs.talk = Together.said(prefs.talk, false, s); build() }

    private fun bubble(l: Together.Line) = TextView(this).apply {
        text = l.text; contentDescription = (if (l.me) "Я: " else "Собеседник: ") + l.text; textSize = 22f; setLineSpacing(0f, 1.15f)
        setTypeface(typeface, if (l.me) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (l.me) ui.onAccent else ui.ink)
        setPadding(ui.px(14), ui.px(10), ui.px(14), ui.px(10))
        background = GradientDrawable().apply { cornerRadius = ui.px(18).toFloat(); setColor(if (l.me) ui.accent else ui.panel); setStroke(ui.px(if (l.me) 0 else 1), ui.line) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (l.me) Gravity.END else Gravity.START; topMargin = ui.px(8)
            if (l.me) leftMargin = ui.px(48) else rightMargin = ui.px(48)
        }
        setOnClickListener { voice.say(l.text) }
    }

    private fun build() {
        // what is being typed in either field survives the feed being drawn again
        val keepAdult = if (::adult.isInitialized) adult.text.toString() else ""
        val keepChild = if (::child.isInitialized) child.text.toString() else ""
        val body = ui.column(16)
        body.addView(ui.button(if (sure) "Точно стереть разговор? Коснитесь ещё раз" else "Новый разговор") {
            if (sure) { prefs.talk = emptyList(); sure = false } else sure = true
            build()
        })
        val lines = Together.talk(prefs.talk).takeLast(40)
        if (lines.isEmpty()) body.addView(ui.note("Здесь будет разговор по очереди: вопросы взрослого — слева, ответы — справа, крупно. Касание повторяет фразу вслух."))
        for (l in lines) body.addView(bubble(l))

        val ar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        adult = EditText(this).apply {
            id = Ui.ID_TALK_ADULT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; imeOptions = EditorInfo.IME_ACTION_SEND
            textSize = 18f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Взрослый: вопрос или ответ"
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { yours(text.toString()); true } else false }
            setText(keepAdult)
        }
        ar.addView(adult, LinearLayout.LayoutParams(0, -2, 1f))
        ar.addView(ui.button("→") { yours(adult.text.toString()) }.apply { layoutParams = LinearLayout.LayoutParams(ui.px(64), -2) })
        body.addView(ar)

        val q = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in Phrases.QUICK) q.addView(ui.button(p.substringBefore(',')) { mine(p) }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, ui.px(8), ui.px(8), 0) }; setPadding(ui.px(16), 0, ui.px(16), 0)
        })
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(q) })

        child = EditText(this).apply {
            id = Ui.ID_TALK_CHILD
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; imeOptions = EditorInfo.IME_ACTION_SEND
            textSize = 26f; setTextColor(ui.ink); setHintTextColor(ui.muted); hint = "Ответ — звездой"; minLines = 2; gravity = Gravity.TOP
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { mine(text.toString(), this); true } else false }
            setText(keepChild); setSelection(keepChild.length)
        }
        body.addView(child)

        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сказать", main = true) { mine(child.text.toString(), child) })
        bottom.addView(ui.button("← Говорить") { finish() })
        val screen = ui.screen(body, bottom)
        setContentView(screen)
        val sv = (screen as LinearLayout).getChildAt(0) as ScrollView
        sv.post { sv.fullScroll(View.FOCUS_DOWN); child.requestFocus() }
    }
}
