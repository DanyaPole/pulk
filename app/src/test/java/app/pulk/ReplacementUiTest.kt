package app.pulk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplacementUiTest {
    private class Field : TextSink {
        var text = ""; var cursor = 0; val actions = ArrayList<Int>()
        override fun commit(s: String) { text = text.substring(0, cursor) + s + text.substring(cursor); cursor += s.length }
        override fun deleteBefore(n: Int) { val start = maxOf(0, cursor - n); text = text.removeRange(start, cursor); cursor = start }
        override fun before(n: Int) = text.substring(maxOf(0, cursor - n), cursor)
        override fun key(code: Int) { if (code == KeyEvent.KEYCODE_DEL) deleteBefore(1) else if (code == KeyEvent.KEYCODE_ENTER) commit("\n") }
        override fun action(a: Int) { actions.add(a) }
        override fun capsOn(inputType: Int) = false
        override fun moveCursor(delta: Int) { cursor = (cursor + delta).coerceIn(0, text.length) }
    }
    private lateinit var svc: PulkService
    private lateinit var field: Field
    private lateinit var view: PulseView
    private val trace = ArrayList<String>()

    @Before fun setup() {
        Coach.reset(); svc = Robolectric.setupService(PulkService::class.java)
        svc.prefs.apply { sound = "off"; haptics = false; mode = "petals"; lang = "ru"; hand = "right"; bigText = true; learn = false; autocorrect = false; family = emptyList(); myPhrases = emptyList() }
        svc.words("ru")
        @Suppress("UNCHECKED_CAST")
        val dicts = svc.javaClass.getDeclaredField("dicts").apply { isAccessible = true }.get(svc) as MutableMap<String, Words>
        synchronized(svc) { dicts["ru"] = Words(listOf("дом", "том", "стоит"), "ru").apply { loadNext(listOf("том\tстоит")) } }
        field = Field(); svc.sink = field
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; initialSelStart = 0; initialSelEnd = 0; imeOptions = EditorInfo.IME_ACTION_SEND })
        svc.feedback.trace = { trace.add(it) }
        view = svc.onCreateInputView() as PulseView; view.reset(open = true); layout()
    }
    private fun layout() {
        val d = view.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((320 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun button(label: String) = all(view).filterIsInstance<Button>().first { it.text.toString() == label }
    private fun phrase() {
        val previous = svc.replacements!!.bank.count()
        svc.beginPetals()
        Pulse.PetalIndex(emptyList(), Pulse.table("ru", false)).key("дом")!!.forEach { svc.addPetal(it - '0') }
        svc.finishPetals()
        for (c in "стоит") svc.typeLetter(c.toString(), Pulse.Slot(0, 'c', c.toString()))
        svc.space(); layout(); assertEquals(previous + 1, svc.replacements!!.bank.count())
    }
    private fun shot(name: String) {
        val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(b))
        File("build/shots").mkdirs(); File("build/shots/$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }; b.recycle()
    }
    @Test fun phraseEndReviewsWithoutSendingAndAcceptUndoChangeTheActualEditorSilently() {
        phrase(); svc.enter(); layout()
        assertTrue(view.reviewingReplacements); assertTrue(field.actions.isEmpty()); assertEquals("дом стоит ", field.text)
        button("Принять").performClick(); assertEquals("том стоит ", field.text); assertTrue(trace.none { it.startsWith("say ") })
        button("Вернуть").performClick(); assertEquals("дом стоит ", field.text)
        button("Принять").performClick(); button("К завершению").performClick(); svc.enter()
        layout(); button("Без знака").performClick(); svc.enter()
        assertEquals(listOf(EditorInfo.IME_ACTION_SEND), field.actions)
        svc.onDestroy()
    }
    @Test fun keepAllCloseAndWaitingAreSilentAndKeepAllCanBeUndone() {
        phrase(); svc.reviewReplacements(); layout(); val before = field.text
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)
        assertEquals(before, field.text); assertEquals(1, svc.replacements!!.bank.count())
        button("Оставить всё").performClick(); assertEquals(0, svc.replacements!!.bank.count()); assertEquals(before, field.text)
        button("Вернуть").performClick(); assertEquals(1, svc.replacements!!.bank.count())
        button("К набору").performClick(); assertEquals(before, field.text); assertTrue(field.actions.isEmpty()); assertTrue(trace.none { it.startsWith("say ") })
        svc.onDestroy()
    }
    @Test fun listenIsTheOnlyReviewActionThatSpeaksAndDoesNotEdit() {
        phrase(); svc.reviewReplacements(); layout(); button("Услышать").performClick()
        assertEquals("дом стоит ", field.text); assertEquals(1, trace.count { it.startsWith("say ") }); svc.onDestroy()
    }
    @Test fun navigationKeepsStableIndicesAndKeepDoesNotChangeText() {
        phrase(); phrase(); assertEquals(2, svc.replacements!!.bank.count()); svc.reviewReplacements(); layout()
        button("Дальше").performClick(); button("Оставить").performClick()
        assertTrue(svc.replacements!!.bank.pending().isEmpty()) // keep suppresses this pair throughout the phrase
        button("Вернуть").performClick(); assertEquals(listOf(0, 2), svc.replacements!!.bank.pending().map { it.index })
        button("Дальше").performClick(); button("Назад").performClick(); button("Принять").performClick()
        assertEquals("том стоит дом стоит ", field.text); svc.onDestroy()
    }
    @Test fun speakingScreenButtonUsesTheSameReviewGate() {
        val a = Robolectric.buildActivity(SpeakActivity::class.java).setup().get()
        val f = all(a.window.decorView).filterIsInstance<android.widget.EditText>().first()
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = svc.packageName; fieldId = Ui.ID_SPEAK })
        phrase(); f.setText(field.text); a.sayAll()
        assertTrue(view.reviewingReplacements); assertEquals(field.text, f.text.toString()); assertTrue(a.lastSaid.isEmpty())
        svc.onDestroy(); a.finish()
    }
    @Test fun reopeningTheKeyboardForReviewPreservesThePhrase() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = svc.packageName; fieldId = Ui.ID_SPEAK }
        svc.startInput(info); phrase(); svc.reviewReplacements()
        svc.onStartInputView(info, true)
        assertTrue(view.reviewingReplacements); assertEquals(1, svc.replacements!!.bank.count()); assertEquals("дом стоит ", field.text)
        svc.onDestroy()
    }
    @Test fun hidingAndReopeningTheSameFieldKeepsTheBankButANewInputClearsIt() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = svc.packageName; fieldId = Ui.ID_SPEAK }
        svc.startInput(info); phrase()
        svc.onFinishInputView(false); svc.onStartInputView(info, false)
        assertEquals(1, svc.replacements!!.bank.count()); assertEquals("дом стоит ", field.text)
        svc.onStartInput(info, false)
        assertTrue(svc.replacements!!.bank.textWords().isEmpty()); svc.onDestroy()
    }
    @Test fun controlsAreLargeLabelledNeutralAndMirroredForEachHand() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        for (hand in listOf("right", "left")) {
            svc.prefs.hand = hand; phrase(); svc.reviewReplacements(); layout()
            val controls = all(view).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }
            for (b in controls) {
                val d = b.resources.displayMetrics.density
                assertTrue("${b.text}: ${b.width} x ${b.height}", b.width >= 48 * d && b.height >= 56 * d)
                assertFalse(b.contentDescription.isNullOrBlank())
                val bounds = android.graphics.Rect(0, 0, b.width, b.height); view.offsetDescendantRectToMyCoords(b, bounds)
                assertTrue("${b.text} clipped: $bounds / ${view.height}", android.graphics.Rect(0, 0, view.width, view.height).contains(bounds))
                assertTrue("${b.text} text clipped", b.layout.height + b.compoundPaddingTop + b.compoundPaddingBottom <= b.height + 2)
                val c = b.currentTextColor; assertFalse(Color.red(c) > Color.green(c) * 1.5 && Color.red(c) > Color.blue(c) * 1.5)
            }
            assertEquals(8, controls.size)
            assertEquals(hand == "left", button("Принять").left < button("Услышать").left)
            shot("replacements-$hand"); svc.endReplacements()
        }
        svc.onDestroy()
    }
    @Test fun badgeHasAccessibleActionsAndExternalEditsCannotApplyStaleRanges() {
        phrase(); val badge = button("замен: 1"); val info = badge.createAccessibilityNodeInfo()
        assertTrue(info.actionList.any { it.label == "Разобрать замены" }); assertTrue(info.actionList.any { it.label == "Оставить всё" })
        field.text = "чужой текст "; field.cursor = field.text.length
        svc.acceptReplacement(0); assertEquals("чужой текст ", field.text); assertEquals(0, svc.replacements!!.bank.count())
        svc.onDestroy()
    }
    @Test fun passwordDisablesAllReplacementStateAndCursorOrDeletionEndsThePhrase() {
        phrase(); svc.moveCursor(-1); assertEquals(0, svc.replacements!!.bank.count())
        svc.moveCursor(1); phrase(); svc.backspace(); assertEquals(0, svc.replacements!!.bank.count())
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertFalse(svc.replacements!!.bank.enabled); assertTrue(svc.replacements!!.bank.textWords().isEmpty()); assertFalse(svc.reviewReplacements())
        val before = field.text; svc.acceptReplacement(0); svc.undoReplacement(); svc.listenReplacement(0); assertEquals(before, field.text)
        svc.onDestroy()
    }
}
