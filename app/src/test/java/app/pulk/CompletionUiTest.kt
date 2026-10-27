package app.pulk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompletionUiTest {
    private class Field : TextSink {
        var text = ""; var cursor = 0; val actions = ArrayList<Int>()
        override fun commit(s: String) { text = text.substring(0, cursor) + s + text.substring(cursor); cursor += s.length }
        override fun deleteBefore(n: Int) { val start = maxOf(0, cursor - n); text = text.removeRange(start, cursor); cursor = start }
        override fun before(n: Int) = text.substring(maxOf(0, cursor - n), cursor)
        override fun key(code: Int) { if (code == KeyEvent.KEYCODE_DEL) deleteBefore(1) else if (code == KeyEvent.KEYCODE_ENTER) commit("\n") }
        override fun action(a: Int) { actions.add(a) }
        override fun capsOn(inputType: Int) = false
    }
    private lateinit var svc: PulkService
    private lateinit var field: Field
    private lateinit var view: PulseView
    private val trace = ArrayList<String>()
    @Before fun setup() {
        Coach.reset(); svc = Robolectric.setupService(PulkService::class.java)
        svc.prefs.apply {
            sound = "off"; haptics = false; mode = "pulse"; lang = "ru"; hand = "right"; bigText = true
            learn = false; autocorrect = false; family = emptyList(); phrases = emptyList()
            myPhrases = listOf("мне пожалуйста воды", "где моя чашка")
        }
        svc.words("ru")
        @Suppress("UNCHECKED_CAST")
        val dicts = svc.javaClass.getDeclaredField("dicts").apply { isAccessible = true }.get(svc) as MutableMap<String, Words>
        synchronized(svc) { dicts["ru"] = Words(listOf("мне", "пожалуйста", "воды", "где", "моя", "чашка", "дом", "том", "стоит"), "ru").apply { loadNext(listOf("том\tстоит")) } }
        field = Field(); svc.sink = field; start()
        svc.feedback.trace = { trace.add(it) }
        view = svc.onCreateInputView() as PulseView; view.reset(open = true); layout()
    }
    private fun start(type: Int = InputType.TYPE_CLASS_TEXT, own: Boolean = false) = svc.startInput(EditorInfo().apply {
        inputType = type; initialSelStart = field.cursor; initialSelEnd = field.cursor; imeOptions = EditorInfo.IME_ACTION_SEND
        if (own) { packageName = svc.packageName; fieldId = Ui.ID_SPEAK }
    })
    private fun type(s: String) {
        for (c in s) if (c == ' ') { svc.space(); shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS) }
        else svc.typeLetter(c.toString(), Pulse.Slot(0, 'c', c.toString()))
    }
    private fun layout() {
        val d = view.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((320 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun button(label: String) = all(view).filterIsInstance<Button>().first { it.text.toString() == label }
    private fun click(label: String) { button(label).performClick(); layout() }
    private fun spoken() = trace.filter { it.startsWith("say ") }
    private fun shot(name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        File("build/shots").mkdirs(); File("build/shots/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }; bitmap.recycle()
    }
    @Test fun acceptAddsOnlyWordsAndUndoRestoresExactInputThenSignRequiresSeparatePress() {
        type("мне пожалуйста "); svc.enter(); layout()
        assertTrue(view.reviewingCompletion); assertEquals("мне пожалуйста ", field.text); assertTrue(field.actions.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)
        assertEquals("мне пожалуйста ", field.text); assertTrue(spoken().isEmpty())
        click("Принять"); assertEquals("мне пожалуйста воды ", field.text); assertTrue(spoken().isEmpty())
        click("Вернуть"); assertEquals("мне пожалуйста ", field.text); assertFalse(svc.completion!!.punctuation)
        click("Принять"); click("?"); assertEquals("мне пожалуйста воды? ", field.text); assertFalse(view.reviewingCompletion); assertTrue(spoken().isEmpty())
        svc.readPhrase(); assertEquals(listOf("say мне пожалуйста воды? "), spoken())
        svc.onDestroy()
    }
    @Test fun listenOnlyAfterPressKeepAndCloseAreSilentAndNextGestureCanSend() {
        type("мне пожалуйста "); svc.enter(); layout(); click("Услышать")
        assertEquals(listOf("say воды"), spoken()); assertEquals("мне пожалуйста ", field.text)
        click("Оставить"); assertEquals("мне пожалуйста ", field.text); click("Без знака")
        assertEquals("мне пожалуйста ", field.text); svc.enter(); assertEquals(listOf(EditorInfo.IME_ACTION_SEND), field.actions)
        svc.onDestroy()
    }
    @Test fun backAndHideKeepInputAndDoNotTrapSubsequentActions() {
        type("мне пожалуйста "); svc.enter(); layout()
        svc.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertNull(svc.completion); assertFalse(view.reviewingCompletion); assertEquals("мне пожалуйста ", field.text)
        type("воды "); svc.enter(); assertTrue(view.reviewingCompletion)
        svc.onFinishInputView(false); assertNull(svc.completion); svc.enter(); assertEquals(listOf(EditorInfo.IME_ACTION_SEND), field.actions)
        assertTrue(spoken().isEmpty()); svc.onDestroy()
    }
    @Test fun replacementsAreFirstAndAcceptedWordsFeedCompletion() {
        svc.prefs.mode = "petals"; svc.prefs.myPhrases = listOf("том стоит рядом")
        svc.beginPetals(); Pulse.PetalIndex(emptyList(), Pulse.table("ru", false)).key("дом")!!.forEach { svc.addPetal(it - '0') }; svc.finishPetals()
        type("стоит "); svc.enter(); layout(); assertFalse(view.reviewingCompletion)
        click("Принять"); click("К завершению"); assertTrue(view.reviewingCompletion); assertEquals("рядом", svc.completion!!.tail)
        assertEquals("том стоит ", field.text); assertTrue(spoken().isEmpty()); svc.onDestroy()
    }
    @Test fun noTailOffersSignsExistingSignsArePreservedAndOwnFieldUsesSameGate() {
        start(own = true); type("хочу "); svc.readPhrase(); layout()
        assertTrue(svc.completion!!.punctuation); assertTrue(spoken().isEmpty()); click("К набору")
        svc.punct("!"); assertFalse(svc.reviewCompletion()); assertEquals("хочу! ", field.text)
        svc.onDestroy()
    }
    @Test fun passwordCursorAndExternalEditsInvalidateCompletionAndAllStaleActions() {
        type("мне пожалуйста "); svc.enter(); layout()
        field.text = "другой текст "; field.cursor = field.text.length; svc.acceptCompletion()
        assertEquals("другой текст ", field.text); assertNull(svc.completion)
        start(); type("мне пожалуйста "); svc.enter(); layout()
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val before = field.text
        svc.acceptCompletion(); svc.undoCompletion(); svc.listenCompletion(); svc.choosePunctuation("?")
        assertFalse(svc.reviewCompletion()); assertEquals(before, field.text); assertTrue(spoken().isEmpty()); assertNull(svc.completion)
        assertTrue(svc.replacements!!.bank.textWords().isEmpty()); svc.onDestroy()
    }
    @Test fun controlsAreLargeLabelledMirroredAndExposeCustomActionsInBothSteps() {
        RuntimeEnvironment.setFontScale(1.3f)
        for (hand in listOf("right", "left")) {
            svc.prefs.hand = hand; start(); type("где моя "); svc.enter(); layout()
            for (signStep in listOf(false, true)) {
                if (signStep) click("Принять")
                val controls = all(view).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }
                for (b in controls) {
                    val d = b.resources.displayMetrics.density
                    assertTrue("${b.text}: ${b.width} x ${b.height}", b.width >= 56 * d && b.height >= 56 * d)
                    assertFalse(b.contentDescription.isNullOrBlank())
                    val bounds = Rect(0, 0, b.width, b.height); view.offsetDescendantRectToMyCoords(b, bounds)
                    assertTrue("${b.text} clipped: $bounds", Rect(0, 0, view.width, view.height).contains(bounds))
                    assertTrue("${b.text} text clipped", b.layout.height + b.compoundPaddingTop + b.compoundPaddingBottom <= b.height + 2)
                }
                val info = view.createAccessibilityNodeInfo()
                assertTrue(info.actionList.any { it.label.toString().contains(if (signStep) "вопрос" else "Принять") })
                if (signStep) {
                    assertEquals("вопрос, предложен первым", button("? · подсказка").contentDescription)
                    assertEquals(hand == "left", button("? · подсказка").left > button(".").left)
                } else assertEquals(hand == "left", button("Принять").left < button("Услышать").left)
                shot("completion-${if (signStep) "sign" else "tail"}-$hand")
            }
            svc.closeCompletion()
        }
        svc.onDestroy()
    }
    @Test fun talkBackCustomActionsAcceptSilentlyAndChooseASignInSeparateSteps() {
        type("мне пожалуйста "); svc.enter(); layout()
        val accept = view.createAccessibilityNodeInfo().actionList.first { it.label == "Принять" }
        assertTrue(view.performAccessibilityAction(accept.id, null)); layout()
        assertEquals("мне пожалуйста воды ", field.text); assertTrue(spoken().isEmpty())
        val sign = view.createAccessibilityNodeInfo().actionList.first { it.label == "восклицание" }
        assertTrue(view.performAccessibilityAction(sign.id, null)); assertEquals("мне пожалуйста воды! ", field.text)
        assertTrue(spoken().isEmpty()); svc.onDestroy()
    }
}
