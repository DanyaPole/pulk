package app.pulk

/**
 * «Говорить»: whole phrases for a person who types instead of speaking. Ready phrases by situation, and the
 * person's own past phrases; a few first letters bring the whole phrase. Pure Kotlin, tested on the computer.
 */
object Phrases {
    /** Said with one touch, at any moment of a conversation. */
    val QUICK = listOf("Да", "Нет", "Подожди, я печатаю", "Повтори, пожалуйста", "Спасибо", "Не знаю")

    /** Ready phrases by situation. */
    val SITUATIONS: List<Pair<String, List<String>>> = listOf(
        "Общение" to listOf("Привет", "Как дела?", "Я не могу говорить, но всё понимаю", "Я отвечу текстом, подожди немного",
            "Говори, пожалуйста, медленнее", "Мне нужна минутка", "Хорошо, договорились", "До встречи"),
        "Магазин" to listOf("Сколько это стоит?", "Дайте, пожалуйста, это", "Можно оплатить картой?", "Пакет не нужен", "Где здесь касса?", "Спасибо, это всё"),
        "Врач" to listOf("У меня болит здесь", "Боль сильная", "Боль несильная", "Это началось вчера", "У меня аллергия", "Я не могу говорить, буду отвечать текстом"),
        "Транспорт" to listOf("Мне нужно сюда", "Остановите, пожалуйста, здесь", "Сколько стоит проезд?", "Это далеко?", "Подскажите, как пройти"),
        "Дом" to listOf("Я голоден", "Хочу пить", "Мне холодно", "Мне жарко", "Я устал", "Позови, пожалуйста", "Всё хорошо"),
        "Срочно" to listOf("Мне нужна помощь", "Вызовите скорую", "Мне плохо", "Позвоните моим близким"),
    )

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

    /**
     * Whole phrases that begin like what is typed (at least two letters): the person's own first (most recent first),
     * then the ready ones. A phrase also matches when typed letters start any of its words in order («ск ст» → «Сколько это стоит?»).
     */
    fun complete(typed: String, history: List<String>, n: Int = 3): List<String> {
        val t = norm(typed)
        if (t.length < 2) return emptyList()
        val all = (history + SITUATIONS.flatMap { it.second } + QUICK).distinctBy { norm(it) }
        val starts = all.filter { norm(it).startsWith(t) && norm(it) != t }
        val byWords = all.filter { it !in starts && wordsMatch(norm(it), t) }
        return (starts + byWords).take(n)
    }

    /** Each typed piece starts a word of the phrase, in order: a shorthand for long phrases. */
    private fun wordsMatch(phrase: String, typed: String): Boolean {
        val pieces = typed.split(' ').filter { it.isNotEmpty() }
        if (pieces.size < 2) return false
        val words = phrase.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        var w = 0
        for (p in pieces) {
            while (w < words.size && !words[w].startsWith(p)) w++
            if (w == words.size) return false
            w++
        }
        return true
    }

    /** The history: newest first, no repeats, a sane length. */
    fun remember(history: List<String>, said: String, max: Int = 200): List<String> {
        val s = said.trim(); if (s.isEmpty()) return history
        return (listOf(s) + history.filter { norm(it) != norm(s) }).take(max)
    }
}
