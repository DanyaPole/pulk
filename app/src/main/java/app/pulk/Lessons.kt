package app.pulk

/**
 * The «Пульс» lessons, as in the web version: four short levels, each a few steps.
 * Principles: nothing is ever "wrong" (no red, no error words), success counts with a wide margin,
 * help is offered and can be declined, and steps down by itself after a success.
 */
object Lessons {
    val LEVELS = listOf("Первые смахи", "Мелкие буквы", "Слова", "Не глядя")
    val ABOUT = listOf(
        "Крупные буквы и пробел: один прямой смах.",
        "Поворот и «туда и обратно» — мелкие буквы. Удержание — ё.",
        "Целые слова из того, что уже умеешь.",
        "Буквы скрыты: печатаешь на слух и на ощупь.",
    )
    val HELP_NAMES = listOf("", "подсказка", "пример", "рука в руке")

    /** One step: [want] is a single letter (or " "), or a whole [word]. */
    data class Step(val say: String, val want: String? = null, val word: String? = null)

    private val DIRS = listOf("вправо", "вправо-вверх", "вверх", "влево-вверх", "влево", "влево-вниз", "вниз", "вправо-вниз")

    /** Direction on the screen, for a direction of the right-hand geometry. The left hand sees it mirrored. */
    fun dir(angle: Int, left: Boolean): String {
        var a = ((angle % 360) + 360) % 360
        if (left) a = ((180 - a) % 360 + 360) % 360
        return DIRS[(a + 22) / 45 % 8]
    }

    /** How to type [ch], in words: «смахни вверх», «смахни вправо и поверни вверх»… */
    fun how(ch: String, lang: String, left: Boolean): String? {
        if (ch == " ") return "коснись центра звезды"
        val t = Pulse.table(lang, false)
        val w = ch.lowercase()
        val base = Pulse.ALT.entries.firstOrNull { it.value == w }?.key ?: w
        for (p in 0 until 8) for ((i, k) in Pulse.KINDS.withIndex()) if (t[p][i] == base) {
            val d = dir(p * 45, left)
            val s = when (k) {
                'c' -> "смахни $d"
                'l' -> "смахни $d и поверни ${dir(p * 45 + 90, left)}"
                'r' -> "смахни $d и поверни ${dir(p * 45 - 90, left)}"
                else -> "смахни $d и верни палец назад"
            }
            return if (base != w) "$s и задержи палец" else s
        }
        return null
    }

    fun steps(level: Int, lang: String, left: Boolean): List<Step> {
        val t = Pulse.table(lang, false)
        fun c(p: Int) = Step("Смахни ${dir(p * 45, left)} — получится «${t[p][0]}».", want = t[p][0])
        fun turn(p: Int, k: Int) = Step("Смахни ${dir(p * 45, left)} к «${t[p][0]}» и сразу поверни ${dir(p * 45 + if (k == 1) 90 else -90, left)} — получится «${t[p][k]}».", want = t[p][k])
        fun back(p: Int) = Step("Смахни ${dir(p * 45, left)} к «${t[p][0]}» и верни палец назад — получится «${t[p][3]}».", want = t[p][3])
        return when (level) {
            0 -> listOf(
                Step("Поставь палец в любое место светлой зоны и смахни ${dir(90, left)} — получится «${t[2][0]}».", want = t[2][0]),
                c(1), c(5), c(0),
                Step("Коснись центра звезды — это пробел.", want = " "),
            )
            1 -> listOf(
                turn(0, 1), turn(0, 2), turn(2, 1), back(1),
                if (lang == "ru") Step("Смахни ${dir(225, left)} к «е» и задержи палец, пока в центре не появится «ё».", want = "ё") else c(5),
            )
            2 -> (if (lang == "ru") listOf("сон", "тесто", "мама", "привет") else listOf("sea", "tea", "notes", "hello"))
                .map { Step("Набери слово «$it». Если сбился — сотри ⌫, это нормально.", word = it) }
            else -> (if (lang == "ru") listOf("сон", "мама", "привет") else listOf("sea", "mama", "hello")).mapIndexed { i, w ->
                Step(if (i == 0) "Буквы на звезде скрыты: слушай, а не смотри. Набери «$w». Каждая буква отзывается звуком и вибрацией; пробел — касание в любом месте."
                     else "Набери «$w», не глядя на звезду.", word = w)
            }
        }
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

    fun editDist(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val tmp = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = tmp }
        }
        return d[b.length]
    }

    /** A wide margin: one slip is still a success (two for long phrases). */
    fun closeEnough(typed: String, want: String): Boolean {
        val a = norm(typed); val b = norm(want)
        return a == b || editDist(a, b) <= (if (b.length >= 20) 2 else 1)
    }

    sealed class Verdict { object Done : Verdict(); object Going : Verdict(); data class Off(val got: String) : Verdict() }

    /** What the learner's text means for the step: done, on the way, or something else came out. */
    fun check(step: Step, text: String): Verdict {
        if (step.want != null) {
            if (text.isEmpty()) return Verdict.Going
            if (step.want == " ") return if (text.endsWith(" ")) Verdict.Done else Verdict.Off(text.takeLast(1))
            return if (text.takeLast(1).lowercase() == step.want) Verdict.Done else Verdict.Off(text.takeLast(1))
        }
        val w = step.word ?: return Verdict.Going
        val typed = text.trimEnd()
        if (norm(typed) == norm(w)) return Verdict.Done
        if ((text.endsWith(" ") || typed.length >= w.length) && closeEnough(typed, w)) return Verdict.Done
        if (norm(w).startsWith(norm(typed))) return Verdict.Going
        return if (typed.length >= w.length) Verdict.Off(typed) else Verdict.Going
    }

    /** The next letter of a word step, while the text so far is right. */
    fun next(step: Step, text: String): String? {
        step.want?.let { return it }
        val w = step.word ?: return null
        val typed = text.trimEnd()
        if (!norm(w).startsWith(norm(typed))) return null
        return if (typed.length < w.length) w[typed.length].toString() else " "
    }
}
