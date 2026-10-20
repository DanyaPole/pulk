package app.pulk

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The keyboard: a strip of suggestions on top, service keys in rings around the thumb's corner and the
 * star of «Пульс» beyond them. Geometry is the same as in the web prototype (base units), scaled to the
 * phone; the left hand is a mirror image.
 */
class PulseView(context: Context, private val svc: PulkService) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private class Colors(val bg: Int, val key: Int, val fn: Int, val zone: Int, val press: Int, val line: Int,
                         val fg: Int, val muted: Int, val accent: Int, val accentFg: Int)
    private val light = Colors(0xFFD4DCE1.toInt(), 0xFFFFFFFF.toInt(), 0xFFC3CDD3.toInt(), 0xFFE3F1F5.toInt(), 0xFFA9D3E0.toInt(),
        0xFFC6D0D6.toInt(), 0xFF152027.toInt(), 0xFF5B6A73.toInt(), 0xFF0B6E8A.toInt(), 0xFFFFFFFF.toInt())
    private val dark = Colors(0xFF0A0E10.toInt(), 0xFF20292E.toInt(), 0xFF2C373D.toInt(), 0xFF1B323B.toInt(), 0xFF2F6070.toInt(),
        0xFF29343A.toInt(), 0xFFE4EBEE.toInt(), 0xFF8B9AA2.toInt(), 0xFF55BAD7.toInt(), 0xFF06222B.toInt())
    private fun colors() = if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) dark else light

    // ---- geometry ----
    private var k = 1f          // pixels per base unit
    private var cx = 0f; private var cy = 0f   // the thumb's corner on screen (right-hand geometry)
    private var kDp = 1f
    private var stripH = 0f     // the strip of suggestions above the star
    private val left get() = svc.prefs.left
    private fun mx(x: Float) = if (left) width - x else x
    private fun blind() = svc.prefs.blind

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val wdp = w / density
        val vhdp = resources.displayMetrics.heightPixels / density
        val r = Pulse.OUTER.toFloat()
        var kd = min(wdp, 440f) / (r * 1.047f) * svc.prefs.size / 100f * (if (blind()) 1.1f else 1f)
        kd = min(kd, (wdp + 4f - 6f) / r)
        kd = min(kd, vhdp * (if (blind()) 0.5f else 0.42f) / (r + 4f))
        kDp = kd; k = kd * density
        stripH = STRIP_DP * density
        val h = Math.ceil((r * k + 4 * density + stripH).toDouble()).toInt()
        cx = w + 4 * density; cy = h + 4 * density
        setMeasuredDimension(w, h)
    }

    // ---- drawing ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun ptPx(rPx: Float, aDeg: Double): FloatArray {
        val a = aDeg * PI / 180
        return floatArrayOf(mx((cx + rPx * cos(a)).toFloat()), (cy - rPx * sin(a)).toFloat())
    }
    private fun pt(r: Double, a: Double) = ptPx((r * k).toFloat(), a)
    /** A point of the star (base units, star centre relative coordinates in [b]) on screen. */
    private fun scr(bx: Double, by: Double) = floatArrayOf(mx((cx + bx * k).toFloat()), (cy - by * k).toFloat())

    /** A ring sector as a polygon (works for the mirrored hand too); radii in pixels. */
    private fun sector(r0Px: Float, r1Px: Float, a0: Double, a1: Double): Path {
        val p = Path()
        val steps = max(2, ((a1 - a0) / 2).toInt())
        for (i in 0..steps) { val q = ptPx(r1Px, a0 + (a1 - a0) * i / steps); if (i == 0) p.moveTo(q[0], q[1]) else p.lineTo(q[0], q[1]) }
        for (i in steps downTo 0) { val q = ptPx(r0Px, a0 + (a1 - a0) * i / steps); p.lineTo(q[0], q[1]) }
        p.close(); return p
    }
    private fun drawKey(c: Canvas, r0: Double, r1: Double, a0: Double, a1: Double, color: Int) {
        val gap = 2.5f * density; val cr = 6f * density * max(kDp, 0.8f)
        val ins = gap + cr; val rm = ((r0 + r1) / 2 * k).toFloat()
        val da = Math.toDegrees((ins / rm).toDouble())
        val path = sector((r0 * k).toFloat() + ins, (r1 * k).toFloat() - ins, a0 + da, a1 - da)
        fill.color = color; fill.style = Paint.Style.FILL; c.drawPath(path, fill)
        stroke.color = color; stroke.strokeWidth = cr * 2; c.drawPath(path, stroke)
    }
    private fun label(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int, bold: Boolean = false) {
        txt.color = color; txt.textSize = sizeDp * density * max(kDp, 0.85f); txt.isFakeBoldText = bold
        c.drawText(s, x, y - (txt.ascent() + txt.descent()) / 2, txt)
    }
    /** Text for the strip: fixed size, shrinks to fit [maxW]. */
    private fun stripText(c: Canvas, s: String, x: Float, y: Float, maxW: Float, color: Int, bold: Boolean) {
        txt.color = color; txt.isFakeBoldText = bold; txt.textSize = 17f * density
        val w = txt.measureText(s)
        if (w > maxW) txt.textSize *= maxW / w
        c.drawText(s, x, y - (txt.ascent() + txt.descent()) / 2, txt)
    }

    // the stroke being drawn now: the candidate letter shows in the middle of the star
    private var cand: Pulse.Slot? = null

    /** Strip slots from the thumb's side: the best suggestion is the nearest to the thumb. */
    private fun slotX(i: Int): Float { val w = width / 3f; val fromThumb = width - w * (i + 0.5f); return if (left) width - fromThumb else fromThumb }
    private fun slotAt(x: Float): Int { val w = width / 3f; val fromThumb = ((if (left) x else width - x) / w).toInt(); return fromThumb.coerceIn(0, 2) }

    override fun onDraw(c: Canvas) {
        val col = colors()
        c.drawColor(col.bg)
        val sym = svc.symbols
        val table = Pulse.table(svc.lang, sym)
        drawStrip(c, col)
        // service keys
        for (key in Pulse.FN_KEYS) {
            val active = (key.fn == Pulse.Fn.SHIFT && (svc.shiftLock || svc.shiftOnce))
            val color = when {
                key.fn == Pulse.Fn.SHIFT && svc.shiftLock -> col.accent
                active || pressedFn == key.fn -> col.press
                else -> col.fn
            }
            drawKey(c, key.r0, key.r1, key.a0, key.a1, color)
            val rm = (key.r0 + key.r1) / 2; val am = (key.a0 + key.a1) / 2
            val p = pt(rm, am)
            val ink = if (key.fn == Pulse.Fn.SHIFT && svc.shiftLock) col.accentFg else col.fg
            when (key.fn) {
                Pulse.Fn.SHIFT -> icon(c, 0, p[0], p[1], ink)
                Pulse.Fn.BKSP -> icon(c, 1, p[0], p[1], ink)
                Pulse.Fn.ENTER -> icon(c, 2, p[0], p[1], ink)
                Pulse.Fn.LANG -> label(c, svc.lang.uppercase(), p[0], p[1], 15f, ink, true)
                Pulse.Fn.SYM -> label(c, if (sym) (if (svc.lang == "ru") "абв" else "abc") else "?123", p[0], p[1], 13f, ink, true)
                Pulse.Fn.SPACE -> {
                    label(c, if (svc.lang == "ru") "пробел" else "space", p[0], p[1], 14f, col.muted)
                    val o = pt(key.r1 - 11, am); val i = pt(key.r0 + 11, am)
                    label(c, ".", o[0], o[1], 17f, col.muted); label(c, ",", i[0], i[1], 17f, col.muted)
                    // along the arc upwards: the best suggestion
                    if (svc.cands.isNotEmpty()) { val u = pt(rm, key.a0 + 9); label(c, "↑", u[0], u[1], 15f, col.accent, true) }
                }
            }
        }
        // the zone of the star
        drawKey(c, Pulse.PAD, Pulse.OUTER, Pulse.A0, Pulse.A1, col.zone)
        val centre = scr(Pulse.PH[0], Pulse.PH[1])
        fill.style = Paint.Style.FILL; fill.color = col.key; c.drawCircle(centre[0], centre[1], (Pulse.R * k).toFloat(), fill)
        stroke.color = col.line; stroke.strokeWidth = density; c.drawCircle(centre[0], centre[1], (Pulse.R * k).toFloat(), stroke)
        // the wedge under the finger, or the one the tutorial points at
        val live = cand
        val goal = if (Coach.active && Coach.help >= 2) Coach.next?.let { target(it) } else null
        if (goal != null && goal.first.petal >= 0) { fill.color = col.zone; c.drawPath(wedge(goal.first.petal), fill) }
        if (live != null && live.petal >= 0) { fill.color = col.press; c.drawPath(wedge(live.petal), fill) }
        // separators
        stroke.color = col.line; stroke.strokeWidth = density
        for (p in 0 until 8) {
            val a = p * 45.0 - 22.5
            val s0 = starPt(15.0, a); val s1 = starPt(Pulse.R, a)
            c.drawLine(s0[0], s0[1], s1[0], s1[1], stroke)
        }
        fill.color = col.fn; c.drawCircle(centre[0], centre[1], (13 * k), fill)
        if (goal != null) drawPath(c, goal.first, col)
        // letters of the star
        val up = svc.shiftActive()
        if (!Coach.hideLabels || !Coach.active) for (p in 0 until 8) for ((i, kind) in Pulse.KINDS.withIndex()) {
            val v = table[p][i]
            if (v.isEmpty()) continue
            val s = Pulse.spot(p, kind)
            val q = scr(s[0], s[1])
            val big = kind == 'c'
            val isGoal = goal != null && goal.first.petal == p && goal.first.kind == kind
            label(c, if (up) v.uppercase() else v, q[0], q[1], if (big) 21f else if (kind == 'b') 11f else 14f,
                if (isGoal) col.accent else if (big) col.fg else col.muted, isGoal)
        }
        // while stroking: the letter that will be typed, big, in the middle of the star
        if (live != null && !(Coach.active && Coach.hideLabels)) {
            val shown = (if (longAlt != null) longAlt!! else live.v).let { if (up) it.uppercase() else it }
            fill.color = col.accent; c.drawCircle(centre[0], centre[1], 26 * density, fill)
            label(c, if (live.isSpace) "·" else shown, centre[0], centre[1], 28f, col.accentFg, true)
        }
    }

    private fun drawStrip(c: Canvas, col: Colors) {
        val y = stripH / 2
        val cands = svc.cands
        val hint = if (Coach.active) Coach.hint else null
        if (cands.isEmpty()) {
            if (hint != null) stripText(c, hint, width / 2f, y, width - 24 * density, col.fg, false)
            return
        }
        stroke.color = col.line; stroke.strokeWidth = density
        for (i in 1..2) { val x = width * i / 3f; c.drawLine(x, stripH * 0.25f, x, stripH * 0.75f, stroke) }
        for ((i, w) in cands.withIndex()) {
            if (i > 2) break
            if (pressedStrip == i) { fill.color = col.press; fill.style = Paint.Style.FILL; c.drawRect(slotX(i) - width / 6f, 0f, slotX(i) + width / 6f, stripH, fill) }
            stripText(c, w, slotX(i), y, width / 3f - 12 * density, if (i == 0) col.fg else col.muted, i == 0)
        }
    }

    /** The path the finger should draw for a letter: out to it, then the turn or the way back. */
    private fun drawPath(c: Canvas, s: Pulse.Slot, col: Colors) {
        stroke.color = col.accent; stroke.strokeWidth = 3.5f * density; stroke.strokeCap = Paint.Cap.ROUND
        val ctr = scr(Pulse.PH[0], Pulse.PH[1])
        if (s.isSpace) { c.drawCircle(ctr[0], ctr[1], 16 * k, stroke); return }
        val big = Pulse.spot(s.petal, 'c')
        val a = scr(big[0], big[1])
        val p = Path(); p.moveTo(ctr[0], ctr[1]); p.lineTo(a[0], a[1])
        when (s.kind) {
            'l', 'r' -> { val e = Pulse.spot(s.petal, s.kind); val b = scr(e[0], e[1]); p.lineTo(b[0], b[1]) }
            'b' -> { val e = Pulse.spot(s.petal, 'b'); val b = scr(e[0], e[1]); p.lineTo(b[0], b[1]) }
        }
        c.drawPath(p, stroke)
        val e = Pulse.spot(s.petal, s.kind); val q = scr(e[0], e[1])
        stroke.strokeWidth = 2f * density; c.drawCircle(q[0], q[1], 14 * k, stroke)
    }

    /** Where a letter the tutorial wants lives on the star, and what typing it there gives. */
    private fun target(want: String): Pair<Pulse.Slot, String>? {
        if (want == " ") return Pulse.SPACE to " "
        val t = Pulse.table(svc.lang, svc.symbols)
        val w = want.lowercase()
        val base = Pulse.ALT.entries.firstOrNull { it.value == w }?.key ?: w
        for (p in 0 until 8) for ((i, kd) in Pulse.KINDS.withIndex()) if (t[p][i] == base) return Pulse.Slot(p, kd, base) to w
        return null
    }

    private fun starPt(rBase: Double, aDeg: Double): FloatArray {
        val a = aDeg * PI / 180
        return scr(Pulse.PH[0] + rBase * cos(a), Pulse.PH[1] + rBase * sin(a))
    }
    private fun wedge(p: Int): Path {
        val path = Path(); val a0 = p * 45.0 - 22.5; val a1 = p * 45.0 + 22.5
        for (i in 0..8) { val q = starPt(Pulse.R - 1, a0 + (a1 - a0) * i / 8); if (i == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1]) }
        for (i in 8 downTo 0) { val q = starPt(15.0, a0 + (a1 - a0) * i / 8); path.lineTo(q[0], q[1]) }
        path.close(); return path
    }

    /** 0 shift, 1 backspace, 2 enter, drawn as paths so no font glyph can be missing. */
    private fun icon(c: Canvas, which: Int, x: Float, y: Float, color: Int) {
        val u = 7f * density * max(kDp, 0.85f)
        fill.style = Paint.Style.STROKE; fill.color = color; fill.strokeWidth = 1.8f * density; fill.strokeJoin = Paint.Join.ROUND; fill.strokeCap = Paint.Cap.ROUND
        val p = Path()
        when (which) {
            0 -> { p.moveTo(x, y - u); p.lineTo(x + u, y); p.lineTo(x + u * .4f, y); p.lineTo(x + u * .4f, y + u); p.lineTo(x - u * .4f, y + u); p.lineTo(x - u * .4f, y); p.lineTo(x - u, y); p.close() }
            1 -> { p.moveTo(x - u, y); p.lineTo(x - u * .4f, y - u * .8f); p.lineTo(x + u, y - u * .8f); p.lineTo(x + u, y + u * .8f); p.lineTo(x - u * .4f, y + u * .8f); p.close()
                   p.moveTo(x - u * .1f, y - u * .35f); p.lineTo(x + u * .6f, y + u * .35f); p.moveTo(x + u * .6f, y - u * .35f); p.lineTo(x - u * .1f, y + u * .35f) }
            else -> { p.moveTo(x + u, y - u * .8f); p.lineTo(x + u, y + u * .3f); p.lineTo(x - u, y + u * .3f)
                      p.moveTo(x - u * .4f, y - u * .3f); p.lineTo(x - u, y + u * .3f); p.lineTo(x - u * .4f, y + u * .9f) }
        }
        c.drawPath(p, fill); fill.style = Paint.Style.FILL
    }

    // ---- touch ----
    private class Touch(val fn: Pulse.FnKey?, val strip: Int, val startMs: Long) {
        val pts = ArrayList<Double>()
        var anchorX = 0.0; var anchorY = 0.0
        var long = false
        var hold: Runnable? = null
        var repeat: Runnable? = null
    }
    private val touches = HashMap<Int, Touch>()
    private var pressedFn: Pulse.Fn? = null
    private var pressedStrip = -1
    private var longAlt: String? = null
    private var lastShiftTap = 0L

    private fun bx(x: Float) = ((mx(x) - cx) / k).toDouble()
    private fun by(y: Float) = ((cy - y) / k).toDouble()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { val i = e.actionIndex; down(e.getPointerId(i), e.getX(i), e.getY(i)) }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { val i = e.actionIndex; up(e.getPointerId(i), e.getX(i), e.getY(i)) }
            MotionEvent.ACTION_CANCEL -> { touches.values.forEach { stopTimers(it) }; touches.clear(); cand = null; pressedFn = null; pressedStrip = -1; longAlt = null; invalidate() }
        }
        return true
    }

    private fun stopTimers(t: Touch) { t.hold?.let { handler.removeCallbacks(it) }; t.repeat?.let { handler.removeCallbacks(it) } }

    private fun down(id: Int, x: Float, y: Float) {
        val b = doubleArrayOf(bx(x), by(y))
        val inStrip = y < stripH
        val fn = if (inStrip) null else Pulse.hit(b[0], b[1])
        val t = Touch(fn, if (inStrip) slotAt(x) else -1, System.currentTimeMillis())
        t.pts.add(b[0]); t.pts.add(b[1]); t.anchorX = b[0]; t.anchorY = b[1]
        touches[id] = t
        svc.feedback.touch()
        when {
            inStrip -> pressedStrip = t.strip
            fn == null -> { updateCand(t); armHold(t) }
            else -> {
                pressedFn = fn.fn
                if (fn.fn == Pulse.Fn.BKSP) {
                    svc.backspace()
                    val rep = object : Runnable { override fun run() { svc.backspace(); handler.postDelayed(this, 65) } }
                    t.repeat = rep; handler.postDelayed(rep, 420)
                }
                if (fn.fn == Pulse.Fn.LANG) {
                    val r = Runnable { t.long = true; pressedFn = null; svc.feedback.mode(); svc.pickKeyboard(); invalidate() }
                    t.hold = r; handler.postDelayed(r, 550)
                }
            }
        }
        invalidate()
    }

    private fun resolveNow(t: Touch): Pulse.Slot {
        val slot = Pulse.resolve(Pulse.table(svc.lang, svc.symbols), t.pts.toDoubleArray(), blind())
        // hand in hand (the tutorial's strongest help): a stroke near the wanted letter becomes that letter
        if (Coach.active && Coach.help >= 3) {
            val want = Coach.next?.let { target(it) }?.first
            if (want != null && !want.isSpace && !slot.isSpace) {
                val d = (slot.petal - want.petal + 8) % 8
                if (d == 0 || d == 1 || d == 7) return want
            }
        }
        return slot
    }

    private fun updateCand(t: Touch) {
        cand = resolveNow(t)
        longAlt = if (t.long) cand?.let { Pulse.ALT[it.v] } else null
    }

    /** Holding still at the end of a stroke gives the alternative: е → ё, ь → ъ, ? → !. */
    private fun armHold(t: Touch) {
        t.hold?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            val s = cand
            if (s != null && !s.isSpace && Pulse.ALT.containsKey(s.v)) { t.long = true; longAlt = Pulse.ALT[s.v]; svc.feedback.mode(); invalidate() }
        }
        t.hold = r; handler.postDelayed(r, 420)
    }

    private fun move(id: Int, x: Float, y: Float) {
        val t = touches[id] ?: return
        val b = doubleArrayOf(bx(x), by(y))
        if (t.strip >= 0) { val s = if (y < stripH * 1.6f) slotAt(x) else -1; if (s != pressedStrip) { pressedStrip = s; invalidate() }; return }
        if (t.fn != null) { t.pts.add(b[0]); t.pts.add(b[1]); return }
        t.pts.add(b[0]); t.pts.add(b[1])
        if (hypot(b[0] - t.anchorX, b[1] - t.anchorY) > 4) { t.anchorX = b[0]; t.anchorY = b[1]; t.long = false; armHold(t) }
        updateCand(t); invalidate()
    }

    private fun up(id: Int, x: Float, y: Float) {
        val t = touches.remove(id) ?: return
        stopTimers(t)
        val b = doubleArrayOf(bx(x), by(y))
        when {
            t.strip >= 0 -> { val s = pressedStrip; pressedStrip = -1; if (s >= 0) svc.pick(s) }
            t.fn == null -> {
                t.pts.add(b[0]); t.pts.add(b[1])
                val slot = resolveNow(t)
                val alt = if (t.long) Pulse.ALT[slot.v] else null
                if (slot.isSpace) svc.space() else svc.typeLetter(alt ?: slot.v, slot)
            }
            else -> {
                val sx = t.pts[0]; val sy = t.pts[1]
                when (t.fn.fn) {
                    Pulse.Fn.SHIFT -> { val now = System.currentTimeMillis(); svc.shiftTap(now - lastShiftTap < 320); lastShiftTap = now }
                    Pulse.Fn.LANG -> if (!t.long) svc.switchLang()
                    Pulse.Fn.SYM -> svc.toggleSymbols()
                    Pulse.Fn.ENTER -> svc.enter()
                    Pulse.Fn.BKSP -> {}
                    Pulse.Fn.SPACE -> when (Pulse.dirOf(sx, sy, b[0] - sx, b[1] - sy, Pulse.GAP_MIN)) {
                        "out" -> svc.punct(".")
                        "in" -> svc.punct(",")
                        "ccw" -> svc.deleteWord()
                        "cw" -> svc.pickBest()
                        else -> svc.space()
                    }
                }
            }
        }
        if (touches.isEmpty()) { cand = null; pressedFn = null; longAlt = null } else if (t.fn != null) pressedFn = null
        invalidate()
    }

    fun refresh() { requestLayout(); invalidate() }

    companion object { const val STRIP_DP = 44f }
}
