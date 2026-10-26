package app.pulk

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Framework views only: painted glyphs and gesture hit regions still need the phone/TalkBack check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
class ScreenAccessibilityTest {
    @Test fun opaqueBoundedChips() = chips()
    @Test @Config(qualifiers = "w412dp-h892dp-night-xxhdpi") fun opaqueBoundedDarkChips() = chips()

    @Test fun bubbleHintStaysOutsideControls() = bubbleHint()
    @Test @Config(qualifiers = "w412dp-h892dp-night-xxhdpi") fun darkBubbleHintStaysOutsideControls() = bubbleHint()

    private fun bubbleHint() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx).apply { popped = 0; sound = "off"; bubblesHelp = true; bigText = true }
        for (mode in listOf("pulse", "jabs", "petals")) for (hand in listOf("right", "left")) {
            prefs.mode = mode; prefs.hand = hand
            val controller = Robolectric.buildActivity(BubblesActivity::class.java).setup()
            val a = controller.get()
            val svc = Robolectric.setupService(PulkService::class.java)
            try {
                val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val d = root.resources.displayMetrics.density
                fun visible(v: View): Rect? = Rect().takeIf { v.getGlobalVisibleRect(it) }
                for ((w, h) in listOf(412 to 892, 412 to 550, 320 to 480)) {
                    root.measure(View.MeasureSpec.makeMeasureSpec((w * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((h * d).toInt(), View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    // On a short screen the body scrolls; hidden content cannot cover the fixed footer.
                    a.guidance.requestRectangleOnScreen(Rect(0, 0, a.guidance.width, a.guidance.height), true)
                    // Robolectric's global visibility can include content clipped by a resized ScrollView.
                    val local = Rect(0, 0, a.guidance.width, a.guidance.height)
                    (root as ViewGroup).offsetDescendantRectToMyCoords(a.guidance, local)
                    val scroll = (root.getChildAt(0) as android.widget.ScrollView)
                    val viewport = Rect(scroll.left, scroll.top, scroll.right, scroll.bottom)
                    assertTrue("hint outside scroll viewport: $local / $viewport", viewport.contains(local))
                    val hint = visible(a.guidance) ?: throw AssertionError("hint is not visible")
                    assertTrue(a.guidance.text.isNotBlank())
                    assertTrue("hint is clipped", hint.height() == a.guidance.height)
                    assertTrue("hint outside width: $hint", hint.left >= 0 && hint.right <= root.width)
                    for (control in all(root).filter { it is Button || it is EditText })
                        visible(control)?.let { assertTrue("hint overlaps ${label(control)} on $w x $h", !Rect.intersects(hint, it)) }
                    val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    try {
                        root.draw(Canvas(bmp))
                        val dir = java.io.File("build/shots").apply { mkdirs() }
                        java.io.File(dir, "hint-$mode-$hand-$w-$h-${if (Ui(a).bg == Color.parseColor("#0B1114")) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    } finally { bmp.recycle() }
                }
                svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = svc.packageName; fieldId = GestureTraces.BUBBLES })
                assertTrue(svc.coaching())
                val keyboard = svc.onCreateInputView() as PulseView
                assertTrue("Bubbles must not paint a second hint over the buttons", keyboard.chipRects().isEmpty())
                svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = svc.packageName; fieldId = GestureTraces.LESSON })
                Coach.traceField = GestureTraces.LESSON
                assertTrue("other lessons keep their keyboard hint", keyboard.chipRects().isNotEmpty())
                if (mode != "pulse") {
                    all(root).filterIsInstance<Button>().first { it.text.toString().contains("Выкл") }.performClick()
                    assertTrue("help can be hidden", a.guidance.visibility == View.INVISIBLE && a.guidance.text.isEmpty())
                }
            } finally { svc.onDestroy(); controller.pause().stop().destroy(); Coach.reset(); prefs.bubblesHelp = true }
        }
        prefs.bigText = false
    }

    private fun chips() {
        val svc = Robolectric.setupService(PulkService::class.java)
        try {
            svc.prefs.sound = "off"; svc.prefs.mode = "pulse"; svc.prefs.textWindow = false
            for (hand in listOf("right", "left")) for (strong in listOf(false, true)) {
                svc.prefs.hand = hand; svc.prefs.theme = if (strong) "contrast" else "auto"
                svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
                svc.javaClass.getDeclaredField("cands").apply { isAccessible = true }.set(svc, listOf("Я", "Ты", "Очень длинная подсказка"))
                val view = svc.onCreateInputView() as PulseView
                for (dp in listOf(320, 412)) {
                    val d = view.resources.displayMetrics.density
                    view.measure(View.MeasureSpec.makeMeasureSpec((dp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight); view.reset(open = true)
                    val rects = view.chipRects()
                    assertTrue(rects.size == 3)
                    for ((i, r) in rects.withIndex()) {
                        assertTrue("chip outside screen: $r", r.left >= 3 * d && r.right <= view.width - 3 * d && r.top >= 0 && r.bottom <= view.height)
                        assertTrue("small chip: $r", r.width() >= 48 * d && r.height() >= 48 * d)
                        assertTrue(rects.take(i).none { RectF.intersects(it, r) })
                    }
                    assertTrue(if (hand == "left") rects[0].centerX() < rects[1].centerX() else rects[0].centerX() > rects[1].centerX())
                    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bmp))
                        val ui = Ui(Robolectric.buildActivity(Activity::class.java).setup().get())
                        for ((i, r) in rects.withIndex()) {
                            val bg = bmp.getPixel(r.centerX().toInt(), (r.top + 10 * d).toInt())
                            assertTrue("transparent chip $i", Color.alpha(bg) == 255)
                            val ink = if (i == 0) ui.onAccent else ui.ink
                            assertTrue("chip contrast", contrast.invoke(null, ink, bg) as Double >= 4.5)
                            val edge = bmp.getPixel(r.centerX().toInt(), r.top.toInt())
                            val rim = bmp.getPixel(r.centerX().toInt(), (r.top - 2 * d).toInt())
                            for (host in listOf(Color.WHITE, Color.BLACK, Color.GRAY))
                                assertTrue("chip outline contrast", maxOf(contrast.invoke(null, edge, host) as Double, contrast.invoke(null, rim, host) as Double) >= 3.0)
                        }
                        val dir = java.io.File("build/shots").apply { mkdirs() }
                        java.io.File(dir, "chips-$hand-$strong-$dp-${if (ui.bg == Color.parseColor("#0B1114")) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    } finally { bmp.recycle() }
                }
            }
        } finally { svc.onDestroy(); Coach.reset() }
    }
    // Reuse Android's contrast and alpha-composition calculations; no second luminance implementation.
    private val colors = Class.forName("com.android.internal.graphics.ColorUtils")
    private val contrast = colors.getMethod("calculateContrast", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val composite = colors.getMethod("compositeColors", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private fun over(fg: Int, bg: Int) = composite.invoke(null, fg, bg) as Int
    private fun label(v: View): String = v.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        ?: (v as? TextView)?.let { it.text.toString().ifBlank { (it as? EditText)?.hint?.toString().orEmpty() } }.orEmpty()
    private fun all(v: View): List<View> = if (v.visibility != View.VISIBLE) emptyList() else
        listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun fill(d: Drawable?, v: View): Int? = when (d) {
        is ColorDrawable -> d.color
        is GradientDrawable -> d.color?.getColorForState(v.drawableState, d.color!!.defaultColor)
        else -> d?.current?.takeIf { it !== d }?.let { fill(it, v) }
    }

    private fun audit(root: View, base: Int): List<String> {
        val issues = arrayListOf<String>()
        fun visit(v: View, behind: Int) {
            if (v.visibility != View.VISIBLE) return
            val bg = fill(v.background, v)?.let { over(it, behind) } ?: behind
            val name = "${v.javaClass.simpleName} «${label(v).take(42)}»"
            if (v.isEnabled && v.isClickable && label(v).none { it.isLetter() }) issues += "$name: no readable label"
            if (v.isEnabled && v is Button) {
                val dp = v.resources.displayMetrics.density
                if (v.width / dp < 48 || v.height / dp < 48) issues += "$name: target ${v.width / dp} × ${v.height / dp} dp < 48"
            }
            if (v.isSelected && (v as? TextView)?.text?.contains('✓') != true &&
                !(v is CompoundButton && v.isChecked)) issues += "$name: selection needs a visible mark"
            if (v is TextView && (v.text.isNotBlank() || (v is EditText && !v.hint.isNullOrBlank()))) {
                val inks = mutableListOf(if (v.text.isNotBlank()) v.currentTextColor else v.currentHintTextColor)
                (v.text as? Spanned)?.let { s -> inks += s.getSpans(0, s.length, ForegroundColorSpan::class.java).map { it.foregroundColor } }
                for (ink in inks.distinct()) {
                    val ratio = contrast.invoke(null, over(ink, bg), bg) as Double
                    if (ratio < 4.5) issues += "$name: contrast $ratio < 4.5"
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i), bg)
        }
        visit(root, base)
        // Keyboard/D-pad order includes buttons even while the test window is in touch mode.
        val focusables = arrayListOf<View>()
        root.addFocusables(focusables, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        val order = focusables.filter { it.isEnabled && it.visibility == View.VISIBLE }
        if (order.none { label(it).any { c -> c.isLetter() } }) issues += "focus order has no labelled target"
        return issues
    }

    @Test fun lightScreens() = screens()
    @Test @Config(qualifiers = "w412dp-h892dp-night-xxhdpi") fun darkScreens() = screens()

    private fun screens() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("pulk", 0).edit().clear().commit()
        val prefs = Prefs(ctx).apply {
            wizardDone = true; lang = "ru"; mode = "jabs"; jabsWords = 10; lessonsDone = 1; sound = "off"
            phrases = listOf("Привет"); talk = Together.said(emptyList(), true, "Привет")
            family = Together.familyLines(listOf(Together.Word("Мама", "")))
        }
        val issues = arrayListOf<String>()
        var checked = 0
        fun check(a: Activity, state: String) {
            val root = ScreensTest.layout(a)
            // Draw once: custom views expose their on-screen instruction to accessibility here.
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            issues += audit(root, Ui(a).bg).map { "${a.javaClass.simpleName}/$state: $it" }
            checked++
        }
        fun <T : Activity> screen(type: Class<T>, intent: Intent? = null, states: (T) -> Unit = { check(it, "main") }) {
            val controller = Robolectric.buildActivity(type, intent).setup()
            try { states(controller.get()) } finally { controller.pause().stop().destroy(); Coach.reset() }
        }
        val activities = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES).activities
        for (info in activities) {
            @Suppress("UNCHECKED_CAST")
            screen(Class.forName(info.name) as Class<Activity>)
        }
        screen(OptionsActivity::class.java, Intent().putExtra(OptionsActivity.MORE, true))
        screen(SpeedActivity::class.java) { a ->
            fun click(text: String) = all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }.performClick()
            click("Начать"); check(a, "phrase")
            repeat(3) { click("Готово") }; check(a, "results")
        }
        screen(SpeakActivity::class.java) { a ->
            all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Показать собеседнику" }.performClick()
            check(a, "partner")
        }
        screen(TutorialActivity::class.java) { a ->
            all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Начать" }.performClick()
            check(a, "lesson")
        }
        screen(SpeakLessonActivity::class.java) { a ->
            for (step in a.steps.indices + a.steps.size) { a.step = step; a.show(); check(a, "step-$step") }
        }
        screen(DrillsActivity::class.java) { a ->
            for ((i, theme) in a.themes().withIndex()) {
                a.theme = i
                for (at in theme.tasks.indices) { a.at = at; a.scene(); check(a, "theme-$i-task-$at") }
                a.at = theme.tasks.size; a.scene(); check(a, "theme-$i-done")
            }
        }
        screen(WizardActivity::class.java) { a ->
            fun click(text: String) = all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == text }.performClick()
            click("Правой"); check(a, "fork"); click("Подстроить под человека — 2 минуты")
            for (d in Comfort.DOORS) { check(a, "door-${d.key}"); click(d.answers.first().first) }
            check(a, "summary")
        }
        println("accessibility: $checked screens, ${issues.size} findings")
        assertTrue(issues.joinToString("\n"), issues.isEmpty())
        prefs.mode = "pulse"
    }

    @Test fun checkerRejectsUnlabelledSmallColourOnlyAndLowContrastControls() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        repeat(2) { n -> root.addView(Button(a).apply {
            text = if (n == 0) "" else "Проверка"; setTextColor(Color.LTGRAY); setBackgroundColor(Color.WHITE)
            isSelected = true; isFocusable = false
            layoutParams = LinearLayout.LayoutParams(24, 24)
        }) }
        a.setContentView(root); ScreensTest.layout(a)
        val issues = audit(root, Color.WHITE).joinToString("\n")
        for (rule in listOf("no readable label", "target", "visible mark", "contrast", "focus order"))
            assertTrue("Checker missed $rule: $issues", rule in issues)
    }
}
