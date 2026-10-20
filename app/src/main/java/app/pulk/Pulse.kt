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

    /**
     * This person's own aim, from the calibration: how many degrees off each direction their thumb really goes
     * (0 = the textbook 45° steps). A stroke belongs to the direction it is closest to.
     */
    val bias = DoubleArray(8)

    fun petalOf(x: Double, y: Double): Int {
        val a = atan2(y, x) * 180 / PI
        var best = 0; var bd = Double.MAX_VALUE
        for (p in 0 until 8) {
            var d = (a - (p * 45 + bias[p])) % 360; if (d > 180) d -= 360; if (d < -180) d += 360
            if (abs(d) < bd) { bd = abs(d); best = p }
        }
        return best
    }

    /** Pixels-per-base-unit of the keyboard, in dp: the same everywhere (keyboard, calibration). */
    fun scaleDp(wdp: Float, vhdp: Float, size: Int, blind: Boolean): Float {
        val r = OUTER.toFloat()
        var kd = minOf(wdp, 440f) / (r * 1.047f) * size / 100f * (if (blind) 1.1f else 1f)
        kd = minOf(kd, (wdp + 4f - 6f) / r)
        kd = minOf(kd, vhdp * (if (blind) 0.5f else 0.42f) / (r + 4f))
        return kd
    }

    /**
     * A steadier hand: tremor is smoothed with the 1€ filter (Casiez, Roussel, Vogel, CHI 2012) — strong smoothing
     * while the finger moves slowly or rests (where tremor shows), almost none in a quick stroke, so strokes do not lag.
     * Points are also measured against this person's stroke length: [tol] above 1 means the thumb's natural strokes are
     * long, so short jitter must not count; below 1, short strokes are enough.
     */
    class Steady(private val ox: Double, private val oy: Double, private val tol: Double, smooth: Double) {
        private val on = smooth > 0.01
        private val minCut = when { smooth < 0.45 -> 3.0; smooth < 0.62 -> 1.5; else -> 0.8 }   // Hz
        private val fx = OneEuro(minCut, BETA); private val fy = OneEuro(minCut, BETA)
        fun at(x: Double, y: Double, tMs: Long): DoubleArray {
            val sx = if (on) fx.f(x, tMs) else x; val sy = if (on) fy.f(y, tMs) else y
            return doubleArrayOf(ox + (sx - ox) / tol, oy + (sy - oy) / tol)
        }
    }
    const val BETA = 0.02   // how fast the smoothing lets go with speed (base units per second)

    /** One coordinate of the 1€ filter. */
    class OneEuro(private val minCut: Double, private val beta: Double, private val dCut: Double = 1.0) {
        private var x0 = Double.NaN; private var dx0 = 0.0; private var t0 = 0L
        private fun alpha(cut: Double, dt: Double): Double { val tau = 1 / (2 * PI * cut); return 1 / (1 + tau / dt) }
        fun f(x: Double, tMs: Long): Double {
            if (x0.isNaN()) { x0 = x; t0 = tMs; return x }
            val dt = maxOf(1L, tMs - t0) / 1000.0; t0 = tMs
            val dx = (x - x0) / dt
            dx0 += alpha(dCut, dt) * (dx - dx0)
            val cut = minCut + beta * abs(dx0)
            x0 += alpha(cut, dt) * (x - x0)
            return x0
        }
    }

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

    // ---- «Толчки»: a whole word without lifting the finger ----
    const val JAB_OUT = 14.0    // the finger has left home: a jab begins
    const val JAB_HOME = 10.0   // it is back home (or within 40% of how far it went): the jab is a letter
    const val JAB_SHORT = 30.0  // a short straight jab is the small letter near the centre, a long one the big letter

    /**
     * Cuts one touch into jabs: from home out towards a petal and back. Each return home is a letter, so a
     * double letter is simply two jabs the same way. Home follows the finger after each jab, so the thumb may drift.
     */
    class Jabs(private var ax: Double, private var ay: Double) {
        private val cur = ArrayList<Double>()
        private var far = 0.0
        // after a jab the finger is still coming back: home settles where it comes closest to the old home
        private var settling = false
        private var hx = 0.0; private var hy = 0.0; private var best = 0.0
        val isOut get() = cur.isNotEmpty()
        val home get() = doubleArrayOf(ax, ay)
        fun current(): DoubleArray? = if (cur.isEmpty()) null else cur.toDoubleArray()
        /** The points of a jab that has just come back home, or null. */
        fun add(x: Double, y: Double): DoubleArray? {
            if (settling) {
                val d0 = hypot(x - hx, y - hy)
                if (d0 <= best + 0.8) { if (d0 < best) best = d0; ax = x; ay = y; return null }
                settling = false
            }
            val d = hypot(x - ax, y - ay)
            if (cur.isEmpty()) { if (d < JAB_OUT) return null; cur.add(ax); cur.add(ay) }
            cur.add(x); cur.add(y); if (d > far) far = d
            if (d < max(JAB_HOME, far * 0.4)) {
                val r = cur.toDoubleArray(); cur.clear(); far = 0.0
                settling = true; hx = ax; hy = ay; best = d; ax = x; ay = y
                return r
            }
            return null
        }
        /** The finger lifted: a jab still on its way out counts too. */
        fun finish(): DoubleArray? = if (cur.isEmpty()) null else cur.toDoubleArray().also { cur.clear(); far = 0.0 }
    }

    /** The letter of one jab: its way out decides, as a «Пульс» stroke would (straight, or with a turn). */
    fun jab(t: Array<Array<String>>, pts: DoubleArray): Slot {
        val ox = pts[0]; val oy = pts[1]; val n = pts.size / 2
        var far = 0.0; var fi = 1
        for (i in 1 until n) { val d = hypot(pts[2 * i] - ox, pts[2 * i + 1] - oy); if (d > far) { far = d; fi = i } }
        val way = pts.copyOf((fi + 1) * 2)
        var s = resolve(t, way)
        if (s.isSpace || s.kind == 'b') s = slot(t, petalOf(pts[2 * fi] - ox, pts[2 * fi + 1] - oy), 'c')
        if (s.kind == 'c' && far < JAB_SHORT) s = slot(t, s.petal, 'b')
        return s
    }

    /** Direction of a drag in the polar axes of the point it started at: c (too short), cw/ccw (along the arc), out/in. */
    fun dirOf(sx: Double, sy: Double, dx: Double, dy: Double, min: Double): String {
        if (hypot(dx, dy) < min) return "c"
        val a = atan2(sy, sx)
        val rad = dx * cos(a) + dy * sin(a)
        val tan = -dx * sin(a) + dy * cos(a)
        return if (abs(tan) >= abs(rad)) (if (tan > 0) "ccw" else "cw") else (if (rad > 0) "out" else "in")
    }

    // ---- the keyboard's service keys ----
    // Nothing small sits in the corner itself, where the thumb has to bend: the corner is one big space and ⏎,
    // ⌫ is a tall key up the right edge (the thumb reaches it by sliding up the edge), and the rare keys
    // line the bottom edge under the star.
    enum class Fn { SHIFT, LANG, SYM, HIDE, BKSP, SPACE, ENTER }
    data class FnKey(val fn: Fn, val r0: Double, val r1: Double, val a0: Double, val a1: Double)
    const val A0 = 91.0
    const val A1 = 179.0
    const val EDGE_A = 110.0     // ⌫: from the right edge up to this angle, beyond the corner keys
    const val BOTTOM_A = 160.0   // the rare keys: from this angle to the bottom edge

    val FN_KEYS: List<FnKey> = listOf(
        FnKey(Fn.SPACE, 30.0, PAD, A0, 150.0),
        FnKey(Fn.ENTER, 30.0, PAD, 150.0, A1),
        FnKey(Fn.BKSP, PAD, OUTER, A0, EDGE_A),
        FnKey(Fn.SHIFT, PAD, 194.0, BOTTOM_A, A1),
        FnKey(Fn.LANG, 194.0, 230.0, BOTTOM_A, A1),
        FnKey(Fn.SYM, 230.0, 266.0, BOTTOM_A, A1),
        FnKey(Fn.HIDE, 266.0, OUTER, BOTTOM_A, A1),
    )

    /** What the finger landed on: a service key, or the star zone (null). */
    fun hit(x: Double, y: Double): FnKey? {
        val r = max(hypot(x, y), 30.0)
        var a = atan2(y, x) * 180 / PI
        if (a < 0) a += 360
        if (r < PAD) a = a.coerceIn(A0 + 0.01, A1 - 0.01)   // the corner: no dead spots at its edges
        else if (r >= OUTER || (a >= EDGE_A && a < BOTTOM_A)) return null
        else a = a.coerceIn(A0 + 0.01, A1 - 0.01)
        return FN_KEYS.firstOrNull { r >= it.r0 && (r < it.r1 || it.r1 == OUTER) && a >= it.a0 && a < it.a1 }
            ?: FN_KEYS.first { r >= it.r0 && r < it.r1 + 1 }
    }
}
