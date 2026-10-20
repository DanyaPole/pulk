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
    private val bg = Color.parseColor("#0D1215")
    private val panel = Color.parseColor("#141B1F")
    private val line = Color.parseColor("#29343A")
    private val fg = Color.parseColor("#E4EBEE")
    private val muted = Color.parseColor("#8B9AA2")
    private val accent = Color.parseColor("#55BAD7")
    private val accentFg = Color.parseColor("#06222B")
    private lateinit var prefs: Prefs
    private lateinit var feel: Feedback
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); feel = Feedback(this, prefs)
        start()
    }
    override fun onDestroy() { feel.shutdown(); super.onDestroy() }

    private fun start() { setContentView(CalibView(this)) }

    private fun done(r: Calibration.Result) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(32), px(20), px(24)); setBackgroundColor(bg) }
        col.addView(text("Готово — Пульк знает твою руку", 24f, fg, true))
        for (s in Calibration.describe(r)) col.addView(text(s, 16f, fg))
        col.addView(text("Можно пройти заново в любой момент: «Настройки» → «Настройка под руку».", 13f, muted))
        col.addView(button("Сохранить", true) {
            prefs.tol = r.tol.toFloat(); prefs.smooth = r.smooth.toFloat()
            prefs.bias = r.bias.joinToString(",") { "%.1f".format(java.util.Locale.US, it) }
            prefs.calibrated = true; prefs.applyBias(); finish()
        })
        col.addView(button("Пройти ещё раз") { start() })
        col.addView(button("Как было по умолчанию") { prefs.tol = 1f; prefs.smooth = 0f; prefs.bias = ""; prefs.calibrated = true; prefs.applyBias(); finish() })
        setContentView(col)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); setLineSpacing(0f, 1.15f); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, px(6), 0, px(6))
    }
    private fun button(s: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 16f; minHeight = px(52); minimumHeight = px(52)
        setTextColor(if (primary) accentFg else fg)
        background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(if (primary) accent else panel); setStroke(px(1), if (primary) accent else line) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) }
        setOnClickListener { onClick() }
    }

    /** The whole screen: the circle to hold, then the arrow of the direction asked for. */
    private inner class CalibView(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = fg }
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

        override fun onDraw(c: Canvas) {
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

        override fun onTouchEvent(e: MotionEvent): Boolean {
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
                        if (far < 8 * dp) { note = "Ещё раз — чуть длиннее"; invalidate(); return true }
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
