package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar

/**
 * Settings in two steps, as the interface project asks: «Основные» — the few everyone needs; «Ещё» — the rest,
 * in small groups, with the help on gestures and the credits.
 */
class OptionsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var drops: Feedback   // to hear a «пульк» sound when choosing it

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this); drops = Feedback(this, prefs)
        if (intent.getBooleanExtra(MORE, false)) more() else main()
    }
    override fun onDestroy() { drops.shutdown(); super.onDestroy() }

    private fun main() {
        val body = ui.column()
        body.addView(ui.title("Настройки"))
        // three rows choose one mode: «Выкл» turns off only its own mode, and every row shows the mode that is on now
        body.addView(ui.choice("Способ набора", Prefs.modes(prefs, listOf("pulse" to "Пульс", "taps" to "Два касания")), { prefs.mode }) { mode(it) })
        if (prefs.jabsWords >= 10 || prefs.mode == "petals") body.addView(ui.note(Prefs.PETALS_NOTE))
        body.addView(ui.choice("Привычная клавиатура", listOf("off" to "Выкл", "classic" to "ЙЦУКЕН"), { if (prefs.mode == "classic") "classic" else "off" }) { mode(if (it == "classic") "classic" else if (prefs.mode == "classic") "pulse" else prefs.mode) })
        body.addView(ui.note("Обычные ряды клавиш — для того, кто к ним привык. После каждой буквы маленькая звезда показывает, каким смахом она набирается в «Пульсе»: переход случится сам, когда захочется."))
        body.addView(ui.onOff("Показывать звезду-подсказку", { prefs.bridge }) { prefs.bridge = it })
        body.addView(ui.choice("Одна кнопка", listOf("off" to "Выкл", "scan" to "Сканирование"), { if (prefs.mode == "scan") "scan" else "off" }) { mode(if (it == "scan") "scan" else if (prefs.mode == "scan") "pulse" else prefs.mode) })
        body.addView(ui.note("Для тех, кто может нажать только одну кнопку: клавиатура по очереди подсвечивает лепестки, пробел, ⌫ и ⏎, а потом буквы лепестка. Нажатие — любое касание клавиатуры или внешняя кнопка (пробел, ввод)."))
        body.addView(ui.choice("Скорость сканирования", listOf("1800" to "Медленно", "1200" to "Средне", "800" to "Быстро"), { prefs.scanMs.toString() }) { prefs.scanMs = it.toInt() })
        body.addView(ui.note("Пульс — смах на букву. " + (if (prefs.mode == "jabs") "Толчки — слово одним касанием, не отрывая пальца. " else "") + "Два касания — без смахов: коснись лепестка, он раскроется крупно, коснись буквы."))
        body.addView(ui.choice("Рука", listOf("left" to "Левая", "right" to "Правая"), { prefs.hand }) { prefs.hand = it })
        body.addView(ui.choice("Где начинать смах", listOf("corner" to "Звезда в углу", "screen" to "Весь экран"), { prefs.zone }) { prefs.zone = it })
        body.addView(ui.note("Весь экран: смах начинается где угодно, звезда появляется под пальцем. Пока клавиатура открыта, приложение за ней не нажимается: чтобы нажать «Отправить», спрячь клавиатуру ⌄."))
        body.addView(ui.choice("Звук", listOf("off" to "Без звука", "clicks" to "Щелчки", "voice" to "Голос"), { prefs.sound }) { prefs.sound = it })
        body.addView(ui.onOff("Вибрация", { prefs.haptics }) { prefs.haptics = it })
        body.addView(ui.choice("Вид звезды", listOf("petals" to "Лепестки", "ripples" to "Круги на воде"), { prefs.look }) { prefs.look = it })
        body.addView(ui.choice("Тема", listOf("auto" to "Как в телефоне", "contrast" to "Контраст"), { prefs.theme }) { prefs.theme = it })
        body.addView(ui.note("Контраст — для слабого зрения: чёрное и белое, янтарный акцент, жирные буквы, почти непрозрачные клавиши."))
        body.addView(ui.onOff("Простой режим", { prefs.simple }) { prefs.simple = it })
        body.addView(ui.note("Только буквы, пробел, ⌫, ⏎, цифры и одна подсказка — без скрытых жестов и автоисправления."))
        body.addView(ui.text("Размер клавиатуры", 16f, ui.ink, true))
        body.addView(SeekBar(this).apply {
            max = 35; progress = prefs.size - 80
            // TalkBack: «Размер клавиатуры, 100 %», not a bare «ползунок, 57 %»
            contentDescription = "Размер клавиатуры"
            fun state() { if (android.os.Build.VERSION.SDK_INT >= 30) stateDescription = "${prefs.size} %" }
            state()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { prefs.size = 80 + p; state() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        body.addView(ui.note("Настройки применяются, когда клавиатура открывается заново."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Проверка скорости") { startActivity(Intent(this, SpeedActivity::class.java)) })
        bottom.addView(ui.button("Ещё настройки и справка") { startActivity(Intent(this, OptionsActivity::class.java).putExtra(MORE, true)) })
        bottom.addView(ui.button("Готово", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }

    /** A new typing mode: the screen is drawn again where it was, so no row keeps a stale ✓. */
    private fun mode(m: String) {
        if (m == prefs.mode) return
        prefs.mode = m
        val y = scroller()?.scrollY ?: 0
        main()
        scroller()?.let { it.post { it.scrollTo(0, y) } }
    }
    private fun scroller() = ((findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.ScrollView)

    private fun more() {
        val body = ui.column()
        body.addView(ui.title("Ещё"))

        body.addView(ui.h("Не глядя"))
        body.addView(ui.onOff("Вслепую", { prefs.blind }) {
            prefs.blind = it
            if (it && prefs.sound == "off") prefs.sound = "voice"   // eyes-free needs sound
            if (it && prefs.compass == "off") prefs.compass = "voice"
        })
        body.addView(ui.note("Звезда крупнее, касание без движения в любом месте — пробел, включается голос."))
        body.addView(ui.choice("Звуковой компас", listOf("off" to "Выкл", "notes" to "Ноты", "voice" to "Голос"), { prefs.compass }) { prefs.compass = it })
        body.addView(ui.note("Пока ведёшь палец, слышно, какая буква выйдет, если отпустить сейчас. С TalkBack включается сам."))

        body.addView(ui.h("Слова"))
        body.addView(ui.onOff("Подсказки слов", { prefs.suggest }) { prefs.suggest = it })
        body.addView(ui.onOff("Следующее слово", { prefs.predict }) { prefs.predict = it })
        body.addView(ui.onOff("Автоисправление", { prefs.autocorrect }) { prefs.autocorrect = it })
        body.addView(ui.note("⌫ сразу после — вернуть своё. Так же ⌫ сразу после отменяет взятую подсказку и стёртое смахом слово."))
        body.addView(ui.onOff("Запоминать мои слова", { prefs.learn }) { prefs.learn = it })
        body.addView(ui.button("Забыть мои слова") { prefs.forget = true; (it as Button).text = "Забудет при следующем открытии клавиатуры" })
        body.addView(ui.note("Слова хранятся только на телефоне. В полях паролей, адресов и в режиме инкогнито клавиатура ничего не запоминает."))

        body.addView(ui.h("Помочь улучшить Пульк"))
        body.addView(ui.note("Можно записывать форму и время движений к заданной букве в уроках и «Пузырях»: цель, что распознал Пульк, способ набора, рука и настройка под руку. Без набранного текста, имён и данных телефона. Обычный набор, свободные ответы, пароли и защищённые поля не записываются никогда."))
        body.addView(ui.note("Всё остаётся на телефоне. Чтобы отдать файл, нажми «Отправить/сохранить файл» и сам выбери приложение в «Поделиться». Пульк ничего не отправляет сам. Запись можно выключить, а записи — стереть."))
        body.addView(ui.onOff("Записывать движения пальца", { prefs.recordTraces }) { GestureTraces.consent(this, it) })
        val traceCount = ui.note("Записано движений: ${GestureTraces.count(this)}. До 5 МБ; затем запись ждёт очистки.")
        body.addView(traceCount)
        body.addView(ui.button("Отправить/сохранить файл") {
            try {
                val uri = GestureTraces.snapshot(this)
                if (uri == null) { traceCount.text = "Пока нет записей. Они появятся в уроках и «Пузырях», если включить запись."; return@button }
                val send = Intent(Intent.ACTION_SEND).setType("application/x-ndjson")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri("Движения Пулька", uri)
                startActivity(Intent.createChooser(send, "Отправить/сохранить файл"))
            } catch (_: Exception) { traceCount.text = "Не получилось открыть файл. Записи остались на телефоне, можно попробовать ещё раз." }
        })
        body.addView(ui.button("Стереть все движения") {
            try { GestureTraces.clear(this); traceCount.text = "Записано движений: 0. Всё стёрто." }
            catch (_: java.io.IOException) { traceCount.text = "Не получилось стереть записи. Попробуй ещё раз." }
        })

        body.addView(ui.h("Вид и звук"))
        body.addView(ui.choice("Звук «пульк» при появлении", listOf("bul" to "Буль", "glass" to "Стакан", "drip" to "Капелька", "off" to "Выкл"), { prefs.dropKind }) { prefs.dropKind = it; drops.drop(it) })
        body.addView(ui.onOff("Окно текста над клавиатурой", { prefs.textWindow }) { prefs.textWindow = it })

        body.addView(ui.h("Новые жесты"))
        body.addView(ui.note("Сложные жесты открываются после уроков. Включены: " +
            (prefs.gestures.map { mapOf("space" to "смахи по пробелу", "hold" to "удержание (ё)", "cursor" to "курсор", "bkspword" to "стереть слово по ⌫")[it] }.joinToString(", ").ifEmpty { "пока только основные" }) + "."))
        if (prefs.gestures != Prefs.GESTURES) body.addView(ui.button("Включить все жесты") { prefs.gestures = Prefs.GESTURES; (it as Button).text = "✓ Все жесты включены" })

        body.addView(ui.h("Для опытных"))
        body.addView(ui.onOff("Толчки", { prefs.mode == "jabs" }) { prefs.mode = if (it) "jabs" else "pulse"; more() })
        body.addView(ui.note(Prefs.JABS_NOTE))
        if (prefs.jabsWords >= 10 || prefs.mode == "petals") {
            body.addView(ui.onOff("Лепестки", { prefs.mode == "petals" }) { prefs.mode = if (it) "petals" else "jabs"; more() })
            body.addView(ui.note(Prefs.PETALS_NOTE))
        }

        body.addView(ui.h("Жесты — справка"))
        body.addView(ui.text(
            "Пульс: прямой смах — крупная буква; смах с поворотом — мелкая снаружи; туда и обратно — мелкая у центра; касание в центре — пробел. " +
            "Задержи палец в конце: е → ё, ь → ъ, ? → !.\n\n" +
            "Толчки: палец не отрываешь — толчок к букве и назад = буква; поднял палец — пробел; двойная буква — два толчка.\n\n" +
            "Два касания: коснись лепестка — он раскроется крупно, коснись буквы; центр — пробел или закрыть.\n\n" +
            "Пробел: смах наружу — точка, внутрь — запятая, вниз по дуге — стереть слово, вверх — лучшая подсказка; удержи и веди — курсор.\n" +
            "⌫ — вдоль правого края: удержание стирает подряд, смах к звезде — слово. ⌫ сразу после действия клавиатуры — отменить его.\n" +
            "Удержи RU/EN — список клавиатур; удержи ⌄ — эти настройки.", 15f))

        body.addView(ui.h("О программе"))
        body.addView(ui.note("Пульк ${packageManager.getPackageInfo(packageName, 0).versionName}. Бесплатно, без рекламы и без интернета.\nСвободная программа, GPL-3.0.\nСловари: FrequencyWords (Hermit Dave), CC BY-SA 4.0. Следующее слово: предложения Tatoeba (tatoeba.org), CC BY 2.0 FR. Фильтр дрожи: 1€ filter (Casiez, Roussel, Vogel, 2012)."))
        body.addView(ui.h("Передать устройство"))
        body.addView(ui.note("Для центра, где планшет переходит от ребёнка к ребёнку. Сотрёт всё, что хранит Пульк: настройки, фразы, «Про меня», слова семьи и фото, разговор, журнал, капли и выученные слова. Как после установки."))
        var sure = false
        body.addView(ui.button("Передать устройство чистым") {
            val b = it as Button
            if (!sure) { sure = true; b.text = "Точно стереть всё? Нажмите ещё раз"; b.setTextColor(ui.urgent); return@button }
            Clean.all(this)
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        })
        body.addView(ui.button("Проверка на телефоне и «Сообщить о проблеме»") { startActivity(Intent(this, CheckActivity::class.java)) })
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Назад", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }

    companion object { const val MORE = "more" }
}
