package app.pulk

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The numbers of «Проверка скорости». Pure Kotlin, tested on the computer. */
object Speed {
    /** Everyday phrases, short, without rare letters — for the first measures. */
    val PHRASES = listOf(
        "привет как дела", "скоро буду дома", "спасибо за помощь", "давай завтра утром", "я уже еду",
        "напиши мне вечером", "всё хорошо не волнуйся", "купи хлеба и молока", "до встречи в пять", "это очень удобно",
    )
    fun pick(n: Int = 3, seed: Long = System.nanoTime()) = PHRASES.shuffled(java.util.Random(seed)).take(n)

    data class Run(val want: String, val typed: String, val ms: Long, val erases: Int)
    data class Result(val date: String, val mode: String, val cpm: Int, val accuracy: Int, val erases: Int) {
        fun line() = "$date|$mode|$cpm|$accuracy|$erases"
    }

    fun summary(runs: List<Run>, mode: String, now: Date = Date()): Result {
        val chars = runs.sumOf { it.typed.length }
        val ms = runs.sumOf { it.ms }.coerceAtLeast(1)
        val cpm = (chars * 60000.0 / ms).toInt()
        val want = runs.sumOf { it.want.length }.coerceAtLeast(1)
        val errors = runs.sumOf { Lessons.editDist(it.typed.lowercase().replace('ё', 'е'), it.want) }
        val acc = (100.0 * (1 - errors.toDouble() / want)).coerceIn(0.0, 100.0).toInt()
        return Result(SimpleDateFormat("d.MM HH:mm", Locale("ru")).format(now), mode, cpm, acc, runs.sumOf { it.erases })
    }

    fun parse(line: String): Result? {
        val p = line.split('|'); if (p.size != 5) return null
        return Result(p[0], p[1], p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null, p[4].toIntOrNull() ?: 0)
    }

    fun modeName(m: String) = when (m) { "jabs" -> "Толчки"; "taps" -> "Два касания"; else -> "Пульс" }

    /** What the person sends: plain text, nothing typed in it but the test phrases. */
    fun report(history: List<String>) = "Пульк — проверка скорости\n" +
        history.mapNotNull { parse(it) }.joinToString("\n") { "${it.date}: ${modeName(it.mode)}, ${it.cpm} зн/мин, точность ${it.accuracy} %, стираний ${it.erases}" }
}
