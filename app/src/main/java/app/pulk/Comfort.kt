package app.pulk

/**
 * «Подстроить под человека» (as the web version): five doors, asked as difficulties, never as diagnoses.
 * «Не знаю» turns on only what never gets in the way. The answers are kept, so «Удобство» can say what is on and
 * the questions can be gone through again. The same five doors group the «Удобство» screen.
 */
object Comfort {
    const val UNKNOWN = -1
    data class Door(val key: String, val title: String, val note: String, val answers: List<Pair<String, Int>>)
    val DOORS = listOf(
        Door("hands", "Рука дрожит, задевает экран или быстро устаёт?", "Подстроим, как звезда принимает касания.",
            listOf("Нет" to 0, "Немного" to 1, "Да, смахивать трудно" to 2, "Не знаю" to UNKNOWN)),
        Door("eyes", "Трудно видеть буквы на экране?", "Сделаем крупнее и контрастнее, а если не видно совсем — озвучим.",
            listOf("Нет" to 0, "Да" to 1, "Совсем не видно" to 2, "Не знаю" to UNKNOWN)),
        Door("speech", "Говорить голосом трудно?", "Тогда главное — «Говорить»: телефон произносит набранное.",
            listOf("Нет" to 0, "Да" to 1, "Не знаю" to UNKNOWN)),
        Door("calm", "Мешают лишние кнопки, звуки, движение?", "Оставим только нужное, звук и голос — мягче.",
            listOf("Нет" to 0, "Да" to 1, "Не знаю" to UNKNOWN)),
        Door("helper", "Рядом будет взрослый, который помогает?", "Покажем ему минутную памятку.",
            listOf("Да" to 1, "Нет" to 0)),
    )

    /** «hands=2;eyes=0;…» in the preferences; «quick» for «Начать сразу». */
    fun encode(a: Map<String, Int>) = a.entries.joinToString(";") { "${it.key}=${it.value}" }
    fun decode(s: String): Map<String, Int> = if (s.isEmpty() || s == "quick") emptyMap()
        else s.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap()

    /** What each answer turns on. */
    fun apply(p: Prefs, a: Map<String, Int>) {
        when (a["hands"]) {
            1 -> { p.guard = maxOf(p.guard, 1); if (p.smooth == 0f) p.smooth = 0.55f }
            2 -> { p.guard = 2; p.smooth = maxOf(p.smooth, 0.75f); p.size = maxOf(p.size, 112); p.mode = "taps" }   // without strokes: touch the petal, then the letter
            UNKNOWN -> p.guard = maxOf(p.guard, 1)
        }
        when (a["eyes"]) {
            1 -> { p.size = maxOf(p.size, 112); p.theme = "contrast" }
            2 -> { p.blind = true; if (p.sound == "off") p.sound = "voice"; if (p.compass == "off") p.compass = "voice" }
        }
        if (a["calm"] == 1) { p.simple = true; p.calm = true }
        p.doors = encode(a)
    }

    /** What is on now, in words: the summary after the doors and the head of «Удобство». */
    fun summary(p: Prefs): List<String> {
        val on = ArrayList<String>()
        if (p.left) on.add("левая рука")
        if (p.guard > 0) on.add("защита от случайных касаний" + if (p.guard == 2) " (сильная)" else "")
        if (p.smooth > 0f) on.add("сглаживание дрожи")
        if (p.calibrated) on.add("подстроено под руку")
        when (p.mode) { "taps" -> on.add("набор касаниями"); "jabs" -> on.add("«Толчки»"); "scan" -> on.add("сканирование"); "classic" -> on.add("привычная клавиатура") }
        if (p.size > 100) on.add("крупная звезда")
        if (p.contrast) on.add("контрастная тема")
        if (p.blind) on.add("набор вслепую")
        if (p.compass != "off") on.add("звуковой компас")
        if (p.simple) on.add("простой режим")
        if (p.calm) on.add("спокойный режим в «Говорить»")
        if (p.speakWords) on.add("говорить по словам")
        if (p.pitch > 1f) on.add("голос моложе")
        return on
    }
}
