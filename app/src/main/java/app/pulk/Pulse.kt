package app.pulk

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The «Пульс» letter table and the stroke recogniser, ported from the web prototype (duga.html) so the
 * keyboard on the phone behaves exactly like the one people already tried.
 * All coordinates are "base units": the thumb's corner is (0, 0), x goes left to the screen's middle
 * for the right hand, y goes up. Pure Kotlin, no Android classes, so it is tested on the computer.
 */
object Pulse {
    const val R = 96.0              // star radius
    const val PAD = 158.0           // the star zone starts beyond the service rings
    const val OUTER = 300.0         // and ends just past the star
    const val P_MIN = 16.0          // shorter than this = a tap
    const val P_TURN = 14.0         // the turn after the first stroke must be at least this long
    const val GAP_MIN = 14.0        // space-bar gesture length

    private const val HOME_R = 262.0
    private const val HOME_A = 135.0
    val PH = doubleArrayOf(HOME_R * cos(HOME_A * PI / 180), HOME_R * sin(HOME_A * PI / 180)) // star centre

    /** Kinds of letter in a petal: c = straight, l = turn left, r = turn right, b = out and back. */
    val KINDS = charArrayOf('c', 'l', 'r', 'b')
    private val SPOT = mapOf('c' to (52.0 to 0.0), 'l' to (86.0 to 14.0), 'r' to (86.0 to -14.0), 'b' to (24.0 to 0.0))

    /** Petals, counter-clockwise from "right" every 45° (right-hand geometry), each: c, l, r, b. */
    private val RU = arrayOf(
        arrayOf("н", "м", "ь", "щ"), arrayOf("о", "у", "б", "ю"), arrayOf("а", "в", "я", "ф"), arrayOf("с", "з", "ч", "ш"),
        arrayOf("и", "ы", "й", "?"), arrayOf("е", "к", "г", "э"), arrayOf("т", "д", "п", "ц"), arrayOf("р", "л", "х", "ж"),
    )
    private val EN = arrayOf(
        arrayOf("o", "u", "f", ":"), arrayOf("e", "d", "w", "z"), arrayOf("t", "h", "p", "-"), arrayOf("s", "b", "v", "q"),
        arrayOf("i", "c", "g", "'"), arrayOf("a", "l", "y", "?"), arrayOf("n", "m", "k", ""), arrayOf("r", "x", "j", ""),
    )
    /** The second layer: digits and symbols, same gestures. */
    private val SYM = arrayOf(
        arrayOf("1", "9", "(", "@"), arrayOf("2", "0", ")", "#"), arrayOf("3", ".", ",", "~"), arrayOf("4", "?", "!", ":"),
        arrayOf("5", "-", "_", ";"), arrayOf("6", "'", "\"", "/"), arrayOf("7", "+", "=", "%"), arrayOf("8", "*", "&", "$"),
    )
    /** Hold the finger still at the end of a stroke to get the alternative. */
    val ALT = mapOf("е" to "ё", "ь" to "ъ", "?" to "!", "." to "…", "-" to "—")

    fun table(lang: String, symbols: Boolean): Array<Array<String>> = if (symbols) SYM else if (lang == "ru") RU else EN

    data class Slot(val petal: Int, val kind: Char, val v: String) {
        val isSpace get() = petal < 0
    }
    val SPACE = Slot(-1, 's', " ")

    fun spot(petal: Int, kind: Char): DoubleArray {
        val (r, da) = SPOT.getValue(kind)
        val a = (petal * 45 + da) * PI / 180
        return doubleArrayOf(PH[0] + r * cos(a), PH[1] + r * sin(a))
    }

    fun petalOf(x: Double, y: Double): Int = (((atan2(y, x) / (PI / 4)).roundToInt() % 8) + 8) % 8

    private fun slot(t: Array<Array<String>>, p: Int, kind: Char): Slot {
        val i = KINDS.indexOf(kind)
        val v = t[p][i]
        return if (v.isNotEmpty()) Slot(p, kind, v) else Slot(p, 'c', t[p][0])
    }

    /** A tap: the letter whose label is nearest; the centre and anything outside the star is space. */
    fun tap(t: Array<Array<String>>, x: Double, y: Double, blind: Boolean): Slot {
        if (blind) return SPACE // eyes-free: a tap anywhere is a space
        val d = hypot(x - PH[0], y - PH[1])
        if (d < 15 || d > R + 10) return SPACE
        var best: Slot? = null
        var bd = Double.MAX_VALUE
        for (p in 0 until 8) for (k in KINDS) {
            val v = t[p][KINDS.indexOf(k)]
            if (v.isEmpty()) continue
            val s = spot(p, k)
            val dd = hypot(x - s[0], y - s[1])
            if (dd < bd) { bd = dd; best = Slot(p, k, v) }
        }
        return best!!
    }

    /** A stroke (flat base-unit points, the first is where the finger landed): straight, turn, or out-and-back. */
    fun resolve(t: Array<Array<String>>, pts: DoubleArray, blind: Boolean = false): Slot {
        val ox = pts[0]; val oy = pts[1]; val n = pts.size / 2
        fun px(i: Int) = pts[2 * i] - ox
        fun py(i: Int) = pts[2 * i + 1] - oy
        var far = 0.0; var fi = 0
        for (i in 1 until n) { val d = hypot(px(i), py(i)); if (d > far) { far = d; fi = i } }
        if (far < P_MIN) return tap(t, ox, oy, blind)
        val ex = px(n - 1); val ey = py(n - 1)
        val endD = hypot(ex, ey)
        if (far >= 24 && endD < far * 0.5) return slot(t, petalOf(px(fi), py(fi)), 'b')
        // the corner: the point farthest from the straight line origin → end
        val l = if (endD == 0.0) 1.0 else endD
        val nx = -ey / l; val ny = ex / l
        var dev = 0.0; var ci = 0
        for (i in 1 until n - 1) { val d = abs(px(i) * nx + py(i) * ny); if (d > dev) { dev = d; ci = i } }
        if (dev >= max(7.0, 0.14 * l)) {
            val cx = px(ci); val cy = py(ci)
            var dt = atan2(ey - cy, ex - cx) - atan2(cy, cx)
            dt = atan2(sin(dt), cos(dt))
            if (abs(dt) >= 130 * PI / 180) return slot(t, petalOf(cx, cy), 'b')
            if (abs(dt) >= 30 * PI / 180 && hypot(ex - cx, ey - cy) >= P_TURN) return slot(t, petalOf(cx, cy), if (dt > 0) 'l' else 'r')
        }
        return slot(t, petalOf(ex, ey), 'c')
    }

    const val LIFT_MS = 45L   // as the thumb lifts, its contact rolls back: samples this close to the lift…
    const val LIFT_MAX = 3    // …that move back towards the start (at most this many) are not part of the stroke

    /**
     * The stroke without its lift-off: on a phone the reported point slides back a few millimetres in the last
     * moments as the pad of the thumb leaves the glass, which made a straight stroke read as «out and back».
     * [pts] are flat x,y pairs, [times] the time of each pair, [up] the time the finger left.
     */
    fun trimLift(pts: DoubleArray, times: LongArray, up: Long): DoubleArray {
        var n = pts.size / 2
        fun d(i: Int) = hypot(pts[2 * i] - pts[0], pts[2 * i + 1] - pts[1])
        var cut = 0
        while (n > 2 && cut < LIFT_MAX && up - times[n - 1] <= LIFT_MS && d(n - 1) < d(n - 2)) { n--; cut++ }
        return pts.copyOf(n * 2)
    }

    /** Direction of a drag in the polar axes of the point it started at: c (too short), cw/ccw (along the arc), out/in. */
    fun dirOf(sx: Double, sy: Double, dx: Double, dy: Double, min: Double): String {
        if (hypot(dx, dy) < min) return "c"
        val a = atan2(sy, sx)
        val rad = dx * cos(a) + dy * sin(a)
        val tan = -dx * sin(a) + dy * cos(a)
        return if (abs(tan) >= abs(rad)) (if (tan > 0) "ccw" else "cw") else (if (rad > 0) "out" else "in")
    }

    // ---- the keyboard's service keys, as rings around the thumb's corner ----
    enum class Fn { SHIFT, LANG, SYM, HIDE, BKSP, SPACE, ENTER }
    data class FnKey(val fn: Fn, val r0: Double, val r1: Double, val a0: Double, val a1: Double)
    const val A0 = 91.0
    const val A1 = 179.0

    val FN_KEYS: List<FnKey> = run {
        fun ring(r0: Double, r1: Double, items: List<Pair<Fn, Double>>): List<FnKey> {
            val tw = items.sumOf { it.second }
            var a = A0
            return items.map { (fn, w) -> val k = FnKey(fn, r0, r1, a, a + (A1 - A0) * w / tw); a = k.a1; k }
        }
        ring(30.0, 100.0, listOf(Fn.SHIFT to 1.0, Fn.LANG to 1.0, Fn.SYM to 1.0, Fn.HIDE to 0.85)) +
            ring(100.0, 158.0, listOf(Fn.BKSP to 1.15, Fn.SPACE to 2.6, Fn.ENTER to 1.0))
    }

    /** What the finger landed on: a service key, or the star zone (null). */
    fun hit(x: Double, y: Double): FnKey? {
        val r = hypot(x, y)
        if (r >= PAD) return null
        var a = atan2(y, x) * 180 / PI
        a = a.coerceIn(A0 + 0.01, A1 - 0.01)
        val ring = if (r < 100.0) 30.0 else 100.0
        return FN_KEYS.filter { it.r0 == ring }.firstOrNull { a >= it.a0 && a < it.a1 } ?: FN_KEYS.filter { it.r0 == ring }.last()
    }
}
