package app.pulk

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * «Мой пруд»: every step that worked — in lessons, drills, «Говорить» — drops a «пульк» into the pond. Drops only
 * gather, never go away; what each milestone opens is shown in advance. Play that is predictable: no race, no loss.
 */
object Pond {
    data class Milestone(val drops: Int, val what: String)
    val MILESTONES = listOf(
        Milestone(5, "Капли в пруду становятся разноцветными"),
        Milestone(15, "Тема «Игра» в тренировках: динозавры, роботы, смешные слова"),
        Milestone(30, "В пруду появляются рыбки"),
        Milestone(50, "Пруд светится по вечерам"),
    )
    fun add(prefs: Prefs, n: Int = 1): Int { prefs.drops = prefs.drops + n; return prefs.drops }
    fun next(drops: Int) = MILESTONES.firstOrNull { it.drops > drops }
    fun open(drops: Int, m: Milestone) = drops >= m.drops
    /** «Игра» is open once the pond has 15 drops — something to look forward to, then always there. */
    fun playOpen(prefs: Prefs) = prefs.drops >= 15
}

class PondActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = Prefs(this); val ui = Ui(this)
        val body = ui.column()
        body.addView(ui.title("Мой пруд"))
        val n = prefs.drops
        body.addView(ui.text("Капель: $n", 22f, ui.accent, true))
        body.addView(PondView(this, n, ui.accent, ui.line, ui.panel), LinearLayout.LayoutParams(-1, ui.px(260)))
        body.addView(ui.note("Каждая получившаяся фраза или шаг — капля. Капли только копятся."))
        Pond.next(n)?.let { body.addView(ui.card(ui.text("Ещё ${it.drops - n} — и откроется: ${it.what}", 16f))) }
        body.addView(ui.h("Что открывается"))
        for (m in Pond.MILESTONES) body.addView(ui.text((if (Pond.open(n, m)) "✓ " else "○ ") + "${m.drops} капель — ${m.what}", 16f, if (Pond.open(n, m)) ui.ink else ui.muted))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Готово", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }

    /** The pond: drops as soft circles, placed by a fixed pattern so they never jump around between visits. */
    private class PondView(ctx: Context, val n: Int, val accent: Int, val edge: Int, val water: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription = "Пруд, капель: $n" + (if (n >= 5) ", разноцветные" else "") + (if (n >= 30) ", плавают две рыбки" else "") }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 8
            // 50 drops, after six in the evening: the pond glows
            val glow = n >= 50 && java.time.LocalTime.now().hour >= 18
            p.style = Paint.Style.FILL; p.color = if (glow) (accent and 0x00FFFFFF) or (0x40 shl 24) else water; c.drawOval(cx - r * 1.4f, cy - r, cx + r * 1.4f, cy + r, p)
            p.style = Paint.Style.STROKE; p.color = edge; p.strokeWidth = 2f; c.drawOval(cx - r * 1.4f, cy - r, cx + r * 1.4f, cy + r, p)
            p.style = Paint.Style.FILL; p.color = accent
            val d = resources.displayMetrics.density
            val colours = intArrayOf(accent, 0xFF4CAF7D.toInt(), 0xFFE0A030.toInt(), 0xFF8E7CC3.toInt(), 0xFFE57373.toInt())
            for (i in 0 until minOf(n, 120)) {
                // sunflower pattern: even, calm, the same every time
                val a = i * 2.39996; val rr = sqrt(i.toDouble() / 120) * (r - 10)
                p.color = if (n >= 5) colours[i % colours.size] else accent   // 5 drops: colours
                p.alpha = 150 + (i * 37) % 100
                c.drawCircle(cx + (cos(a) * rr * 1.4).toFloat(), cy + (sin(a) * rr).toFloat(), 5 * d, p)
            }
            p.alpha = 255
            // 30 drops: two fish, always in the same places
            if (n >= 30) for ((fx, fy) in listOf(-0.55f to -0.35f, 0.5f to 0.4f)) {
                val x = cx + fx * r * 1.4f; val y = cy + fy * r
                p.color = 0xFFE0A030.toInt(); c.drawOval(x - 14 * d, y - 7 * d, x + 14 * d, y + 7 * d, p)
                val tail = android.graphics.Path(); tail.moveTo(x + 12 * d, y); tail.lineTo(x + 24 * d, y - 8 * d); tail.lineTo(x + 24 * d, y + 8 * d); tail.close(); c.drawPath(tail, p)
            }
        }
    }
}
