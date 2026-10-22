package app.pulk

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * «Настройка под руку»: 30 seconds. First the thumb rests still on a circle (its tremor), then sixteen strokes in
 * the directions asked (their length and real aim). The keyboard then fits these: no settings to understand.
 */
class CalibrationActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var feel: Feedback
    private lateinit var ui: Ui
    private val bg get() = ui.bg
    private val line get() = ui.line
    private val fg get() = ui.ink
    private val muted get() = ui.muted
    private val accent get() = ui.accent
    private val dp get() = resources.displayMetrics.density
    /** Strokes that came out too short and were asked again: many of them say stroking is hard. */
    private var retries = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); feel = Feedback(this, prefs); ui = Ui(this)
        start()
    }
    override fun onDestroy() { feel.shutdown(); super.onDestroy() }

    private fun start() { retries = 0; setContentView(CalibView(this)) }

    /** «Что мы подстроили»: each thing found has its own switch; and if strokes were hard, «Два касания» is offered. */
    private fun done(r: Calibration.Result) {
        val body = ui.column(32)
        body.addView(ui.title("Что мы подстроили"))
        val lines = Calibration.describe(r)
        var useLen = true; var useShake = r.smooth > 0; var useAim = true
        body.addView(ui.card(ui.text(lines[0])).also { it.addView(ui.onOff("Подстроить под длину смахов", { useLen }) { v -> useLen = v }) })
        body.addView(ui.card(ui.text(lines[1])).also { it.addView(ui.onOff("Фильтр дрожи", { useShake }) { v -> useShake = v }) })
        body.addView(ui.card(ui.text(lines[2])).also { it.addView(ui.onOff("Подстроить направления", { useAim }) { v -> useAim = v }) })
        val hard = retries >= 4 || r.tremorMm >= 1.2
        if (hard) body.addView(ui.card(ui.text("Смахивать тяжело? Есть способ без смахов: «Два касания» — коснись лепестка, потом буквы.", 16f, ui.ink, true),
            ui.button("Включить «Два касания»") { prefs.mode = "taps"; (it as Button).text = "✓ «Два касания» включены" }))
        body.addView(ui.note("Пройти заново можно в любой момент: главная → «Под мою руку»."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сохранить", main = true) {
            prefs.tol = if (useLen) r.tol.toFloat() else 1f
            prefs.smooth = if (useShake) maxOf(r.smooth, 0.35).toFloat() else 0f
            prefs.bias = if (useAim) r.bias.joinToString(",") { "%.1f".format(java.util.Locale.US, it) } else ""
            prefs.calibrated = true; prefs.applyBias(); finish()
        })
        bottom.addView(ui.button("Пройти ещё раз") { start() })
        setContentView(ui.screen(body, bottom))
    }

    /** The whole screen: the circle to hold, then the arrow of the direction asked for. */
    private inner class CalibView(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val plan = Calibration.plan()
        private var step = -1                          // -1: holding still; 0..15: strokes
        private val holdX = ArrayList<Double>(); private val holdY = ArrayList<Double>()
        private var holdStart = 0L
        private var tremor = 0.0
        private val strokes = ArrayList<Calibration.Stroke>()
        private var downX = 0f; private var downY = 0f; private var far = 0f; private var farX = 0f; private var farY = 0f
        private var note = ""
        private val left = prefs.left

        private fun kDp(): Double {
            val m = resources.displayMetrics
            return Pulse.scaleDp(m.widthPixels / m.density, m.heightPixels / m.density, prefs.size, prefs.blind).toDouble()
        }
        /** Where the thumb rests: about where the star is on the keyboard. */
        private fun restX() = if (left) width * 0.3f else width * 0.7f
        private fun restY() = height * 0.72f

        /** What TalkBack says: the same words the screen draws; read aloud whenever they change. */
        private fun say() = if (step < 0) "Настройка под руку. Положи большой палец на круг внизу и держи, не двигая, 2 секунды"
            else "Смахни ${Lessons.dir(plan[step] * 45, left)}, с любого места. ${step + 1} из ${plan.size}. $note".trim()

        override fun onDraw(c: Canvas) {
            val s = say()
            if (contentDescription != s) { contentDescription = s; announceForAccessibility(s) }
            c.drawColor(bg)
            txt.textSize = 22f * dp; txt.isFakeBoldText = true; txt.color = fg
            val cx = restX(); val cy = restY()
            if (step < 0) {
                c.drawText("Настройка под руку", width / 2f, height * 0.14f, txt)
                txt.textSize = 17f * dp; txt.isFakeBoldText = false
                c.drawText("Положи большой палец на круг", width / 2f, height * 0.22f, txt)
                c.drawText("и держи, не двигая, 2 секунды", width / 2f, height * 0.22f + 26 * dp, txt)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = accent
                c.drawCircle(cx, cy, 34 * dp, paint)
                if (holdStart > 0) {
                    val p = ((SystemClock.uptimeMillis() - holdStart) / 2000f).coerceIn(0f, 1f)
                    paint.strokeWidth = 6 * dp
                    c.drawArc(cx - 44 * dp, cy - 44 * dp, cx + 44 * dp, cy + 44 * dp, -90f, 360 * p, false, paint)
                    postInvalidateOnAnimation()
                }
            } else {
                val p = plan[step]
                val dir = Lessons.dir(p * 45, left)
                c.drawText("Смахни $dir", width / 2f, height * 0.16f, txt)
                txt.textSize = 15f * dp; txt.isFakeBoldText = false; txt.color = muted
                c.drawText("как тебе удобно, с любого места · ${step + 1} из ${plan.size}", width / 2f, height * 0.16f + 26 * dp, txt)
                if (note.isNotEmpty()) { txt.color = accent; c.drawText(note, width / 2f, height * 0.16f + 50 * dp, txt) }
                // the eight directions, the asked one lit
                for (q in 0 until 8) {
                    var a = q * 45.0; if (left) a = 180 - a
                    val r0 = 26 * dp; val r1 = if (q == p) 96 * dp else 70 * dp
                    val ax = cos(a * PI / 180).toFloat(); val ay = -sin(a * PI / 180).toFloat()
                    paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = (if (q == p) 7 else 3) * dp; paint.color = if (q == p) accent else line
                    c.drawLine(cx + ax * r0, cy + ay * r0, cx + ax * r1, cy + ay * r1, paint)
                    if (q == p) {
                        val hx = cx + ax * r1; val hy = cy + ay * r1; val bx = -ax; val by = -ay
                        val path = Path(); path.moveTo(hx, hy)
                        path.lineTo(hx + (bx * 18 - by * 12) * dp, hy + (by * 18 + bx * 12) * dp); path.moveTo(hx, hy)
                        path.lineTo(hx + (bx * 18 + by * 12) * dp, hy + (by * 18 - bx * 12) * dp)
                        c.drawPath(path, paint)
                    }
                }
            }
        }

        // with TalkBack one finger comes as hover (as on the keyboard itself): it is taken as the touch it is
        private val a11y = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        private fun exploring() = a11y.isEnabled && a11y.isTouchExplorationEnabled
        private var fromHover = false
        override fun onHoverEvent(e: MotionEvent): Boolean {
            if (!exploring()) return super.onHoverEvent(e)
            val act = when (e.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> MotionEvent.ACTION_DOWN
                MotionEvent.ACTION_HOVER_MOVE -> MotionEvent.ACTION_MOVE
                MotionEvent.ACTION_HOVER_EXIT -> MotionEvent.ACTION_UP
                else -> return true
            }
            val t = MotionEvent.obtain(e.downTime, e.eventTime, act, e.x, e.y, 0)
            fromHover = true; onTouchEvent(t); fromHover = false; t.recycle()
            return true
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (exploring() && !fromHover) return true   // TalkBack's double-tap would be a stroke of nothing
            val x = e.x; val y = e.y
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = x; downY = y; far = 0f; farX = x; farY = y
                    if (step < 0) { holdX.clear(); holdY.clear(); holdStart = SystemClock.uptimeMillis(); invalidate() }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (step < 0) {
                        if (SystemClock.uptimeMillis() - holdStart > 300) { holdX.add((x / dp).toDouble()); holdY.add((y / dp).toDouble()) }
                        if (SystemClock.uptimeMillis() - holdStart >= 2000) finishHold()
                    } else {
                        val d = hypot(x - downX, y - downY); if (d > far) { far = d; farX = x; farY = y }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (step < 0) { if (SystemClock.uptimeMillis() - holdStart >= 2000) finishHold() else { holdStart = 0; invalidate() } }
                    else {
                        val k = kDp() * dp
                        if (far < 8 * dp) { note = "Ещё раз — чуть длиннее"; retries++; invalidate(); return true }
                        var bx = (farX - downX) / k; val by = -(farY - downY) / k
                        if (left) bx = -bx
                        strokes.add(Calibration.Stroke(plan[step], bx, by))
                        feel.touch(); note = ""
                        step++
                        if (step >= plan.size) done(Calibration.compute(tremor, strokes)) else invalidate()
                    }
                }
            }
            return true
        }

        private fun finishHold() {
            if (step >= 0) return
            tremor = Calibration.tremorMm(holdX, holdY)
            feel.mode(); step = 0; holdStart = 0; invalidate()
        }
    }
}
