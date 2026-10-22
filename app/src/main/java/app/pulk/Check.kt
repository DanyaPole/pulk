package app.pulk

/**
 * «Проверка на этом телефоне»: what to try with one's hands on a real phone before version 1.0, and the report
 * «Сообщить о проблеме» sends — the version, the phone and the settings, never anything typed.
 */
object Check {
    class Item(val id: String, val what: String, val how: String)

    val ITEMS = listOf(
        Item("on", "Включается и выбирается", "Главный экран → «Включить клавиатуру», потом «Выбрать Пульк». Вверху — «✓ Пульк включён и выбран»."),
        Item("opens", "Появляется в других приложениях", "Открой заметки или мессенджер и коснись поля: снизу звезда, звучит «пульк»."),
        Item("type", "Печатает", "Смах — буква, касание центра — пробел, ⌫ стирает. Напиши «привет мама»."),
        Item("buzz", "Вибрация", "При каждой букве лёгкий толчок (если вибрация включена)."),
        Item("words", "Подсказки слов", "Над звездой появляются слова; касание подставляет слово."),
        Item("password", "Поле пароля", "В поле пароля буквы не называются и не звучат по-разному — у всех один тихий щелчок; подсказок слов нет; два пробела остаются пробелами, точка жестом — без пробела."),
        Item("turn", "Поворот экрана", "Поверни телефон боком и обратно: клавиатура на месте, набранное не пропало."),
        Item("away", "Свернуть и вернуться", "В «Говорить» набери фразу, выйди на рабочий стол и вернись: фраза на месте."),
        Item("speak", "Голос", "«Говорить» → набери фразу → «Сказать». Голос русский, понятный."),
        Item("hand", "Под мою руку", "Главный экран → «Под мою руку»: проходится до конца, без вылета."),
        Item("lesson", "Урок", "«Обучение» → «Начать»: не та буква не печатается, после трёх попыток Пульк помогает."),
        Item("talkback", "TalkBack (если есть)", "С TalkBack буквы и кнопки называются вслух."),
        Item("buttons", "Внешняя кнопка (если есть)", "Клавиатура, джойстик или кнопка по Bluetooth: нажатия доходят."),
    )

    /** What is marked: id → ok · no · skip; stored as "id=ok,id=no". */
    fun decode(s: String): Map<String, String> =
        s.split(",").mapNotNull { p -> p.split("=").takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] } }.toMap()
    fun encode(m: Map<String, String>) = m.entries.filter { it.value.isNotEmpty() }.joinToString(",") { "${it.key}=${it.value}" }

    /** The phone and the app, as the report shows them. */
    class Phone(val version: String, val maker: String, val model: String, val android: String, val screen: String, val enabled: Boolean, val selected: Boolean, val talkBack: Boolean)

    /** The report: no phrases, names, conversation, journal or learned words — only how Пульк is set up and what was marked. */
    fun report(p: Phone, prefs: Prefs, marks: Map<String, String>): String {
        val b = StringBuilder()
        b.append("Пульк ${p.version}\n")
        b.append("Телефон: ${p.maker} ${p.model}, Android ${p.android}, экран ${p.screen}\n")
        b.append("Клавиатура: ${if (p.enabled) "включена" else "не включена"}, ${if (p.selected) "выбрана" else "не выбрана"}; TalkBack ${if (p.talkBack) "вкл" else "выкл"}\n")
        b.append("\nНастройки: способ ${prefs.mode}, рука ${prefs.hand}, место ${prefs.zone}, звук ${prefs.sound}, вибрация ${onOff(prefs.haptics)}, " +
            "вслепую ${onOff(prefs.blind)}, компас ${prefs.compass}, размер ${prefs.size}%, тема ${prefs.theme}, вид ${prefs.look}, простой ${onOff(prefs.simple)}, " +
            "подсказки ${onOff(prefs.suggest)}, следующее слово ${onOff(prefs.predict)}, автоисправление ${onOff(prefs.autocorrect)}, запоминать ${onOff(prefs.learn)}, " +
            "окно текста ${onOff(prefs.textWindow)}, под руку ${onOff(prefs.calibrated)}, защита касаний ${prefs.guard}, спокойно ${onOff(prefs.calm)}, " +
            "жесты ${prefs.gestures.sorted().joinToString("+").ifEmpty { "основные" }}, сканирование ${prefs.scanMs} мс, скорость речи ${prefs.speakRate}\n")
        b.append("\nПроверка:\n")
        for (i in ITEMS) b.append("${when (marks[i.id]) { "ok" -> "✓"; "no" -> "✗"; "skip" -> "–"; else -> "·" }} ${i.what}\n")
        b.append("\n(✓ работает, ✗ не работает, – пропущено, · не проверено)\nЧто случилось — напишите ниже своими словами:\n")
        return b.toString()
    }
    private fun onOff(v: Boolean) = if (v) "вкл" else "выкл"
}
