package app.pulk

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Fitting the keyboard to one hand, from 30 seconds of the person's own movements:
 * holding the thumb still shows its tremor, sixteen strokes show how long and in which real directions it goes.
 * Pure Kotlin, tested on the computer.
 */
object Calibration {
    /** One stroke asked for [petal]: its way out in base units, right-hand geometry (x right, y up). */
    data class Stroke(val petal: Int, val dx: Double, val dy: Double)

    data class Result(val tol: Double, val smooth: Double, val bias: DoubleArray, val tremorMm: Double, val lengthBase: Double)

    const val TEXTBOOK = 45.0       // the stroke length the recogniser is tuned for
    const val MAX_BIAS = 18.0       // so neighbouring directions never swap places

    /** How much the finger wanders while held still: the spread of its positions, in mm. */
    fun tremorMm(xsDp: List<Double>, ysDp: List<Double>): Double {
        if (xsDp.size < 3) return 0.0
        val mx = xsDp.average(); val my = ysDp.average()
        val v = xsDp.indices.sumOf { (xsDp[it] - mx) * (xsDp[it] - mx) + (ysDp[it] - my) * (ysDp[it] - my) } / xsDp.size
        return sqrt(v) * 0.15875 // 1 dp = 1/160 inch
    }

    fun smoothFor(mm: Double) = when { mm < 0.25 -> 0.0; mm < 0.6 -> 0.35; mm < 1.2 -> 0.55; else -> 0.7 }

    fun compute(tremorMm: Double, strokes: List<Stroke>): Result {
        val lens = strokes.map { hypot(it.dx, it.dy) }.sorted()
        val median = if (lens.isEmpty()) TEXTBOOK else lens[lens.size / 2]
        val tol = (median / TEXTBOOK).coerceIn(0.6, 1.8)
        val bias = DoubleArray(8)
        for (p in 0 until 8) {
            val mine = strokes.filter { it.petal == p }
            if (mine.isEmpty()) continue
            // the average way this thumb goes when it means p, as an angle off p·45°
            val off = mine.map { s ->
                var d = atan2(s.dy, s.dx) * 180 / PI - p * 45
                while (d > 180) d -= 360; while (d < -180) d += 360; d
            }.average()
            bias[p] = off.coerceIn(-MAX_BIAS, MAX_BIAS)
        }
        return Result(tol, smoothFor(tremorMm), bias, tremorMm, median)
    }

    /** In words, for the person: what the keyboard now knows about their hand. */
    fun describe(r: Result): List<String> {
        val len = when { r.tol < 0.85 -> "короткие — клавиатура станет чувствительнее"; r.tol > 1.2 -> "длинные — мелкие случайные движения не будут буквами"; else -> "обычной длины" }
        val shake = when { r.smooth == 0.0 -> "почти нет — фильтр не нужен"; r.smooth < 0.5 -> "лёгкая — включён мягкий фильтр"; r.smooth < 0.65 -> "заметная — включён фильтр"; else -> "сильная — включён сильный фильтр" }
        val worst = r.bias.withIndex().maxByOrNull { kotlin.math.abs(it.value) }
        val aim = if (worst == null || kotlin.math.abs(worst.value) < 6) "почти точно по направлениям" else "палец уводит до ${kotlin.math.abs(worst.value).toInt()}° — границы направлений подстроены"
        return listOf("Смахи: $len.", "Дрожь: $shake.", "Направления: $aim.")
    }

    /** The order to ask for directions: each twice, shuffled so the hand does not just repeat itself. */
    fun plan(seed: Long = System.nanoTime()): List<Int> = (0 until 8).flatMap { listOf(it, it) }.shuffled(java.util.Random(seed))
}
