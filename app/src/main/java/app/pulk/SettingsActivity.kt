package app.pulk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** The app's front door: turn the keyboard on, try it, choose hand, sound and size. */
class SettingsActivity : Activity() {
    private val bg = Color.parseColor("#0D1215")
    private val panel = Color.parseColor("#141B1F")
    private val fg = Color.parseColor("#E4EBEE")
    private val muted = Color.parseColor("#8B9AA2")
    private val accent = Color.parseColor("#55BAD7")
    private val accentFg = Color.parseColor("#06222B")
    private lateinit var prefs: Prefs
    private lateinit var drops: Feedback   // to hear a «пульк» sound when choosing it
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        drops = Feedback(this, prefs)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(28), px(20), px(36)); setBackgroundColor(bg) }

        col.addView(text("Пульк", 32f, fg, true))
        col.addView(text("Клавиатура для набора одной рукой, большим пальцем. Режим «Пульс»: все буквы — в звезде из 8 направлений, палец почти не двигается.", 16f, muted))

        // first of all: fit the keyboard to this hand (once; can be redone)
        col.addView(button(if (prefs.calibrated) "Настройка под руку — пройти заново" else "Настрой Пульк под свою руку — 30 секунд", primary = !prefs.calibrated) {
            startActivity(Intent(this, CalibrationActivity::class.java))
        })
        col.addView(text("Клавиатура узнает длину твоих смахов, дрожь пальца и куда он на самом деле ведёт, и подстроится.", 13f, muted))
        col.addView(button("Обучение: «Пульс» и «Толчки»", primary = prefs.calibrated) { startActivity(Intent(this, TutorialActivity::class.java)) })
        col.addView(h("Как печатать — выбери"))
        col.addView(segmented("Способ набора", listOf("pulse" to "Пульс: смах = буква", "jabs" to "Толчки: касание = слово"), { prefs.mode }) { prefs.mode = it })
        col.addView(segmented("Где начинать смах", listOf("corner" to "Звезда в углу", "screen" to "Весь экран"), { prefs.zone }) { prefs.zone = it })
        col.addView(text("Весь экран: смах начинается где угодно, звезда появляется под пальцем, касание без движения — пробел. Пока клавиатура открыта, приложение за ней не нажимается: чтобы нажать «Отправить», спрячь клавиатуру ⌄.", 13f, muted))
        col.addView(text("Эти настройки и все остальные открываются и с клавиатуры: удержи ⌄ у нижнего края.", 13f, muted))

        col.addView(h("1. Включи клавиатуру"))
        col.addView(button("Открыть настройки клавиатур") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(text("В списке включи «Пульк». Android предупредит, что клавиатура видит всё, что ты печатаешь. У Пулька нет доступа к интернету, набранное не покидает телефон.", 14f, muted))
        col.addView(h("2. Выбери её для набора"))
        col.addView(button("Выбрать клавиатуру") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
        col.addView(h("3. Попробуй здесь"))
        col.addView(EditText(this).apply {
            hint = "Нажми и печатай"; setHintTextColor(muted); setTextColor(fg); textSize = 20f; minLines = 3; gravity = Gravity.TOP
        })

        col.addView(h("Как печатать"))
        col.addView(text(
            "Поставь палец в звезду (в режиме «Весь экран» — куда угодно) и смахни:\n" +
            "• прямо в сторону крупной буквы — она;\n" +
            "• смахни и сразу поверни к мелкой букве — мелкая снаружи;\n" +
            "• смахни и верни палец назад — мелкая у центра;\n" +
            "• касание в центре звезды — пробел.\n\n" +
            "Задержи палец в конце жеста: е → ё, ь → ъ, ? → !.\n" +
            "Смах по пробелу наружу — точка, внутрь — запятая, вдоль дуги вниз — стереть слово, вдоль дуги вверх — взять лучшую подсказку.\n" +
            "Подсказки слов — над звездой, лучшая ближе к пальцу. Если клавиатура исправила слово, а ты хотел своё, — сразу нажми ⌫.\n" +
            "⌫ — высокая клавиша вдоль правого края: удержание стирает подряд, смах по ней к звезде — слово целиком.\n" +
            "Угол — большой пробел и ⏎. ⇧, RU/EN, ?123 и ⌄ (спрятать) — вдоль нижнего края под звездой.\n" +
            "Удержи RU/EN — список клавиатур, чтобы вернуться к другой.\n" +
            "Кнопка ?123 переключает на цифры и знаки, RU/EN — язык.", 15f, fg))

        col.addView(h("Толчки — слово одним касанием"))
        col.addView(text(
            "Поставь палец в звезду и не отрывай его: толкни к букве и верни назад — буква напечатана, толкни к следующей… " +
            "Поднял палец — пробел. Двойная буква — два толчка в одну сторону. Толчок с поворотом — мелкая буква снаружи, короткий толчок — у центра. " +
            "Сбился — подними палец и сотри ⌫; смах по ⌫ к звезде стирает слово целиком.", 15f, fg))
        col.addView(h("Курсор"))
        col.addView(text(
            "Удержи пробел и веди палец влево или вправо — курсор едет следом. " +
            "Можно включить и окно текста над клавиатурой (ниже в настройках): коснись текста в нём — курсор встанет туда.", 15f, fg))

        col.addView(h("Настройки"))
        col.addView(segmented("Окно текста", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.textWindow) "on" else "off" }) { prefs.textWindow = it == "on" })
        col.addView(segmented("Рука", listOf("left" to "Левая", "right" to "Правая"), { prefs.hand }) { prefs.hand = it })
        col.addView(segmented("Звук", listOf("off" to "Без звука", "clicks" to "Щелчки", "voice" to "Голос"), { prefs.sound }) { prefs.sound = it })
        col.addView(text("Щелчки: у каждого направления своя нота. Голос: называет введённую букву и читает слово после пробела (в полях паролей не читает).", 13f, muted))
        col.addView(segmented("Вибрация", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.haptics) "on" else "off" }) { prefs.haptics = it == "on" })
        col.addView(segmented("Вслепую", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.blind) "on" else "off" }) {
            prefs.blind = it == "on"
            if (prefs.blind && prefs.sound == "off") prefs.sound = "voice" // eyes-free needs sound
            if (prefs.blind && prefs.compass == "off") prefs.compass = "voice"
        })
        col.addView(text("Вслепую: звезда крупнее, касание без движения в любом месте — пробел, включается голос.", 13f, muted))
        col.addView(segmented("Звук «пульк» при появлении", listOf("bul" to "Буль", "glass" to "Стакан", "drip" to "Капелька", "off" to "Выкл"), { prefs.dropKind }) { prefs.dropKind = it; drops.drop(it) })
        col.addView(text("Клавиатура выдвигается из угла со звуком капли. ⌄ у нижнего края прячет её, касание кружка в углу — возвращает. Видна только звезда и полупрозрачные клавиши, за ними — приложение; касания мимо них идут в приложение. Без звука, если телефон в беззвучном режиме.", 13f, muted))
        col.addView(segmented("Звуковой компас", listOf("off" to "Выкл", "notes" to "Ноты", "voice" to "Голос"), { prefs.compass }) { prefs.compass = it })
        col.addView(text("Пока ведёшь палец, слышишь, какая буква выйдет, если отпустить сейчас: нота направления или название буквы. Можно поправиться, не глядя. С TalkBack включается сам.", 13f, muted))
        col.addView(segmented("Следующее слово", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.predict) "on" else "off" }) { prefs.predict = it == "on" })
        col.addView(text("После пробела над звездой — слова, которые обычно идут следом (по корпусу живых фраз Tatoeba и по тому, как пишешь ты). Лучшее — смах по пробелу вверх.", 13f, muted))
        col.addView(segmented("Подсказки слов", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.suggest) "on" else "off" }) { prefs.suggest = it == "on" })
        col.addView(segmented("Автоисправление", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.autocorrect) "on" else "off" }) { prefs.autocorrect = it == "on" })
        col.addView(text("Пробел меняет незнакомое слово на ближайшее из словаря, учитывая типичные промахи «Пульса». ⌫ сразу после — вернуть своё; такое слово клавиатура запомнит.", 13f, muted))
        col.addView(segmented("Запоминать мои слова", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.learn) "on" else "off" }) { prefs.learn = it == "on" })
        col.addView(button("Забыть мои слова") { prefs.forget = true; (it as Button).text = "Забудет при следующем открытии клавиатуры" })
        col.addView(text("Слова хранятся только на телефоне. В полях паролей, адресов и в режиме инкогнито клавиатура ничего не запоминает.", 13f, muted))
        col.addView(text("Размер клавиатуры", 15f, fg, true))
        col.addView(SeekBar(this).apply {
            max = 35; progress = prefs.size - 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { prefs.size = 80 + p }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        col.addView(text("Настройки применяются, когда клавиатура открывается заново.", 13f, muted))
        col.addView(h("О программе"))
        col.addView(text("Пульк ${packageManager.getPackageInfo(packageName, 0).versionName}. Бесплатно, без рекламы и без интернета.\nСловари: FrequencyWords (Hermit Dave), CC BY-SA 4.0. Следующее слово: предложения Tatoeba (tatoeba.org), CC BY 2.0 FR. Фильтр дрожи: 1€ filter (Casiez, Roussel, Vogel, 2012).", 13f, muted))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(col) })
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); setLineSpacing(0f, 1.15f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, px(6), 0, px(6))
    }
    private fun h(s: String) = text(s, 19f, fg, true).apply { setPadding(0, px(22), 0, px(6)) }
    private fun button(s: String, primary: Boolean = false, onClick: (View) -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 16f; minHeight = px(52); minimumHeight = px(52)
        setTextColor(if (primary) accentFg else fg)
        background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(if (primary) accent else panel); setStroke(px(1), if (primary) accent else Color.parseColor("#29343A")) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6); bottomMargin = px(4) }
        setOnClickListener { onClick(it) }
    }

    /** A row of choices; the chosen one is highlighted. */
    private fun segmented(title: String, options: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(text(title, 15f, fg, true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = ArrayList<Pair<String, Button>>()
        fun paint() {
            val cur = get()
            for ((v, btn) in buttons) {
                val on = v == cur
                btn.background = GradientDrawable().apply { cornerRadius = px(10).toFloat(); setColor(if (on) accent else panel); setStroke(px(1), if (on) accent else Color.parseColor("#29343A")) }
                btn.setTextColor(if (on) accentFg else fg)
            }
        }
        for ((v, label) in options) {
            val btn = Button(this).apply {
                text = label; isAllCaps = false; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(2), 0, px(2), 0) }
                minHeight = px(48); minimumHeight = px(48); setPadding(px(6), px(8), px(6), px(8))
                setOnClickListener { set(v); paint() }
            }
            buttons.add(v to btn); row.addView(btn)
        }
        paint(); wrap.addView(row)
        return wrap
    }
}
