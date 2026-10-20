package app.pulk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The whole keyboard on the computer: the real view draws a picture and real touches type into a fake text field. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardTest {
    private class Field : TextSink {
        val sb = StringBuilder()
        var caps = true
        override fun commit(s: String) { sb.append(s) }
        override fun deleteBefore(n: Int) { sb.setLength(maxOf(0, sb.length - n)) }
        override fun before(n: Int) = sb.takeLast(n).toString()
        override fun key(code: Int) { if (code == KeyEvent.KEYCODE_DEL && sb.isNotEmpty()) sb.setLength(sb.length - 1) else if (code == KeyEvent.KEYCODE_ENTER) sb.append('\n') }
        override fun action(a: Int) { sb.append("<action$a>") }
        override fun capsOn(inputType: Int) = caps && (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0) && (sb.isEmpty() || sb.endsWith(". ") || sb.endsWith("\n"))
    }

    private lateinit var svc: PulkService
    private lateinit var view: PulseView
    private lateinit var field: Field

    @Before fun setUp() {
        svc = Robolectric.setupService(PulkService::class.java)
        svc.prefs.sound = "off"; svc.prefs.haptics = false; svc.prefs.hand = "right"; svc.prefs.lang = "ru"; svc.prefs.blind = false; svc.prefs.size = 100
        svc.prefs.suggest = true; svc.prefs.autocorrect = true; svc.prefs.learn = true
        svc.words("ru"); svc.words("en"); svc.forget()
        Coach.reset()
        field = Field(); svc.sink = field
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES })
        view = svc.onCreateInputView() as PulseView
        measure()
    }

    private fun measure() {
        val w = 412 * 3 // xxhdpi: 3 px per dp
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun picture(name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/shots"); dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- touches, in base units of the keyboard geometry ----
    private val density = 3f
    private fun cx() = view.width + 4 * density
    private fun cy() = view.height + 4 * density
    private fun scale(): Float { // px per base unit, as the view computes it
        val m = view.javaClass.getDeclaredField("k").also { it.isAccessible = true }
        return m.getFloat(view)
    }
    private fun px(bx: Double, by: Double, left: Boolean = false): FloatArray {
        val x = (cx() + bx * scale()).toFloat(); val y = (cy() - by * scale()).toFloat()
        return floatArrayOf(if (left) view.width - x else x, y)
    }

    private var t0 = 0L
    private fun ev(action: Int, p: FloatArray): MotionEvent {
        t0 += 16
        return MotionEvent.obtain(t0, t0, action, p[0], p[1], 0)
    }
    private fun touch(points: List<DoubleArray>, left: Boolean = false) {
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(points[0][0], points[0][1], left)))
        for (p in points.drop(1)) view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, px(p[0], p[1], left)))
        val last = points.last()
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, px(last[0], last[1], left)))
    }
    private fun stroke(segs: List<Pair<Double, Double>>, start: DoubleArray = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8), left: Boolean = false) {
        var x = start[0]; var y = start[1]
        val pts = arrayListOf(doubleArrayOf(x, y))
        for ((a, len) in segs) for (s in 1..6) { x += cos(a * PI / 180) * len / 6; y += sin(a * PI / 180) * len / 6; pts.add(doubleArrayOf(x, y)) }
        touch(pts, left)
    }
    private fun letter(ch: String, left: Boolean = false) {
        val t = Pulse.table(svc.lang, svc.symbols)
        for (p in 0 until 8) for ((i, k) in Pulse.KINDS.withIndex()) if (t[p][i] == ch.lowercase() || t[p][i] == ch) {
            val a = p * 45.0
            stroke(when (k) {
                'c' -> listOf(a to 45.0)
                'l' -> listOf(a to 40.0, (a + 70) to 24.0)
                'r' -> listOf(a to 40.0, (a - 70) to 24.0)
                else -> listOf(a to 40.0, (a + 180) to 34.0)
            }, left = left)
            return
        }
        error("no letter $ch")
    }
    private fun spaceTap(left: Boolean = false) = touch(listOf(doubleArrayOf(Pulse.PH[0], Pulse.PH[1])), left)
    private fun fnTap(fn: Pulse.Fn, left: Boolean = false) {
        val k = Pulse.FN_KEYS.first { it.fn == fn }
        val a = (k.a0 + k.a1) / 2 * PI / 180; val r = (k.r0 + k.r1) / 2
        touch(listOf(doubleArrayOf(r * cos(a), r * sin(a))), left)
    }

    @Test fun drawsTheKeyboard() {
        picture("right-ru")
        assertTrue(view.width > 0 && view.height > 150)
        svc.prefs.hand = "left"; view.refresh(); measure(); picture("left-ru")
        svc.prefs.hand = "right"; svc.prefs.blind = true; view.refresh(); measure(); picture("right-blind")
        svc.prefs.blind = false; view.refresh(); measure()
        svc.toggleSymbols(); picture("symbols"); svc.toggleSymbols()
        svc.switchLang(); picture("right-en"); svc.switchLang()
    }

    @Test fun typesAWordWithAutoCapital() {
        for (c in "привет") letter(c.toString())
        assertEquals("Привет", field.sb.toString())    // the first letter is capital by itself
    }

    @Test fun typesBothLanguagesAndPunctuation() {
        for (c in "тесто") letter(c.toString())
        spaceTap()
        for (c in "и") letter(c.toString())
        spaceTap()
        for (c in "сено") letter(c.toString())
        assertEquals("Тесто и сено", field.sb.toString())
        svc.switchLang()
        spaceTap(); for (c in "tea") letter(c.toString())
        assertEquals("Тесто и сено tea", field.sb.toString())
    }

    @Test fun spaceGesturesAndBackspace() {
        for (c in "дом") letter(c.toString())
        // flick out from the space key: a full stop that replaces nothing here, then auto-capital
        val k = Pulse.FN_KEYS.first { it.fn == Pulse.Fn.SPACE }
        val a = (k.a0 + k.a1) / 2 * PI / 180; val r = (k.r0 + k.r1) / 2
        val s = doubleArrayOf(r * cos(a), r * sin(a))
        touch(listOf(s, doubleArrayOf(s[0] + 12 * cos(a), s[1] + 12 * sin(a)), doubleArrayOf(s[0] + 25 * cos(a), s[1] + 25 * sin(a))))
        assertEquals("Дом. ", field.sb.toString())
        letter("т")
        assertEquals("Дом. Т", field.sb.toString())
        fnTap(Pulse.Fn.BKSP)
        assertEquals("Дом. ", field.sb.toString())
    }

    @Test fun holdingGivesTheAlternative() {
        // е held at the end of its stroke becomes ё
        val a = 5 * 45.0
        val o = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8)
        var x = o[0]; var y = o[1]
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(x, y)))
        for (i in 1..6) { x += cos(a * PI / 180) * 7; y += sin(a * PI / 180) * 7; view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, px(x, y))) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, px(x, y)))
        assertEquals("Ё", field.sb.toString())
    }

    @Test fun leftHandIsAMirrorImage() {
        svc.prefs.hand = "left"; view.refresh(); measure()
        for (c in "сон") letter(c.toString(), left = true)
        assertEquals("Сон", field.sb.toString())
    }

    @Test fun symbolsLayerForNumbers() {
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER })
        assertTrue(svc.symbols)
        letter("1"); letter("2"); letter("0")
        assertEquals("120", field.sb.toString())
    }

    @Test fun passwordFieldsAreNotReadAloud() {
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertTrue(svc.isSecure)
    }

    @Test fun blindModeTapAnywhereIsSpace() {
        svc.prefs.blind = true; view.refresh(); measure()
        for (c in "да") letter(c.toString())
        touch(listOf(doubleArrayOf(Pulse.spot(1, 'c')[0], Pulse.spot(1, 'c')[1])))   // on a letter: still a space
        assertEquals("Да ", field.sb.toString())
    }

    private fun stripTap(slot: Int) {
        val w = view.width / 3f
        val x = view.width - w * (slot + 0.5f)
        val y = PulseView.STRIP_DP * density / 2
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(x, y)))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(x, y)))
    }
    private fun spaceSwipe(dir: Double) { // along the space key: +1 up the arc, -1 down
        val k = Pulse.FN_KEYS.first { it.fn == Pulse.Fn.SPACE }
        val am = (k.a0 + k.a1) / 2; val r = (k.r0 + k.r1) / 2
        touch((0..5).map { i -> val a = (am - dir * i * 2.2) * PI / 180; doubleArrayOf(r * cos(a), r * sin(a)) })
    }

    @Test fun suggestsAndPicksFromTheStrip() {
        for (c in "прив") letter(c.toString())
        assertEquals("Привет", svc.cands[0])
        picture("strip")
        stripTap(0)
        assertEquals("Привет ", field.sb.toString())
    }

    @Test fun swipeUpTheSpaceKeyTakesTheBestWord() {
        for (c in "спаси") letter(c.toString())
        spaceSwipe(1.0)
        assertEquals("Спасибо ", field.sb.toString())
        for (c in "до") letter(c.toString())
        spaceSwipe(-1.0)   // down the arc: the word is deleted
        assertEquals("Спасибо ", field.sb.toString())
    }

    @Test fun autocorrectAndUndo() {
        for (c in "нама") letter(c.toString())   // «м» typed as straight «н»
        spaceTap()
        assertEquals("Мама ", field.sb.toString())
        fnTap(Pulse.Fn.BKSP)                       // ⌫ right after: the typed word is back
        assertEquals("Нама", field.sb.toString())
        spaceTap()
        assertEquals("Нама ", field.sb.toString()) // and is left alone from now on
    }

    @Test fun noSuggestionsInPasswords() {
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        for (c in "прив") letter(c.toString())
        assertTrue(svc.cands.isEmpty())
        spaceTap(); assertEquals("прив ", field.sb.toString())
    }

    @Test fun tutorialHandInHand() {
        Coach.active = true; Coach.next = "м"; Coach.help = 3; Coach.hint = "м: смахни вправо и поверни вверх"
        picture("coach")
        letter("н")                                 // a straight stroke right, near enough
        assertEquals("М", field.sb.toString())
        Coach.reset()
        letter("н")
        assertEquals("Мн", field.sb.toString())
    }
}
