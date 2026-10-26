package app.pulk

/** Short, familiar words from the existing web word bubbles; shared through build-web.js. */
object BubbleWords {
    val WORDS = listOf("нос", "сон", "рот", "сто", "три", "рис", "она", "они", "нет", "торт", "нора",
        "шар", "нож", "щит", "щи", "жена", "шина", "отец", "цена", "это", "шарф", "жара", "наш",
        "да", "мама", "папа", "дом", "кот", "мяч", "сок", "суп", "хлеб", "вода", "лук", "чай", "мир", "лес", "день", "ночь", "утро", "рука", "небо", "друг")

    fun pool(level: Int) = WORDS.filter { w -> w.all { it.toString() in BubblesActivity.pool(level) } }
    fun next(level: Int, stats: Map<String, IntArray>, prev: String, r: Double): String {
        val ws = pool(level).filter { it != prev }.ifEmpty { pool(0).filter { it != prev } }.ifEmpty { return prev }
        val weights = ws.map { w -> 1.0 + 3.0 * w.map { c -> val v = stats[c.toString()] ?: intArrayOf(0, 0); v[1].toDouble() / (v[0] + v[1] + 1) }.average() }
        var x = r * weights.sum()
        for (i in ws.indices) { x -= weights[i]; if (x <= 0) return ws[i] }
        return ws.last()
    }

    /** No lift operation: accepted letters survive any number of touches. */
    class Progress(val word: String) {
        var done = 0; private set
        var misses = 0; private set
        val complete get() = done == word.length
        val next get() = word.getOrNull(done)?.toString()
        fun add(got: String, help: Boolean): Boolean {
            if (complete) return false
            if (got == next) { done++; misses = 0; return true }
            misses++
            if (help && misses >= 3) { done++; misses = 0 }
            return false
        }
    }
}
