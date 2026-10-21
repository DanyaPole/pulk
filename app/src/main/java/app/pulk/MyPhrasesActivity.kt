package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

/**
 * «Для взрослого»: what the grown-up beside the child sets up. A choice to offer (the quickest way to a first
 * conversation), the family's words with photos (first in suggestions, on the buttons of a choice), «Про меня»,
 * own phrases, and help for a hand that shakes. Own words matter more than any ready ones.
 */
class MyPhrasesActivity : Activity() {
    companion object { const val CHOICE = "choice"; private const val PICK = 7; private const val PHOTO = "photo" }
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var list: LinearLayout
    private lateinit var famBox: LinearLayout
    private lateinit var about: EditText
    internal var photo: String? = null
    private var photoButton: android.widget.Button? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); prefs = Prefs(this); ui = Ui(this)
        photo = b?.getString(PHOTO)?.takeIf { File(it).isFile }   // a photo chosen before Android closed the screen
        build()
        if (photo != null) photoButton?.text = "Фото ✓"
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); photo?.let { out.putString(PHOTO, it) } }
    override fun onPause() { if (::about.isInitialized) prefs.aboutMe = about.text.toString().trim(); super.onPause() }

    private var fields = 0   // counted anew at each build, so each field keeps its id (see [Ui.ID_SPEAK])
    private fun field(hint: String, value: String, lines: Int = 1) = EditText(this).apply {
        id = Ui.ID_ADULT + fields++
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or (if (lines > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
        textSize = 20f; setTextColor(ui.ink); setHintTextColor(ui.muted); this.hint = hint; setText(value); minLines = lines
    }

    internal fun offer(question: String, typed: List<String>) {
        val opts = Together.options(typed.joinToString(","))
        if (opts.isEmpty()) return
        val c = Together.Choice(question.trim(), opts)
        prefs.choice = Together.encode(c); prefs.choicePicked = -1
        prefs.choices = (listOf(prefs.choice) + prefs.choices.filter { Together.decode(it)?.opts != opts }).take(8)
        if (c.q.isNotEmpty()) prefs.talk = Together.said(prefs.talk, false, c.q)
        finish()
    }

    private fun build() {
        fields = 0
        val body = ui.column()
        body.addView(ui.title("Для взрослого"))
        val wk = Together.week(prefs.journal, java.time.LocalDate.now())
        body.addView(ui.card(ui.text(todayTip(), 17f, ui.ink, true),
            ui.note(if (wk[0] > 0) "За неделю: сказано ${wk[0]} раз, новых фраз — ${wk[1]}, дней с Пульком — ${wk[2]}. Всё это хранится только на этом телефоне." else "Здесь появится, сколько сказано за неделю. Хранится только на этом телефоне.")))

        val choiceAt = ui.h("Предложить выбор"); body.addView(choiceAt)
        body.addView(ui.note("Спросите вслух и напишите варианты. Ребёнок коснётся — телефон скажет. Фото из словаря семьи появятся на кнопках сами."))
        val q = field("Вопрос, можно пусто: Что будешь пить?", "")
        val o = field("Варианты через запятую: сок, вода, молоко", "")
        body.addView(q); body.addView(o)
        body.addView(ui.button("Показать ребёнку", main = true) { offer(q.text.toString(), listOf(o.text.toString())) })
        val recent = prefs.choices.mapNotNull { Together.decode(it) }
        if (recent.isNotEmpty()) body.addView(ui.note("Недавние — одним касанием:"))
        for (c in recent) body.addView(ui.button((if (c.q.isNotEmpty()) c.q + " — " else "") + c.opts.joinToString(", ")) { offer(c.q, c.opts) })

        body.addView(ui.h("Словарь семьи"))
        body.addView(ui.note("Имена, питомцы, любимые вещи и места — с фото, если хотите. Эти слова первыми идут в подсказках клавиатуры и на кнопках выбора."))
        val w = field("Слово: Барсик, бабушка, качели", "")
        body.addView(w)
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        photoButton = ui.button("Фото") { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK) }
            .apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = ui.px(6); topMargin = ui.px(8) } }
        r.addView(photoButton)
        r.addView(ui.button("Добавить") {
            val t = w.text.toString().trim(); if (t.isEmpty()) return@button
            addFamily(t, photo ?: ""); w.setText(""); photo = null; photoButton?.text = "Фото"; paintFamily()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = ui.px(8) } })
        body.addView(r)
        famBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(famBox); paintFamily()

        body.addView(ui.h("Про меня"))
        body.addView(ui.note("Говорится и показывается крупно одним касанием — для новых людей."))
        about = field("Меня зовут … Я не говорю, но всё понимаю. Я отвечу, напечатав.", prefs.aboutMe, 3)
        body.addView(about)

        body.addView(ui.h("Свои фразы"))
        val add = field("Новая фраза, например «Мама, я хочу домой»", "")
        body.addView(add)
        body.addView(ui.button("Добавить фразу") {
            val t = add.text.toString().trim(); if (t.isEmpty()) return@button
            prefs.myPhrases = (prefs.myPhrases + t).distinct(); add.setText(""); paint()
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list); paint()

        body.addView(ui.h("Если рука дрожит или задевает экран"))
        body.addView(ui.choice("Защита от случайных касаний", listOf("0" to "Нет", "1" to "Слабая", "2" to "Сильная"), { prefs.guard.toString() }) { prefs.guard = it.toInt() })
        body.addView(ui.note("Короткие задевания и повторные «дребезжащие» касания не станут буквами. Сглаживание дрожи и длина смаха — в «Под мою руку» на главном экране."))
        body.addView(ui.button("Как помочь ребёнку") { startActivity(Intent(this, ParentsActivity::class.java)) })

        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Готово", main = true) { prefs.aboutMe = about.text.toString().trim(); finish() })
        val screen = ui.screen(body, bottom)
        setContentView(screen)
        if (intent.getBooleanExtra(CHOICE, false)) q.requestFocus()
        else (screen as? LinearLayout)?.getChildAt(0)?.let { (it as? ScrollView)?.scrollTo(0, 0) }
    }

    /** One small step for today, from what has (not) been tried yet. */
    private fun todayTip(): String = when {
        !prefs.speakLessonDone -> "Сегодня — урок «Учиться говорить», 3 минуты. Вы рядом, палец — его."
        prefs.choices.isEmpty() -> "Сегодня — выбор из двух: «Предложить выбор» → «сок, вода». Спросите вслух и подождите."
        prefs.family.size < 3 -> "Сегодня — словарь семьи: три слова с фото (мама, любимая игрушка, питомец)."
        else -> listOf("Сегодня — фразы «Близким»: покажите «Обними меня» и «Посмотри!».", "Сегодня — ответьте через Пульк сами: напечатайте ему короткую фразу.",
            "Сегодня — один вопрос с выбором за едой.", "Сегодня — «Пузыри» в «Тренировках», один раунд.", "Сегодня — пусть он напечатает одно своё слово. Любое.")[(java.time.LocalDate.now().toEpochDay() % 5).toInt()]
    }

    internal fun addFamily(word: String, photoPath: String) {
        val wd = word.replaceFirstChar { it.uppercase() }
        val fam = Together.family(prefs.family).filter { !it.word.equals(wd, ignoreCase = true) } + Together.Word(wd, photoPath)
        prefs.family = Together.familyLines(fam.takeLast(60))
    }

    private fun paintFamily() {
        famBox.removeAllViews()
        val fam = Together.family(prefs.family)
        if (fam.isEmpty()) famBox.addView(ui.note("Пока пусто. Слова появятся в «Говорить» во вкладке «Семья»."))
        for (f in fam) famBox.addView(ui.button("✕  ${f.word}" + if (f.photo.isNotEmpty()) " · фото" else "") {
            prefs.family = Together.familyLines(Together.family(prefs.family).filter { it.word != f.word })
            if (f.photo.isNotEmpty()) File(f.photo).delete()
            paintFamily()
        })
    }

    private fun paint() {
        list.removeAllViews()
        if (prefs.myPhrases.isEmpty()) list.addView(ui.note("Пока пусто. Добавленные фразы появятся в «Говорить» первым разделом — «Мои»."))
        for (p in prefs.myPhrases) list.addView(ui.button("✕  $p") { prefs.myPhrases = prefs.myPhrases - p; paint() })
    }

    /** A photo from the gallery or camera: made small and square, kept in the app's own storage (never shared). */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")   // the result API needs a library; this works on every Android
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data; if (req != PICK || res != RESULT_OK || uri == null) return
        val made = try { Photos.square({ contentResolver.openInputStream(uri) }, File(filesDir, "family")) } catch (_: Throwable) { null }   // OutOfMemoryError too
        if (made != null) { photo?.let { File(it).delete() }; photo = made.path; photoButton?.text = "Фото ✓" }
        else photoButton?.text = "Фото не открылось — другое?"
    }
}
