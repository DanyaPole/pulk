package app.pulk

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The keyboard: service keys in rings around the thumb's corner, the star of «Пульс» beyond them and
 * suggestions floating above. Everything else is see-through: the app shows behind it and touches there
 * go to the app. The keyboard slides out of the corner with a drop sound («пульк») and hides back into it.
 * Geometry is the same as in the web prototype (base units), scaled to the phone; the left hand is a mirror image.
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
    private var stripH = 0f     // the band for suggestions above the star
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
        // the star sticks out past the zone towards the screen's middle: the view is tall enough for it
        val starTop = ((Pulse.PH[1] + Pulse.R + 4) * k).toFloat()
        val h = Math.ceil(max(r * k + 4 * density + stripH, starTop + 4 * density).toDouble()).toInt()
        cx = w + 4 * density; cy = h + 4 * density
        setMeasuredDimension(w, h)
    }

    // ---- open / hidden ----
    /** How far out of the corner the keyboard is: 0 hidden (only the handle), 1 open. */
    private var g = 0f
    var isOpen = false; private set
    private var anim: ValueAnimator? = null

    fun open(sound: Boolean) {
        if (isOpen && anim == null) return
        isOpen = true
        if (sound) svc.feedback.drop()
        animateTo(1f, 300, OvershootInterpolator(1.1f))
    }
    fun hide() {
        if (!isOpen) return
        isOpen = false
        touches.values.forEach { stopTimers(it) }; touches.clear(); cand = null
        animateTo(0f, 170, DecelerateInterpolator())
    }
    /** Without animation, e.g. when the keyboard window is hidden by the system. */
    fun reset(open: Boolean) { anim?.cancel(); anim = null; isOpen = open; g = if (open) 1f else 0f; invalidate() }

    private fun animateTo(to: Float, ms: Long, interp: android.animation.TimeInterpolator) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(g, to).apply {
            duration = ms; interpolator = interp
            addUpdateListener { g = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (anim === a) { anim = null; g = to; invalidate(); svc.insetsChanged() } }
            })
            start()
        }
        svc.insetsChanged()
    }

    /** The parts of the view that are the keyboard; the rest belongs to the app behind. */
    fun touchRegion(out: Region) {
        val clip = Region(0, 0, width, height)
        if (!isOpen) { out.set(handleRect()); return }
        val p = shape()
        val r = Region(); r.setPath(p, clip)
        for (rc in chipRects()) r.op(Rect(rc.left.toInt(), rc.top.toInt(), rc.right.toInt(), rc.bottom.toInt()), Region.Op.UNION)
        out.set(r)
    }

    /** Where the app's own content should end: the arc's height at the middle of the screen. */
    fun contentTop(): Int {
        if (!isOpen) return height
        val dx = (width / 2f - 4 * density) / k
        val r = Pulse.OUTER
        val hBase = if (dx < r) sqrt(r * r - dx * dx) else 0.0
        return max(0, (cy - hBase * k).toInt())
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
    /** A point in base units on screen. */
    private fun scr(bx: Double, by: Double) = floatArrayOf(mx((cx + bx * k).toFloat()), (cy - by * k).toFloat())

    /** The opaque body of the keyboard: the quarter disc around the corner and the star that sticks out of it. */
    private fun shape(): Path {
        val p = Path()
        val c = scr(0.0, 0.0); val rr = (Pulse.OUTER * k).toFloat()
        p.addCircle(c[0], c[1], rr, Path.Direction.CW)
        val s = scr(Pulse.PH[0], Pulse.PH[1])
        val p2 = Path(); p2.addCircle(s[0], s[1], (Pulse.R * k).toFloat() + 6 * density, Path.Direction.CW)
        p.op(p2, Path.Op.UNION)
        return p
    }

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

    // the stroke being drawn now: the candidate letter shows in the middle of the star
    private var cand: Pulse.Slot? = null
    private var trail: List<Double>? = null

    // ---- suggestions: chips floating above the keyboard, the best nearest to the thumb ----
    private fun chipText(): List<String> {
        val c = svc.cands.take(3)
        if (c.isNotEmpty()) return c
        val h = if (Coach.active) Coach.hint else null
        return if (h != null) listOf(h) else emptyList()
    }
    internal fun chipRects(): List<RectF> {
        val items = chipText()
        if (items.isEmpty()) return emptyList()
        txt.textSize = 17f * density; txt.isFakeBoldText = true
        val pad = 14 * density; val gap = 8 * density; val h = 38 * density
        val top = (stripH - h) / 2
        val out = ArrayList<RectF>()
        var edge = width - 8 * density  // from the thumb's side
        val maxW = (width - 16 * density - gap * (items.size - 1)) / items.size
        for (s in items) {
            val w = min(txt.measureText(s) + pad * 2, if (items.size == 1) width - 16 * density else maxW)
            val r = RectF(edge - w, top, edge, top + h)
            out.add(if (left) RectF(width - r.right, r.top, width - r.left, r.bottom) else r)
            edge -= w + gap
        }
        return out
    }

    private fun handleCentre() = pt(62.0, 135.0)
    private fun handleRect(): Rect { val c = handleCentre(); val r = (30 * density).toInt(); return Rect(c[0].toInt() - r, c[1].toInt() - r, c[0].toInt() + r, c[1].toInt() + r) }

    override fun onDraw(c: Canvas) {
        val col = colors()
        // the handle in the corner, when the keyboard is hidden (and under it while it comes out)
        if (g < 1f) drawHandle(c, col, 1f - g)
        if (g <= 0.001f) return
        val corner = scr(0.0, 0.0)
        c.save()
        c.scale(g, g, corner[0], corner[1])
        drawBody(c, col)
        c.restore()
    }

    private fun drawHandle(c: Canvas, col: Colors, alpha: Float) {
        val p = handleCentre(); val r = 26 * density
        fill.style = Paint.Style.FILL; fill.color = col.accent; fill.alpha = (255 * alpha).toInt()
        c.drawCircle(p[0], p[1], r, fill)
        stroke.color = col.accentFg; stroke.alpha = (255 * alpha).toInt(); stroke.strokeWidth = 2.2f * density; stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) { val a = i * PI / 4; c.drawLine(p[0] + cos(a).toFloat() * r * .3f, p[1] + sin(a).toFloat() * r * .3f, p[0] + cos(a).toFloat() * r * .62f, p[1] + sin(a).toFloat() * r * .62f, stroke) }
        fill.alpha = 255; stroke.alpha = 255
    }

    private fun drawBody(c: Canvas, col: Colors) {
        val sym = svc.symbols
        val table = Pulse.table(svc.lang, sym)
        fill.style = Paint.Style.FILL; fill.color = col.bg
        c.drawPath(shape(), fill)
        drawChips(c, col)
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
                Pulse.Fn.HIDE -> icon(c, 3, p[0], p[1], ink)
                Pulse.Fn.LANG -> label(c, svc.lang.uppercase(), p[0], p[1], 14f, ink, true)
                Pulse.Fn.SYM -> label(c, if (sym) (if (svc.lang == "ru") "абв" else "abc") else "?123", p[0], p[1], 12f, ink, true)
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
        // the finger's trail: what the keyboard sees of the stroke
        trail?.let { tr ->
            if (tr.size >= 4) {
                val p = Path(); val a = scr(tr[0], tr[1]); p.moveTo(a[0], a[1])
                for (j in 1 until tr.size / 2) { val q = scr(tr[2 * j], tr[2 * j + 1]); p.lineTo(q[0], q[1]) }
                stroke.color = col.accent; stroke.alpha = 150; stroke.strokeWidth = 5f * density; stroke.strokeCap = Paint.Cap.ROUND
                c.drawPath(p, stroke); stroke.alpha = 255
            }
        }
        // while stroking: the letter that will be typed, big, in the middle of the star
        if (live != null && !(Coach.active && Coach.hideLabels)) {
            val shown = (if (longAlt != null) longAlt!! else live.v).let { if (up) it.uppercase() else it }
            fill.color = col.accent; c.drawCircle(centre[0], centre[1], 26 * density, fill)
            label(c, if (live.isSpace) "·" else shown, centre[0], centre[1], 28f, col.accentFg, true)
        }
    }

    private fun drawChips(c: Canvas, col: Colors) {
        val items = chipText(); if (items.isEmpty()) return
        val rects = chipRects()
        val isHint = svc.cands.isEmpty()
        for ((i, r) in rects.withIndex()) {
            fill.style = Paint.Style.FILL; fill.color = if (pressedStrip == i) col.press else if (i == 0 && !isHint) col.accent else col.fn
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
            txt.color = if (i == 0 && !isHint) col.accentFg else col.fg; txt.isFakeBoldText = i == 0 && !isHint; txt.textSize = 17f * density
            val maxW = r.width() - 20 * density; val w = txt.measureText(items[i]); if (w > maxW) txt.textSize *= maxW / w
            c.drawText(items[i], r.centerX(), r.centerY() - (txt.ascent() + txt.descent()) / 2, txt)
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
        if (s.kind != 'c') { val e = Pulse.spot(s.petal, s.kind); val b = scr(e[0], e[1]); p.lineTo(b[0], b[1]) }
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

    /** 0 shift, 1 backspace, 2 enter, 3 hide, drawn as paths so no font glyph can be missing. */
    private fun icon(c: Canvas, which: Int, x: Float, y: Float, color: Int) {
        val u = 7f * density * max(kDp, 0.85f)
        fill.style = Paint.Style.STROKE; fill.color = color; fill.strokeWidth = 1.8f * density; fill.strokeJoin = Paint.Join.ROUND; fill.strokeCap = Paint.Cap.ROUND
        val p = Path()
        when (which) {
            0 -> { p.moveTo(x, y - u); p.lineTo(x + u, y); p.lineTo(x + u * .4f, y); p.lineTo(x + u * .4f, y + u); p.lineTo(x - u * .4f, y + u); p.lineTo(x - u * .4f, y); p.lineTo(x - u, y); p.close() }
            1 -> { p.moveTo(x - u, y); p.lineTo(x - u * .4f, y - u * .8f); p.lineTo(x + u, y - u * .8f); p.lineTo(x + u, y + u * .8f); p.lineTo(x - u * .4f, y + u * .8f); p.close()
                   p.moveTo(x - u * .1f, y - u * .35f); p.lineTo(x + u * .6f, y + u * .35f); p.moveTo(x + u * .6f, y - u * .35f); p.lineTo(x - u * .1f, y + u * .35f) }
            2 -> { p.moveTo(x + u, y - u * .8f); p.lineTo(x + u, y + u * .3f); p.lineTo(x - u, y + u * .3f)
                   p.moveTo(x - u * .4f, y - u * .3f); p.lineTo(x - u, y + u * .3f); p.lineTo(x - u * .4f, y + u * .9f) }
            else -> { p.moveTo(x - u * .8f, y - u * .3f); p.lineTo(x, y + u * .5f); p.lineTo(x + u * .8f, y - u * .3f) }
        }
        c.drawPath(p, fill); fill.style = Paint.Style.FILL
    }

    // ---- touch ----
    private class Touch(val fn: Pulse.FnKey?, val strip: Int, val handle: Boolean) {
        val pts = ArrayList<Double>()
        val times = ArrayList<Long>()
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
        val tm = e.eventTime
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { val i = e.actionIndex; down(e.getPointerId(i), e.getX(i), e.getY(i), tm) }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                // every sample the screen reported, not only the last one of the frame
                for (h in 0 until e.historySize) move(e.getPointerId(i), e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalEventTime(h))
                move(e.getPointerId(i), e.getX(i), e.getY(i), tm)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { val i = e.actionIndex; up(e.getPointerId(i), e.getX(i), e.getY(i), tm) }
            MotionEvent.ACTION_CANCEL -> { touches.values.forEach { stopTimers(it) }; touches.clear(); cand = null; trail = null; pressedFn = null; pressedStrip = -1; longAlt = null; invalidate() }
        }
        return true
    }

    private fun stopTimers(t: Touch) { t.hold?.let { handler.removeCallbacks(it) }; t.repeat?.let { handler.removeCallbacks(it) } }

    private fun chipAt(x: Float, y: Float): Int = chipRects().indexOfFirst { it.contains(x, y) }

    private fun down(id: Int, x: Float, y: Float, tm: Long) {
        if (!isOpen || anim != null) {
            val t = Touch(null, -1, handle = true); touches[id] = t
            if (!isOpen) open(sound = true)
            return
        }
        val b = doubleArrayOf(bx(x), by(y))
        val chip = if (svc.cands.isNotEmpty()) chipAt(x, y) else -1
        val inStrip = chip >= 0
        val fn = if (inStrip) null else Pulse.hit(b[0], b[1])
        val t = Touch(fn, chip, handle = false)
        t.pts.add(b[0]); t.pts.add(b[1]); t.times.add(tm); t.anchorX = b[0]; t.anchorY = b[1]
        touches[id] = t
        svc.feedback.touch()
        when {
            inStrip -> pressedStrip = chip
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

    private fun resolveNow(pts: DoubleArray): Pulse.Slot {
        val slot = Pulse.resolve(Pulse.table(svc.lang, svc.symbols), pts, blind())
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
        cand = resolveNow(t.pts.toDoubleArray())
        longAlt = if (t.long) cand?.let { Pulse.ALT[it.v] } else null
        trail = ArrayList(t.pts)
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

    private fun move(id: Int, x: Float, y: Float, tm: Long) {
        val t = touches[id] ?: return
        if (t.handle) return
        val b = doubleArrayOf(bx(x), by(y))
        if (t.strip >= 0) { val s = chipAt(x, y); if (s != pressedStrip) { pressedStrip = s; invalidate() }; return }
        t.pts.add(b[0]); t.pts.add(b[1]); t.times.add(tm)
        if (t.fn != null) return
        if (hypot(b[0] - t.anchorX, b[1] - t.anchorY) > 4) { t.anchorX = b[0]; t.anchorY = b[1]; t.long = false; armHold(t) }
        updateCand(t); invalidate()
    }

    private fun up(id: Int, x: Float, y: Float, tm: Long) {
        val t = touches.remove(id) ?: return
        stopTimers(t)
        if (t.handle) return
        val b = doubleArrayOf(bx(x), by(y))
        when {
            t.strip >= 0 -> { val s = pressedStrip; pressedStrip = -1; if (s >= 0) svc.pick(s) }
            t.fn == null -> {
                // the point where the finger left is not used (it jumps as the pad lifts), unless nothing moved
                if (t.pts.size == 2 && hypot(b[0] - t.pts[0], b[1] - t.pts[1]) >= Pulse.P_MIN) { t.pts.add(b[0]); t.pts.add(b[1]); t.times.add(tm) }
                val pts = Pulse.trimLift(t.pts.toDoubleArray(), t.times.toLongArray(), tm)
                val slot = resolveNow(pts)
                val alt = if (t.long) Pulse.ALT[slot.v] else null
                if (slot.isSpace) svc.space() else svc.typeLetter(alt ?: slot.v, slot)
            }
            else -> {
                val sx = t.pts[0]; val sy = t.pts[1]
                when (t.fn.fn) {
                    Pulse.Fn.SHIFT -> { val now = System.currentTimeMillis(); svc.shiftTap(now - lastShiftTap < 320); lastShiftTap = now }
                    Pulse.Fn.LANG -> if (!t.long) svc.switchLang()
                    Pulse.Fn.SYM -> svc.toggleSymbols()
                    Pulse.Fn.HIDE -> hide()
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
        if (touches.isEmpty()) { cand = null; trail = null; pressedFn = null; longAlt = null } else if (t.fn != null) pressedFn = null
        invalidate()
    }

    fun refresh() { requestLayout(); invalidate(); svc.insetsChanged() }

    companion object { const val STRIP_DP = 52f }
}
