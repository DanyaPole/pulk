package app.pulk

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/** The two decisions are separate. Closing either step keeps the editor exactly as it is. */
internal class CompletionPanel(context: Context, private val svc: PulkService) : LinearLayout(context) {
    private val ui = Ui(context)
    private val title = ui.title("")
    private val progress = ui.text("", 16f, ui.muted)
    private val proposal = ui.text("", 26f, bold = true).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val bottom = ui.bottomBar()
    private val actions = LinkedHashMap<Int, Button>()
    init {
        orientation = VERTICAL; setBackgroundColor(ui.bg)
        addView(ScrollView(context).apply {
            addView(ui.column(8).apply {
                addView(title); addView(progress); addView(proposal)
                addView(ui.note("Сказать фразу можно после выбора обычным действием."))
            })
        }, LayoutParams(-1, 0, 1f))
        addView(bottom, LayoutParams(-1, -2)); refresh()
    }
    private var step: Boolean? = null
    fun refresh() {
        val r = svc.completion ?: return
        if (step == r.punctuation) return
        step = r.punctuation
        bottom.removeAllViews(); actions.clear()
        title.text = if (r.punctuation) "Знак" else "Допиши фразу"
        progress.text = if (r.punctuation) "Шаг 2 из 2" else "Шаг 1 из 2"
        proposal.text = if (r.punctuation) "Выбери знак или оставь без знака" else "Дописать: „${r.tail}“?"
        fun row(vararg specs: Triple<String, String, () -> Unit>) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            for ((label, description, action) in if (svc.prefs.left) specs.reversed() else specs.toList()) {
                val b = ui.button(label, main = label == "Принять" || description.endsWith("предложен первым")) { action() }.apply {
                    contentDescription = description
                    minHeight = ui.px(if (label == "Принять") 64 else 56); minimumHeight = minHeight
                    setPadding(ui.px(6), ui.px(8), ui.px(6), ui.px(8))
                    layoutParams = LayoutParams(0, -2, 1f).apply { setMargins(ui.px(2), ui.px(4), ui.px(2), 0) }
                }
                actions[0x00f00100 + actions.size] = b; row.addView(b)
            }
            bottom.addView(row)
        }
        fun spec(label: String, action: () -> Unit) = Triple(label, label, action)
        if (!r.punctuation) {
            row(spec("Услышать", svc::listenCompletion), spec("Принять", svc::acceptCompletion))
            row(spec("Оставить", svc::keepCompletion), spec("Вернуть", svc::undoCompletion))
        } else {
            val hints = svc.completionHints()
            val signs = hints.mapIndexed { i, sign ->
                val name = when (sign) { "." -> "точка"; "?" -> "вопрос"; else -> "восклицание" }
                Triple(if (i == 0) "$sign · подсказка" else sign, name + if (i == 0) ", предложен первым" else "", { svc.choosePunctuation(sign) })
            } + spec("Без знака") { svc.choosePunctuation("") }
            signs.chunked(2).forEach { row(*it.toTypedArray()) }
            row(spec("Вернуть", svc::undoCompletion), spec("К набору", svc::closeCompletion))
        }
        if (!r.punctuation) row(spec("К набору", svc::closeCompletion))
        requestLayout(); svc.insetsChanged()
    }
    internal fun addActions(info: AccessibilityNodeInfo) {
        for ((id, b) in actions) info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, b.contentDescription))
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(info); addActions(info) }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean =
        actions[action]?.performClick() ?: super.performAccessibilityAction(action, arguments)
}
