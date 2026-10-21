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
                         val fg: Int, val muted: Int, val accent: Int, val accentFg: Int, val halo: Int, val glassEdge: Int)
    // the design system «Пульк», concept «Лепестки»: bg is unused (the keyboard is see-through), key = petal,
    // fn = glass (its own transparency), zone = coach-zone, line = petal-edge, fg = ink, muted = ink-muted, accentFg = on-accent
    private val light = Colors(0, 0xFFFFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0xFFE6F3F7.toInt(), 0xFFCFE8F0.toInt(),
        0xFF7F929C.toInt(), 0xFF0F1E26.toInt(), 0xFF4A5B64.toInt(), 0xFF0A6680.toInt(), 0xFFFFFFFF.toInt(),
        halo = 0xFFFFFFFF.toInt(), glassEdge = 0x2E0F1E26)
    private val dark = Colors(0, 0xFF1B262C.toInt(), 0x991B262C.toInt(), 0xFF1E3944.toInt(), 0xFF244A5B.toInt(),
        0xFF5E737D.toInt(), 0xFFEDF3F6.toInt(), 0xFFA3B4BC.toInt(), 0xFF4CC2DE.toInt(), 0xFF032029.toInt(),
        halo = 0xFF0B1114.toInt(), glassEdge = 0x2EFFFFFF)
    /** «Контраст» (concept «Маяк»): for weak sight — black and white, an amber accent, nearly solid glass, bold letters. */
    private val contrastLight = Colors(0, 0xFFFFFFFF.toInt(), 0xD9FFFFFF.toInt(), 0xFFFFE9C7.toInt(), 0xFFFFD58A.toInt(),
        0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF1A1A1A.toInt(), 0xFF9A4F00.toInt(), 0xFFFFFFFF.toInt(),
        halo = 0xFFFFFFFF.toInt(), glassEdge = 0x66000000)
    private val contrastDark = Colors(0, 0xFF000000.toInt(), 0xD9000000.toInt(), 0xFF3D2A00.toInt(), 0xFF5C3F00.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFEDEDED.toInt(), 0xFFFFB000.toInt(), 0xFF000000.toInt(),
        halo = 0xFF000000.toInt(), glassEdge = 0x66FFFFFF)
    private fun night() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private fun colors() = when { svc.prefs.contrast -> if (night()) contrastDark else contrastLight; night() -> dark; else -> light }

    // ---- geometry ----
    private var k = 1f          // pixels per base unit
    private var cx = 0f; private var cy = 0f   // the thumb's corner on screen (right-hand geometry)
    private var kDp = 1f
    private var stripH = 0f     // the band for suggestions above the star
    private val left get() = svc.prefs.left
    private fun mx(x: Float) = if (left) width - x else x
    private fun blind() = svc.prefs.blind
    /** «Толчки»: a whole word per touch. A lesson says itself which way it teaches. */
    private fun jabsMode() = if (Coach.active) Coach.jabs else svc.prefs.mode == "jabs"
    /** The whole screen is where a stroke may start, and the star comes to the finger. Lessons keep the star in place. */
    private fun screenZone() = svc.prefs.zone == "screen" && !Coach.active && !tapsMode()
    /** Простой режим: ⇧ and RU/EN are not shown (capitals come by themselves); the space key has no gestures. */
    private fun simple() = svc.prefs.simple && !Coach.active
    /** An extra gesture this person has switched on (lessons always have all of them). */
    private fun gest(g: String) = Coach.active || (svc.prefs.has(g) && !simple())
    private fun hiddenInSimple(fn: Pulse.Fn) = simple() && (fn == Pulse.Fn.SHIFT || fn == Pulse.Fn.LANG)
    /** «Два касания»: for a hand that can tap but not stroke. Strokes still work as in «Пульс». */
    private fun tapsMode() = !Coach.active && svc.prefs.mode == "taps"
    /** The petal opened by the first tap, its letters waiting for the second (-1: none). */
    internal var openPetal = -1
    private fun tapIsSpace() = blind() || screenZone()
    private fun textWindow() = svc.prefs.textWindow && !Coach.active
    private var cornerH = 0f    // how tall the keyboard is in the corner (the screen zone is taller)
    private fun statusBar(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * density).toInt()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val wdp = w / density
        val vhdp = resources.displayMetrics.heightPixels / density
        val r = Pulse.OUTER.toFloat()
        val kd = Pulse.scaleDp(wdp, vhdp, svc.prefs.size, blind())
        kDp = kd; k = kd * density
        stripH = STRIP_DP * density
        // the star sticks out past the zone towards the screen's middle: the view is tall enough for it
        val starTop = ((Pulse.PH[1] + Pulse.R + 4) * k).toFloat()
        cornerH = max(r * k + 4 * density + stripH, starTop + 4 * density)
        var hf = cornerH + if (textWindow()) FIELD_BAND_DP * density else 0f
        if (screenZone()) {
            val spec = MeasureSpec.getSize(heightSpec)
            val screenH = resources.displayMetrics.heightPixels - statusBar()
            val avail = if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED && spec > 0) min(spec, screenH) else screenH
            hf = max(cornerH, avail.toFloat())
        }
        val h = Math.ceil(hf.toDouble()).toInt()
        cx = w + 4 * density; cy = h + 4 * density
        setMeasuredDimension(w, h)
        forgetShapes()
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
        if (!isOpen) { out.set(handleRect()); return }
        if (screenZone()) { out.set(0, 0, width, height); return }   // all of it: the finger may start anywhere
        val r = Region(shapeRegion())
        fieldRect()?.let { f -> r.op(Rect(f.left.toInt(), f.top.toInt(), f.right.toInt(), f.bottom.toInt()), Region.Op.UNION) }
        for (rc in chipRects()) r.op(Rect(rc.left.toInt(), rc.top.toInt(), rc.right.toInt(), rc.bottom.toInt()), Region.Op.UNION)
        out.set(r)
    }

    /** Where the app's own content should end: the arc's height at the middle of the screen. */
    fun contentTop(): Int {
        if (!isOpen) return height
        if (screenZone()) return max(0, (cy - 75 * k).toInt())        // the app shows down to the keys at the bottom
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

    // ---- shapes kept between frames: they change only with the size or the hand ----
    private var shapeReg: Region? = null
    private val keyPaths = HashMap<Pulse.FnKey, Path>()
    private val wedgePaths = arrayOfNulls<Path>(8)
    private var shapesFor = 0
    /** The keyboard's size or hand changed: everything drawn from geometry is built again. */
    private fun forgetShapes() {
        val stamp = java.util.Objects.hash(measuredWidth, measuredHeight, k, left)
        if (stamp == shapesFor) return
        shapesFor = stamp; shapeReg = null; keyPaths.clear(); wedgePaths.fill(null)
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); shapesFor = 0; forgetShapes() }

    /** What is the keyboard: the star (the active zone) and the keys. Everything else is the app, seen and touched. */
    private fun shapeRegion(): Region = shapeReg ?: run {
        val p = Path()
        val s = scr(Pulse.PH[0], Pulse.PH[1])
        p.addCircle(s[0], s[1], (Pulse.R * k).toFloat() + 10 * density, Path.Direction.CW)
        for (key in Pulse.FN_KEYS) p.op(sector((key.r0 * k).toFloat(), (key.r1 * k).toFloat(), key.a0, key.a1), Path.Op.UNION)
        Region().also { it.setPath(p, Region(0, 0, width, height)); shapeReg = it }
    }

    /** A ring sector as a polygon (works for the mirrored hand too); radii in pixels. */
    private fun sector(r0Px: Float, r1Px: Float, a0: Double, a1: Double): Path {
        val p = Path()
        val steps = max(2, ((a1 - a0) / 2).toInt())
        for (i in 0..steps) { val q = ptPx(r1Px, a0 + (a1 - a0) * i / steps); if (i == 0) p.moveTo(q[0], q[1]) else p.lineTo(q[0], q[1]) }
        for (i in steps downTo 0) { val q = ptPx(r0Px, a0 + (a1 - a0) * i / steps); p.lineTo(q[0], q[1]) }
        p.close(); return p
    }
    private fun drawKey(c: Canvas, key: Pulse.FnKey, color: Int) {
        val cr = 6f * density * max(kDp, 0.8f)
        val path = keyPaths.getOrPut(key) {
            val ins = 2.5f * density + cr; val rm = ((key.r0 + key.r1) / 2 * k).toFloat()
            val da = Math.toDegrees((ins / rm).toDouble())
            sector((key.r0 * k).toFloat() + ins, (key.r1 * k).toFloat() - ins, key.a0 + da, key.a1 - da)
        }
        // one pass, so a see-through key has no darker rim
        fill.color = color; fill.style = Paint.Style.FILL_AND_STROKE; fill.strokeWidth = cr * 2; fill.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, fill); fill.style = Paint.Style.FILL
        if (edge != 0) { stroke.color = edge; stroke.strokeWidth = density; c.drawPath(path, stroke) }
    }
    private var edge = 0
    /** Sizes of the design are for 1 unit = 1.232 dp; the keyboard scales with the screen. */
    private fun ds() = density * max(kDp, 0.85f) / 1.232f
    /**
     * A label. On glass ([halo] not 0) it gets a 3 dp halo of the halo colour first: that keeps it readable
     * (WCAG AA) over whatever app is behind the see-through keyboard.
     */
    private fun label(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int, bold: Boolean = false, halo: Int = 0) {
        txt.textSize = sizeDp * ds(); txt.isFakeBoldText = bold || svc.prefs.contrast
        val by = y - (txt.ascent() + txt.descent()) / 2
        if (halo != 0) {
            txt.style = Paint.Style.STROKE; txt.strokeWidth = 3 * density * 2; txt.strokeJoin = Paint.Join.ROUND; txt.color = halo
            c.drawText(s, x, by, txt); txt.style = Paint.Style.FILL
        }
        txt.color = color; c.drawText(s, x, by, txt)
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
        val pad = 18 * density; val gap = 8 * density; val h = 48 * density
        val top = (height - cornerH) + (stripH - h) / 2   // above the corner keyboard, whatever the zone
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

    // ---- the text window ----
    private var fieldDrag: FloatArray? = null   // while it is being dragged: its left and top
    // the text as last drawn, to find which letter the finger is on
    private var fieldLay: android.text.StaticLayout? = null
    private var fieldTx = 0f; private var fieldTy = 0f
    // while the cursor is being slid: the text as it was when the finger came down, and where the cursor is in it
    private var curText: String? = null
    private var curAt = 0
    private fun gripW() = 26 * density
    private fun onGrip(r: RectF, x: Float) = if (left) x > r.right - gripW() else x < r.left + gripW()
    private fun fieldW() = min(width * 0.8f, 360 * density)
    private fun fieldH() = 60 * density
    /** Where the text window is, or null when it is off. Kept inside the view. */
    internal fun fieldRect(): RectF? {
        if (!textWindow() || width == 0) return null
        val w = fieldW(); val h = fieldH()
        val d = fieldDrag
        var x = d?.get(0) ?: svc.prefs.fieldX.let { if (it < 0) (if (left) 8 * density else width - w - 8 * density) else it * width }
        var y = d?.get(1) ?: svc.prefs.fieldUp.let { if (it < 0) height - cornerH - h - 4 * density else height - it * density }
        x = x.coerceIn(0f, width - w); y = y.coerceIn(0f, height - h)
        return RectF(x, y, x + w, y + h)
    }

    private val layoutPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private fun drawField(c: Canvas, col: Colors) {
        val r = fieldRect() ?: return
        fill.style = Paint.Style.FILL; fill.color = (col.key and 0x00FFFFFF) or (0xEE shl 24)
        c.drawRoundRect(r, 14 * density, 14 * density, fill)
        stroke.color = if (fieldDrag != null) col.accent else col.line; stroke.strokeWidth = (if (fieldDrag != null) 2f else 1f) * density
        c.drawRoundRect(r, 14 * density, 14 * density, stroke)
        // a grip on the side away from the thumb, so the window reads as something to drag
        val gx = if (left) r.right - 10 * density else r.left + 10 * density
        fill.color = col.muted
        for (i in 0..2) c.drawCircle(gx, r.centerY() + (i - 1) * 7 * density, 1.8f * density, fill)
        val pad = 30 * density; val tw = (r.width() - pad * 2).toInt()   // clear of the grip on either side
        val snap = curText
        val before = if (snap != null) snap.substring(0, curAt) else svc.textBefore
        val after = if (snap != null) snap.substring(curAt) else svc.textAfter
        layoutPaint.textSize = 16f * density; layoutPaint.color = col.fg
        fieldLay = null
        if (before.isEmpty() && after.isEmpty()) {
            layoutPaint.color = col.muted
            val hint = if (svc.lang == "ru") "здесь будет текст — перетащи окно куда удобно" else "your text shows here — drag me anywhere"
            layoutPaint.textSize = 13f * density
            val w = layoutPaint.measureText(hint); if (w > tw) layoutPaint.textSize *= tw / w   // fits between the grips
            c.drawText(hint, r.left + pad, r.centerY() + 5 * density, layoutPaint)
            return
        }
        val text = before + after
        val lay = android.text.StaticLayout.Builder.obtain(text, 0, text.length, layoutPaint, tw).setIncludePad(false).build()
        val cur = before.length
        val line = lay.getLineForOffset(cur)
        val first = if (snap != null) fieldFirst else maxOf(0, line - 1)   // two lines: the cursor's line and the one before; still while sliding
        fieldFirst = first
        val lineH = lay.getLineBottom(0) - lay.getLineTop(0)
        c.save()
        c.clipRect(r.left + pad - 2, r.top + 4 * density, r.right - 8 * density, r.bottom - 4 * density)
        val top = r.top + (r.height() - lineH * minOf(2, line + 1)) / 2
        c.translate(r.left + pad, top - lay.getLineTop(first))
        fieldLay = lay; fieldTx = r.left + pad; fieldTy = top - lay.getLineTop(first)
        lay.draw(c)
        val cx0 = lay.getPrimaryHorizontal(cur)
        fill.color = col.accent
        c.drawRect(cx0, lay.getLineTop(line).toFloat(), cx0 + 2 * density, lay.getLineBottom(line).toFloat(), fill)
        c.restore()
    }

    private var fieldFirst = 0

    /** Which character of the shown text is under the finger (an offset into before + after). */
    private fun offsetAt(x: Float, y: Float): Int? {
        val lay = fieldLay ?: return null
        val line = lay.getLineForVertical((y - fieldTy).toInt().coerceIn(lay.getLineTop(fieldFirst), lay.getLineBottom(minOf(lay.lineCount - 1, fieldFirst + 1)) - 1))
        return lay.getOffsetForHorizontal(line, x - fieldTx)
    }
    private fun slideCursor(x: Float, y: Float) {
        val at = offsetAt(x, y) ?: return
        if (at != curAt) { svc.moveCursor(at - curAt); curAt = at }
        invalidate()
    }

    private fun handleCentre() = pt(62.0, 135.0)
    private fun handleRect(): Rect { val c = handleCentre(); val r = (30 * density).toInt(); return Rect(c[0].toInt() - r, c[1].toInt() - r, c[0].toInt() + r, c[1].toInt() + r) }

    override fun onDraw(c: Canvas) {
        val col = colors()
        // the handle in the corner, when the keyboard is hidden (and under it while it comes out)
        if (g < 1f) drawHandle(c, col, 1f - g)
        if (g <= 0.001f) return
        if (g >= 0.999f) drawField(c, col)
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
        drawChips(c, col)
        // service keys
        for (key in Pulse.FN_KEYS) {
            if (hiddenInSimple(key.fn)) continue
            val active = (key.fn == Pulse.Fn.SHIFT && (svc.shiftLock || svc.shiftOnce))
            val color = when {
                key.fn == Pulse.Fn.SHIFT && svc.shiftLock -> col.accent
                active || pressedFn == key.fn -> col.press
                else -> col.fn
            }
            edge = 0   // the glass is its own edge: a line on the rounded shape doubles it
            drawKey(c, key, color); edge = 0
            val h = if (color == col.fn) col.halo else 0
            val rm = (key.r0 + key.r1) / 2; val am = (key.a0 + key.a1) / 2
            val p = pt(rm, am)
            val ink = if (key.fn == Pulse.Fn.SHIFT && svc.shiftLock) col.accentFg else col.fg
            when (key.fn) {
                Pulse.Fn.SHIFT -> icon(c, 0, p[0], p[1], ink, h)
                Pulse.Fn.BKSP -> icon(c, 1, p[0], p[1], ink, h)
                Pulse.Fn.ENTER -> icon(c, 2, p[0], p[1], ink, h)
                Pulse.Fn.HIDE -> icon(c, 3, p[0], p[1], ink, h)
                Pulse.Fn.LANG -> label(c, svc.lang.uppercase(), p[0], p[1], 18f, ink, true, h)
                Pulse.Fn.SYM -> label(c, if (sym) (if (svc.lang == "ru") "абв" else "abc") else "?123", p[0], p[1], 16f, ink, true, h)
                Pulse.Fn.SPACE -> {
                    label(c, if (svc.lang == "ru") "пробел" else "space", p[0], p[1], 17f, col.muted, false, h)
                    val o = pt(key.r1 - 11, am); val i = pt(key.r0 + 11, am)
                    label(c, ".", o[0], o[1], 21f, col.muted, false, h); label(c, ",", i[0], i[1], 21f, col.muted, false, h)
                    // along the arc upwards: the best suggestion
                    if (svc.cands.isNotEmpty() && !simple()) { val u = pt(rm, key.a0 + 9); label(c, "↑", u[0], u[1], 18f, col.accent, true, h) }
                }
            }
        }
        // the star: in its place, or (whole screen) under the finger
        val origin = starOrigin()
        if (screenZone() && origin == null) { drawIdleHint(c, col); return }
        val home = scr(Pulse.PH[0], Pulse.PH[1])
        val dx = if (origin != null) scr(origin[0], origin[1])[0] - home[0] else 0f
        val dy = if (origin != null) scr(origin[0], origin[1])[1] - home[1] else 0f
        c.save(); c.translate(dx, dy)
        val centre = home
        // eight petals with air between them; the one under the finger steps out, the tutorial's goal is tinted
        val live = cand
        val goal = if (Coach.active && Coach.help >= 2) Coach.next?.let { target(it) } else null
        val round = android.graphics.CornerPathEffect(5 * ds())
        for (p in 0 until 8) {
            val pressed = live != null && live.petal == p
            val lift = if (pressed) 6 * ds() else 0f
            val a = p * 45 * PI / 180
            c.save(); c.translate(mx(cx + cos(a).toFloat() * lift) - mx(cx), -sin(a).toFloat() * lift)
            fill.pathEffect = round; stroke.pathEffect = round
            fill.style = Paint.Style.FILL
            fill.color = when { pressed -> col.press; goal != null && goal.first.petal == p -> col.zone; else -> col.key }
            c.drawPath(wedge(p), fill)
            stroke.color = if (pressed) col.accent else col.line; stroke.strokeWidth = (if (pressed) 2f else 1f) * density
            c.drawPath(wedge(p), stroke)
            fill.pathEffect = null; stroke.pathEffect = null
            c.restore()
        }
        fill.color = col.accent; c.drawCircle(centre[0], centre[1], 16 * ds(), fill)   // the drop
        if (goal != null) drawPath(c, goal.first, col)
        // letters of the star
        val up = svc.shiftActive()
        if (openPetal >= 0) drawOpenPetal(c, col, table, up = svc.shiftActive())
        else if (!Coach.hideLabels || !Coach.active) for (p in 0 until 8) for ((i, kind) in Pulse.KINDS.withIndex()) {
            val v = table[p][i]
            if (v.isEmpty()) continue
            val s = Pulse.spot(p, kind)
            val q = scr(s[0], s[1])
            val big = kind == 'c'
            val isGoal = goal != null && goal.first.petal == p && goal.first.kind == kind
            label(c, if (up) v.uppercase() else v, q[0], q[1], if (big) 28f else if (kind == 'b') 14f else 17f,
                if (isGoal) col.accent else if (big) col.fg else col.muted, big || isGoal)
        }
        c.restore()
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
            val bx = centre[0] + dx; val by = centre[1] + dy
            fill.color = col.accent; c.drawCircle(bx, by, 26 * density, fill)
            label(c, if (live.isSpace) "·" else if (svc.isSecure) "•" else shown, bx, by, 28f, col.accentFg, true)
        }
    }

    /** An open petal: its letters, big, each in the quarter of the star where its stroke would go. */
    private fun drawOpenPetal(c: Canvas, col: Colors, table: Array<Array<String>>, up: Boolean) {
        val ctr = scr(Pulse.PH[0], Pulse.PH[1]); val r = (Pulse.R * k).toFloat()
        fill.style = Paint.Style.FILL; fill.color = col.key; c.drawCircle(ctr[0], ctr[1], r, fill)
        // the big letter's quarter is lit, so the petal just opened is easy to recognise
        val big = Path(); val a0 = Pulse.tapAngle(openPetal, 'c') - 45
        for (i in 0..12) { val q = starPt(Pulse.R - 1, a0 + 90.0 * i / 12); if (i == 0) big.moveTo(q[0], q[1]) else big.lineTo(q[0], q[1]) }
        for (i in 12 downTo 0) { val q = starPt(15.0, a0 + 90.0 * i / 12); big.lineTo(q[0], q[1]) }
        big.close(); fill.color = col.zone; c.drawPath(big, fill)
        stroke.color = col.line; stroke.strokeWidth = 1.5f * density
        for (j in 0 until 4) { val a = Pulse.tapAngle(openPetal, 'c') + 45 + 90 * j; val s0 = starPt(15.0, a); val s1 = starPt(Pulse.R, a); c.drawLine(s0[0], s0[1], s1[0], s1[1], stroke) }
        for ((i, kind) in Pulse.KINDS.withIndex()) {
            val v = table[openPetal][i]; if (v.isEmpty()) continue
            val q = starPt(Pulse.R * 0.58, Pulse.tapAngle(openPetal, kind))
            label(c, if (svc.isSecure) "•" else if (up) v.uppercase() else v, q[0], q[1], 34f, col.fg, kind == 'c')
        }
        label(c, "×", ctr[0], ctr[1], 18f, col.muted, true)   // the centre closes it
    }

    /** Where the star should be centred now, in base units: under the finger (whole screen), or its own place. */
    private fun starOrigin(): DoubleArray? {
        if (!screenZone()) return null
        val t = touches.values.firstOrNull { it.fn == null && it.strip < 0 && !it.handle } ?: return null
        return t.jabs?.home ?: doubleArrayOf(t.pts[0], t.pts[1])
    }

    /** The whole screen, before a touch: a faint star where it would be, and a word on what to do. */
    private fun drawIdleHint(c: Canvas, col: Colors) {
        val s = scr(Pulse.PH[0], Pulse.PH[1]); val r = (Pulse.R * k).toFloat()
        stroke.color = col.accent; stroke.alpha = 90; stroke.strokeWidth = 2f * density
        c.drawCircle(s[0], s[1], r, stroke)
        for (p in 0 until 8) { val a = starPt(Pulse.R * 0.55, p * 45.0); val b = starPt(Pulse.R * 0.85, p * 45.0); c.drawLine(a[0], a[1], b[0], b[1], stroke) }
        stroke.alpha = 255
        label(c, if (svc.lang == "ru") "касайся где угодно" else "touch anywhere", s[0], s[1], 13f, col.muted)
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
    private fun wedge(p: Int): Path = wedgePaths[p] ?: makeWedge(p).also { wedgePaths[p] = it }
    private fun makeWedge(p: Int): Path {
        val path = Path(); val a0 = p * 45.0 - 22.5; val a1 = p * 45.0 + 22.5
        val inner = 22 / 1.232                       // petal-inner, in base units
        val gapOut = Math.toDegrees(2.0 / 1.232 / Pulse.R); val gapIn = Math.toDegrees(2.0 / 1.232 / inner)   // half of the 4 dp gap
        for (i in 0..8) { val q = starPt(Pulse.R - 1, a0 + gapOut + (a1 - a0 - 2 * gapOut) * i / 8); if (i == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1]) }
        for (i in 8 downTo 0) { val q = starPt(inner, a0 + gapIn + (a1 - a0 - 2 * gapIn) * i / 8); path.lineTo(q[0], q[1]) }
        path.close(); return path
    }

    /** 0 shift, 1 backspace, 2 enter, 3 hide, drawn as paths so no font glyph can be missing. */
    private fun icon(c: Canvas, which: Int, x: Float, y: Float, color: Int, halo: Int = 0) {
        if (halo != 0) { iconPath(c, which, x, y, halo, 1.8f * density + 6 * density) }
        iconPath(c, which, x, y, color, 1.8f * density)
    }
    private fun iconPath(c: Canvas, which: Int, x: Float, y: Float, color: Int, width: Float) {
        val u = 8.5f * ds()
        fill.style = Paint.Style.STROKE; fill.color = color; fill.strokeWidth = width; fill.strokeJoin = Paint.Join.ROUND; fill.strokeCap = Paint.Cap.ROUND
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
    private class Touch(val fn: Pulse.FnKey?, val strip: Int, val handle: Boolean, val field: FloatArray? = null) {
        val pts = ArrayList<Double>()
        val times = ArrayList<Long>()
        var anchorX = 0.0; var anchorY = 0.0
        var long = false
        var hold: Runnable? = null
        var repeat: Runnable? = null
        var jabs: Pulse.Jabs? = null   // «Толчки»: this touch is a word
        var cursorX = Float.NaN        // holding space: the cursor follows the finger from here (screen x)
        var steady: Pulse.Steady? = null  // the stroke, smoothed and measured to this hand
    }
    private val touches = HashMap<Int, Touch>()
    private var pressedFn: Pulse.Fn? = null
    private var pressedStrip = -1
    private var longAlt: String? = null
    private var lastShiftTap = 0L
    private var lastX = 0f

    private fun bx(x: Float) = ((mx(x) - cx) / k).toDouble()
    private fun by(y: Float) = ((cy - y) / k).toDouble()

    // ---- TalkBack: with touch exploration on, one finger arrives as hover; the keyboard takes it as a stroke ----
    private val a11y = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    private fun exploring() = a11y.isEnabled && a11y.isTouchExplorationEnabled
    init {
        contentDescription = "Клавиатура Пульк. Поставь палец и веди к букве — услышишь её, отпусти — напечатается. Касание без движения — пробел."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (!exploring()) return super.onHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> down(0, e.x, e.y, e.eventTime)
            MotionEvent.ACTION_HOVER_MOVE -> move(0, e.x, e.y, e.eventTime)
            MotionEvent.ACTION_HOVER_EXIT -> up(0, e.x, e.y, e.eventTime)
        }
        return true
    }

    // the sound compass: say what the finger is on, each time it changes
    private var heard: Pulse.Slot? = null
    private fun compass(s: Pulse.Slot?) {
        if (s == null || (svc.prefs.compass == "off" && !exploring())) return
        val h = heard
        if (h != null && h.petal == s.petal && h.kind == s.kind) return
        heard = s
        svc.feedback.compass(s, if (svc.shiftActive()) s.v.uppercase() else s.v, svc.lang, svc.isSecure)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // with TalkBack the finger comes as hover; its double-tap would type a second time
        if (exploring()) return true
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

    /** The finger never went far from where it landed (a screen reports a few jittery moves even for a tap). */
    private fun isTap(t: Touch, b: DoubleArray): Boolean {
        var far = hypot(b[0] - t.pts[0], b[1] - t.pts[1])
        for (i in 1 until t.pts.size / 2) far = max(far, hypot(t.pts[2 * i] - t.pts[0], t.pts[2 * i + 1] - t.pts[1]))
        return far < Pulse.P_MIN
    }

    /** «Два касания»: the first tap opens a petal, the second picks a letter in it; the centre is a space (or closes). */
    private fun tapStep(bx: Double, by: Double) {
        val t = Pulse.table(svc.lang, svc.symbols)
        val d = hypot(bx - Pulse.PH[0], by - Pulse.PH[1])
        if (openPetal < 0) {
            if (d < 15 || d > Pulse.R + 10) { svc.space(); return }
            openPetal = Pulse.petalOf(bx - Pulse.PH[0], by - Pulse.PH[1])
            svc.feedback.petal(openPetal, t[openPetal].filter { it.isNotEmpty() }, svc.lang, svc.isSecure)
            return
        }
        val s = Pulse.tapPick(t, openPetal, bx, by)
        openPetal = -1
        if (s == null) svc.feedback.mode() else svc.typeLetter(s.v, s)
    }

    private fun jabSlot(pts: DoubleArray) = helped(Pulse.jab(Pulse.table(svc.lang, svc.symbols), pts))

    private fun chipAt(x: Float, y: Float): Int = chipRects().indexOfFirst { it.contains(x, y) }

    private fun down(id: Int, x: Float, y: Float, tm: Long) {
        heard = null
        lastX = x
        if (!isOpen || anim != null) {
            val t = Touch(null, -1, handle = true); touches[id] = t
            if (!isOpen) open(sound = true)
            return
        }
        // the text window: dragged by the finger, wherever it is handy
        fieldRect()?.let { f ->
            if (f.contains(x, y)) {
                if (onGrip(f, x) || fieldLay == null) { touches[id] = Touch(null, -1, handle = false, field = floatArrayOf(x - f.left, y - f.top, x, y)); fieldDrag = floatArrayOf(f.left, f.top) }
                else { touches[id] = Touch(null, -1, handle = false, field = floatArrayOf(Float.NaN, 0f, x, y)); curText = svc.textBefore + svc.textAfter; curAt = svc.textBefore.length; slideCursor(x, y) }
                invalidate(); return
            }
        }
        val b = doubleArrayOf(bx(x), by(y))
        val chip = if (svc.cands.isNotEmpty()) chipAt(x, y) else -1
        val inStrip = chip >= 0
        val fn = if (inStrip) null else Pulse.hit(b[0], b[1])?.takeUnless { hiddenInSimple(it.fn) }
        val t = Touch(fn, chip, handle = false)
        if (fn == null && !inStrip) t.steady = Pulse.Steady(b[0], b[1], svc.prefs.tol.toDouble(), svc.prefs.smooth.toDouble())
        t.pts.add(b[0]); t.pts.add(b[1]); t.times.add(tm); t.anchorX = b[0]; t.anchorY = b[1]
        touches[id] = t
        svc.feedback.touch()
        when {
            inStrip -> pressedStrip = chip
            fn == null -> if (jabsMode()) t.jabs = Pulse.Jabs(b[0], b[1]) else { updateCand(t); armHold(t) }
            else -> {
                pressedFn = fn.fn
                if (fn.fn == Pulse.Fn.BKSP) {
                    svc.backspace()
                    val rep = object : Runnable { override fun run() { svc.backspace(); handler.postDelayed(this, 65) } }
                    t.repeat = rep; handler.postDelayed(rep, 420)
                }
                if (fn.fn == Pulse.Fn.SPACE && gest("cursor")) {   // held still: the cursor rides on the finger, left and right
                    val r = Runnable {
                        val moved = hypot(t.pts[t.pts.size - 2] - t.pts[0], t.pts[t.pts.size - 1] - t.pts[1])
                        if (moved < Pulse.GAP_MIN / 2) { t.cursorX = lastX; svc.feedback.mode(); invalidate() }
                    }
                    t.hold = r; handler.postDelayed(r, CURSOR_HOLD_MS)
                }
                if (fn.fn == Pulse.Fn.HIDE) {   // held: the settings, from the keyboard itself
                    val r = Runnable { t.long = true; pressedFn = null; svc.feedback.mode(); svc.openSettings(); invalidate() }
                    t.hold = r; handler.postDelayed(r, 550)
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
        return helped(Pulse.resolve(Pulse.table(svc.lang, svc.symbols), pts, tapIsSpace()))
    }

    private fun helped(slot: Pulse.Slot): Pulse.Slot {
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
        // in «Два касания» a tap is not a letter: nothing to show until the finger really strokes
        if (tapsMode() && (t.pts.size < 4 || hypot(t.pts[t.pts.size - 2] - t.pts[0], t.pts[t.pts.size - 1] - t.pts[1]) < Pulse.P_MIN)) { cand = null; trail = null; return }
        cand = resolveNow(t.pts.toDoubleArray())
        if (t.pts.size > 2) compass(cand)
        longAlt = if (t.long) cand?.let { Pulse.ALT[it.v] } else null
        trail = ArrayList(t.pts)
    }

    /** Holding still at the end of a stroke gives the alternative: е → ё, ь → ъ, ? → !. */
    private fun armHold(t: Touch) {
        if (!gest("hold")) return
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
        t.field?.let { f -> if (f[0].isNaN()) slideCursor(x, y) else { fieldDrag = floatArrayOf(x - f[0], y - f[1]); invalidate() }; return }
        val b = doubleArrayOf(bx(x), by(y))
        t.steady?.let { s -> val q = s.at(b[0], b[1], tm); b[0] = q[0]; b[1] = q[1] }
        if (t.strip >= 0) { val s = chipAt(x, y); if (s != pressedStrip) { pressedStrip = s; invalidate() }; return }
        t.pts.add(b[0]); t.pts.add(b[1]); t.times.add(tm)
        lastX = x
        if (t.fn != null) {
            if (!t.cursorX.isNaN()) {
                val step = CURSOR_STEP_DP * density
                val n = ((x - t.cursorX) / step).toInt()
                if (n != 0) { svc.moveCursor(n); t.cursorX += n * step }
            }
            return
        }
        val j = t.jabs
        if (j != null) {
            val done = j.add(b[0], b[1])
            if (done != null) {
                val s = jabSlot(done)
                svc.typeLetter(s.v, s)
                cand = null; trail = null
            } else if (j.isOut) {
                j.current()?.let { cand = jabSlot(it); trail = it.toList(); compass(cand) }
            }
            invalidate(); return
        }
        if (hypot(b[0] - t.anchorX, b[1] - t.anchorY) > 4) { t.anchorX = b[0]; t.anchorY = b[1]; t.long = false; armHold(t) }
        updateCand(t); invalidate()
    }

    private fun up(id: Int, x: Float, y: Float, tm: Long) {
        val t = touches.remove(id) ?: return
        stopTimers(t)
        if (t.handle) return
        t.field?.let { f ->
            if (f[0].isNaN()) { slideCursor(x, y); curText = null; invalidate(); return }
            val r = fieldRect()
            fieldDrag = null
            if (r != null) {
                if (hypot(x - f[2], y - f[3]) < 10 * density) { if (svc.prefs.sound == "voice" && !svc.isSecure) svc.feedback.say(svc.textBefore + svc.textAfter, 1.1f) }
                else { svc.prefs.fieldX = r.left / width; svc.prefs.fieldUp = (height - r.top) / density }
            }
            svc.insetsChanged(); invalidate(); return
        }
        val b = doubleArrayOf(bx(x), by(y))
        t.steady?.let { s -> val q = s.at(b[0], b[1], tm); b[0] = q[0]; b[1] = q[1] }
        when {
            t.strip >= 0 -> { val s = pressedStrip; pressedStrip = -1; if (s >= 0) svc.pick(s) }
            t.fn == null && t.jabs != null -> {
                t.jabs!!.finish()?.let { val s = jabSlot(it); svc.typeLetter(s.v, s) }
                svc.space()   // lifting the finger ends the word
            }
            t.fn == null && tapsMode() && isTap(t, b) -> tapStep(t.pts[0], t.pts[1])
            t.fn == null -> {
                if (tapsMode()) openPetal = -1   // a real stroke: typed as in «Пульс»
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
                    Pulse.Fn.HIDE -> if (!t.long) hide()
                    Pulse.Fn.ENTER -> svc.enter()
                    // a swipe along ⌫ towards the star: the rest of the word goes too
                    Pulse.Fn.BKSP -> if (gest("bkspword") && Pulse.dirOf(sx, sy, b[0] - sx, b[1] - sy, Pulse.GAP_MIN) == "ccw") svc.deleteWord()
                    Pulse.Fn.SPACE -> if (!t.cursorX.isNaN()) Unit else if (!gest("space")) svc.space() else when (Pulse.dirOf(sx, sy, b[0] - sx, b[1] - sy, Pulse.GAP_MIN)) {
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

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // ⌫ runs along the screen's edge, where Android's own back gesture starts: keep that edge for ⌫
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val key = Pulse.FN_KEYS.first { it.fn == Pulse.Fn.BKSP }
            val bounds = RectF(); sector((key.r0 * k).toFloat(), (key.r1 * k).toFloat(), key.a0, key.a1).computeBounds(bounds, true)
            systemGestureExclusionRects = listOf(Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
        }
    }

    companion object {
        const val STRIP_DP = 60f
        const val CURSOR_HOLD_MS = 380L
        const val CURSOR_STEP_DP = 9f
        const val FIELD_BAND_DP = 68f
    }
}
