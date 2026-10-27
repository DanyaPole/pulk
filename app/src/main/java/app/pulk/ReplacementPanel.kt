package app.pulk

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/** A review stays open until the person closes it. No delayed actions and no phrase speech here. */
internal class ReplacementPanel(context: Context, prefs: Prefs,
    private val bank: ReplacementBank, listen: (Int) -> Unit, accept: (Int) -> Unit,
    keep: (Int) -> Unit, keepAll: () -> Unit, undo: () -> Unit, close: () -> Unit,
    private val finishing: Boolean = false
) : LinearLayout(context) {
    private val ui = Ui(context)
    private var position = 0
    private val progress = ui.text("", 16f, ui.muted)
    private val pair = ui.text("", 28f, bold = true).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val contextText = ui.text("", 18f)
    private val buttons = HashMap<String, Button>()
    private fun current() = bank.pending().getOrNull(position)?.index
    init {
        orientation = VERTICAL; setBackgroundColor(ui.bg)
        val body = ui.column(8).apply {
            addView(ui.title("Копилка замен")); addView(progress); addView(pair); addView(contextText)
            addView(ui.note("Принять — только заменить слово. Сказать фразу можно после разбора обычным действием."))
        }
        addView(ScrollView(context).apply { addView(body) }, LayoutParams(-1, 0, 1f))
        val bottom = ui.bottomBar()
        fun row(vararg actions: Pair<String, () -> Unit>) {
            val r = LinearLayout(context).apply {
                orientation = HORIZONTAL; isBaselineAligned = false
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            // The app has no global supportsRtl flag: mirror the control order explicitly.
            for ((label, fn) in if (prefs.left) actions.reversed() else actions.toList()) {
                val b = ui.button(label, main = label == "Принять") { fn(); refresh() }.apply {
                    contentDescription = label; layoutDirection = View.LAYOUT_DIRECTION_LTR
                    minHeight = ui.px(if (label == "Принять") 64 else 56); minimumHeight = minHeight
                    setPadding(ui.px(6), ui.px(8), ui.px(6), ui.px(8))
                    layoutParams = LayoutParams(0, -2, 1f).apply { setMargins(ui.px(2), ui.px(4), ui.px(2), 0) }
                }
                buttons[label] = b; r.addView(b)
            }
            bottom.addView(r)
        }
        row("Услышать" to { current()?.let(listen) }, "Принять" to { current()?.let(accept) })
        row("Оставить" to { current()?.let(keep) }, "Вернуть" to undo)
        row("Назад" to { position = maxOf(0, position - 1) }, "Дальше" to { position++ })
        row("Оставить всё" to keepAll, "К набору" to close)
        addView(bottom, LayoutParams(-1, -2)); refresh()
    }
    fun refresh() {
        val pending = bank.pending(); position = position.coerceIn(0, maxOf(0, pending.lastIndex))
        val p = pending.getOrNull(position)
        progress.text = if (p == null) "Всё разобрано" else "${position + 1} из ${pending.size}"
        pair.text = p?.let { "${it.before} → ${it.after}" } ?: "Можно вернуться к набору"
        pair.contentDescription = p?.let { "Вышло „${it.before}“. Заменить на „${it.after}“?" } ?: pair.text
        contextText.text = p?.context.orEmpty()
        buttons["К набору"]?.apply {
            text = if (finishing && pending.isEmpty()) "К завершению" else "К набору"
            contentDescription = text
        }
        for (label in listOf("Услышать", "Принять", "Оставить")) buttons[label]?.isEnabled = p != null
        buttons["Назад"]?.isEnabled = position > 0
        buttons["Дальше"]?.isEnabled = position < pending.lastIndex
    }
}
