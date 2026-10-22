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
    /** A text field with a cursor, like the app's. */
    private class Field : TextSink {
        val sb = StringBuilder()
        var cur = 0
        var reads = 0   // calls that on a phone go to the app being typed in
        var caps = true
        override fun commit(s: String) { sb.insert(cur, s); cur += s.length }
        override fun deleteBefore(n: Int) { val k = minOf(n, cur); sb.delete(cur - k, cur); cur -= k }
        override fun before(n: Int): String { reads++; return sb.substring(maxOf(0, cur - n), cur) }
        override fun after(n: Int): String { reads++; return sb.substring(cur, minOf(sb.length, cur + n)) }
        override fun key(code: Int) { if (code == KeyEvent.KEYCODE_DEL) deleteBefore(1) else if (code == KeyEvent.KEYCODE_ENTER) commit("\n") }
        override fun action(a: Int) { commit("<action$a>") }
        override fun moveCursor(delta: Int) { cur = (cur + delta).coerceIn(0, sb.length) }
        override fun capsOn(inputType: Int) = caps && (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0) &&
            before(2).let { it.isEmpty() && cur == 0 || it.endsWith(". ") || it.endsWith("\n") }
    }

    private lateinit var svc: PulkService
    private lateinit var view: PulseView
    private lateinit var field: Field

    @Before fun setUp() {
        svc = Robolectric.setupService(PulkService::class.java)
        svc.prefs.sound = "off"; svc.prefs.haptics = false; svc.prefs.hand = "right"; svc.prefs.lang = "ru"; svc.prefs.blind = false; svc.prefs.size = 100
        svc.prefs.mode = "pulse"; svc.prefs.zone = "corner"; svc.prefs.textWindow = true; svc.prefs.fieldX = -1f; svc.prefs.fieldUp = -1f
        svc.prefs.suggest = true; svc.prefs.autocorrect = true; svc.prefs.learn = true
        svc.words("ru"); svc.words("en"); svc.forget()
        Coach.reset()
        field = Field(); svc.sink = field
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES })
        view = svc.onCreateInputView() as PulseView
        measure()
        view.reset(open = true)
    }

    private fun measure() {
        val w = 412 * 3 // xxhdpi: 3 px per dp
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun picture(name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // a pretend app behind the keyboard: stripes of a chat, to see what shows through
        val p = android.graphics.Paint()
        c.drawColor(0xFFEDE7DA.toInt())
        for (i in 0 until view.height / 90) { p.color = if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFD9F2C9.toInt(); val l = if (i % 2 == 0) 30f else view.width * 0.35f; c.drawRoundRect(l, i * 90f + 12, l + view.width * 0.6f, i * 90f + 78, 24f, 24f, p) }
        view.draw(c)
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
        svc.prefs.theme = "contrast"; picture("contrast"); svc.prefs.theme = "auto"
        svc.prefs.look = "ripples"; picture("ripples")
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(Pulse.PH[0] + 20, Pulse.PH[1] + 10))); picture("ripples-touch")
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_CANCEL, px(Pulse.PH[0] + 20, Pulse.PH[1] + 10))); svc.prefs.look = "petals"
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

    @Test fun twoSpacesInAPasswordStayTwoSpaces() {
        for (c in "да") letter(c.toString()); spaceTap(); spaceTap()   // ordinary text: two quick spaces end the sentence
        assertEquals("Да. ", field.sb.toString())
        field.sb.clear(); field.cur = 0
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        for (c in "да") letter(c.toString()); spaceTap(); spaceTap()
        assertEquals("да  ", field.sb.toString())   // a password gets exactly what was typed
    }

    @Test fun aFullStopByGestureInAnAddressHasNoSpace() {
        for (c in "да") letter(c.toString()); svc.punct("."); letter("н")
        assertEquals("Да. Н", field.sb.toString())   // ordinary text: the end of a sentence
        field.sb.clear(); field.cur = 0
        for (t in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or t })
            for (c in "да") letter(c.toString()); svc.punct("."); letter("н"); svc.punct(",")
            assertEquals("да.н,", field.sb.toString())   // an address or a password: just the character
            field.sb.clear(); field.cur = 0
        }
    }

    @Test fun aHeldBackspaceStopsWhenTheKeyboardHides() {
        for (c in "мама") letter(c.toString())
        val k = Pulse.FN_KEYS.first { it.fn == Pulse.Fn.BKSP }
        val a = (k.a0 + k.a1) / 2 * PI / 180; val r = (k.r0 + k.r1) / 2
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(r * cos(a), r * sin(a))))   // ⌫ held…
        val left = field.sb.toString()
        view.reset(open = false)                                                         // …and the keyboard went away
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        assertEquals(left, field.sb.toString())   // nothing more is deleted
    }

    @Test fun blindModeTapAnywhereIsSpace() {
        svc.prefs.blind = true; view.refresh(); measure()
        for (c in "да") letter(c.toString())
        touch(listOf(doubleArrayOf(Pulse.spot(1, 'c')[0], Pulse.spot(1, 'c')[1])))   // on a letter: still a space
        assertEquals("Да ", field.sb.toString())
    }

    private fun stripTap(slot: Int) {
        val r = view.chipRects()[slot]
        val x = r.centerX(); val y = r.centerY()
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

    @Test fun seeThroughAndSlidingOut() {
        val reg = android.graphics.Region()
        view.touchRegion(reg)
        assertTrue("the star is the keyboard", reg.contains(px(Pulse.PH[0], Pulse.PH[1])[0].toInt(), px(Pulse.PH[0], Pulse.PH[1])[1].toInt()))
        assertTrue("the far top corner is the app", !reg.contains(5, 5))
        assertTrue(view.contentTop() > 0)
        fnTap(Pulse.Fn.HIDE)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        assertTrue(!view.isOpen); assertEquals(view.height, view.contentTop())
        picture("hidden")
        view.touchRegion(reg)
        assertTrue("only the handle is touchable", !reg.contains(px(Pulse.PH[0], Pulse.PH[1])[0].toInt(), px(Pulse.PH[0], Pulse.PH[1])[1].toInt()))
        fnTap(Pulse.Fn.SHIFT)     // the handle sits where the inner ring is: a touch there brings the keyboard out
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        assertTrue(view.isOpen)
        assertEquals("", field.sb.toString())
    }

    private fun idle(ms: Long) = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms))

    /** The points of a word typed as jabs from [start]: out to each letter and back home. */
    private fun jabPoints(word: String, start: DoubleArray = doubleArrayOf(Pulse.PH[0], Pulse.PH[1])): List<DoubleArray> {
        val t = Pulse.table(svc.lang, false)
        var x = start[0]; var y = start[1]
        val pts = arrayListOf(doubleArrayOf(x, y))
        fun go(a: Double, len: Double) { for (s in 1..6) { x += cos(a * PI / 180) * len / 6; y += sin(a * PI / 180) * len / 6; pts.add(doubleArrayOf(x, y)) } }
        for (ch in word.lowercase()) {
            var p = -1; var k = -1
            for (i in 0 until 8) { val j = t[i].indexOf(ch.toString()); if (j >= 0) { p = i; k = j } }
            val a = p * 45.0
            when (Pulse.KINDS[k]) {
                'c' -> { go(a, 42.0); go(a + 180, 42.0) }
                'l' -> { go(a, 36.0); go(a + 70, 22.0); go(a + 200, 50.0) }
                'r' -> { go(a, 36.0); go(a - 70, 22.0); go(a - 200, 50.0) }
                else -> { go(a, 20.0); go(a + 180, 20.0) }
            }
        }
        return pts
    }

    @Test fun jabsTypeAWordPerTouch() {
        svc.prefs.mode = "jabs"
        touch(jabPoints("мама"))
        assertEquals("Мама ", field.sb.toString())       // lifting the finger is the space
        touch(jabPoints("касса"))                        // a double letter: two jabs the same way
        assertEquals("Мама касса ", field.sb.toString())
        touch(listOf(doubleArrayOf(Pulse.PH[0], Pulse.PH[1])))   // a tap right after: two spaces make a full stop
        assertEquals("Мама касса. ", field.sb.toString())
    }

    @Test fun jabsHoldingStillNeverErases() {
        // thinking with the finger resting at home must not touch what is typed
        svc.prefs.mode = "jabs"
        val pts = jabPoints("дым")
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(pts[0][0], pts[0][1])))
        for (p in pts.drop(1)) view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, px(p[0], p[1])))
        idle(3000)
        assertEquals("Дым", field.sb.toString())
        val last = pts.last()
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, px(last[0], last[1])))
        assertEquals("Дым ", field.sb.toString())
        fnTap(Pulse.Fn.BKSP); fnTap(Pulse.Fn.BKSP)          // ⌫ after the lift: the space, then the letter
        assertEquals("Ды", field.sb.toString())
    }

    @Test fun wholeScreenStarComesToTheFinger() {
        svc.prefs.zone = "screen"; view.refresh(); measure()
        assertTrue("taller than the corner keyboard", view.height > 412 * 3)
        val reg = android.graphics.Region(); view.touchRegion(reg)
        assertTrue("all of it is the keyboard", reg.contains(10, 10))
        picture("screen-idle")
        // a stroke up from far away from the star still gives «а»; a tap anywhere is a space
        stroke(listOf(90.0 to 45.0), start = doubleArrayOf(-150.0, 520.0))
        spaceTap()
        touch(listOf(doubleArrayOf(-200.0, 450.0)))
        assertEquals("А. ", field.sb.toString())
        // with «Толчки» too
        svc.prefs.mode = "jabs"
        touch(jabPoints("сон", start = doubleArrayOf(-120.0, 600.0)))
        assertEquals("А. Сон ", field.sb.toString())
    }

    @Test fun textWindowMovesTheCursorAndItself() {
        for (c in "дом") letter(c.toString())
        picture("text-window")
        val r = view.fieldRect()!!
        // touch the text at its very start: the cursor goes before «Д», and typing goes there
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(r.left + 31 * density, r.centerY())))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(r.left + 31 * density, r.centerY())))
        assertEquals(0, field.cur)
        letter("т")
        assertEquals("ТДом", field.sb.toString())   // at the very start a capital, as in any field
        // the dots on the side drag the window, and it stays where it was left
        val g = floatArrayOf(r.right - 10 * density, r.centerY())   // right hand: the grip is on the far side
        val gx = if (svc.prefs.left) r.right - 10 * density else r.left + 8 * density
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(gx, g[1])))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, floatArrayOf(gx - 150, g[1] + 160)))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(gx - 150, g[1] + 160)))
        val r2 = view.fieldRect()!!
        assertTrue("moved", r2.top > r.top + 100 && kotlin.math.abs(r2.left - r.left) > 100)
        assertEquals("ТДом", field.sb.toString())   // at the very start a capital, as in any field
    }

    @Test fun holdSpaceAndSlideMovesTheCursor() {
        for (c in "дом") letter(c.toString())
        val k = Pulse.FN_KEYS.first { it.fn == Pulse.Fn.SPACE }
        val am = (k.a0 + k.a1) / 2 * PI / 180; val r = (k.r0 + k.r1) / 2
        val p = px(r * cos(am), r * sin(am))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, p))
        idle(500)                                              // held still: the cursor mode
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, floatArrayOf(p[0] - 9 * density * 2.5f, p[1])))
        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(p[0] - 9 * density * 2.5f, p[1])))
        assertEquals(1, field.cur)                             // two letters back, and no space typed
        assertEquals("Дом", field.sb.toString())
        fnTap(Pulse.Fn.SPACE)                                  // a plain tap is still a space
        assertEquals("Д ом", field.sb.toString())
    }

    @Test fun nextWordBeforeALetterIsTyped() {
        assertTrue("a start of a message has suggestions", svc.cands.isNotEmpty())
        for (c in "доброе") letter(c.toString())
        spaceTap()
        assertEquals("утро", svc.cands[0])                    // from everyday sentences
        stripTap(0)
        assertEquals("Доброе утро ", field.sb.toString())
        // its own habits win: after «пишу» this person always types «тебе»… after two times it is first
        repeat(2) { for (c in "пишу") letter(c.toString()); spaceTap(); for (c in "тебе") letter(c.toString()); spaceTap() }
        for (c in "пишу") letter(c.toString()); spaceTap()
        assertEquals("тебе", svc.cands[0])
    }

    @Test fun talkBackHoverStrokesAndSpeaks() {
        val am = svc.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        org.robolectric.Shadows.shadowOf(am).setEnabled(true)
        org.robolectric.Shadows.shadowOf(am).setTouchExplorationEnabled(true)
        val said = ArrayList<String>(); svc.feedback.announcer = { said.add(it) }
        try {
            // one finger under TalkBack comes as hover: a stroke up, the letter is heard before the finger lifts
            val o = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8)
            fun hv(action: Int, b: DoubleArray) { val p = px(b[0], b[1]); t0 += 16; view.onHoverEvent(MotionEvent.obtain(t0, t0, action, p[0], p[1], 0)) }
            hv(MotionEvent.ACTION_HOVER_ENTER, o)
            for (i in 1..6) hv(MotionEvent.ACTION_HOVER_MOVE, doubleArrayOf(o[0], o[1] + 7.5 * i))
            idle(300)
            assertTrue("heard before lifting: $said", said.contains("а"))
            hv(MotionEvent.ACTION_HOVER_EXIT, doubleArrayOf(o[0], o[1] + 45))
            assertEquals("А", field.sb.toString())
            // its double-tap arrives as a touch: it must not type again
            letter("о")
            assertEquals("А", field.sb.toString())
        } finally { org.robolectric.Shadows.shadowOf(am).setTouchExplorationEnabled(false) }
    }

    @Test fun compassNotesDoNotChangeWhatIsTyped() {
        svc.prefs.compass = "notes"
        for (c in "мама") letter(c.toString())
        assertEquals("Мама", field.sb.toString())
        svc.prefs.compass = "off"
    }

    @Test fun fewReadsFromTheAppPerLetter() {
        for (c in "привет") letter(c.toString()); spaceTap()
        field.reads = 0
        for (c in "как дела") if (c == ' ') spaceTap() else letter(c.toString())
        val perKey = field.reads / 8.0
        println("reads per keystroke=$perKey")
        assertTrue("reads per keystroke $perKey", perKey <= 3.0)
    }

    @Test fun passwordsWithNotesAndClicksGiveNoDirectionAway() {
        svc.prefs.haptics = true; svc.prefs.sound = "clicks"; svc.prefs.compass = "notes"
        val log = ArrayList<String>(); svc.feedback.trace = { log.add(it) }
        fun traceOf(ch: String): List<String> { log.clear(); letter(ch); idle(400); return log.toList() }
        try {
            // an ordinary field: the compass ticks and plays the direction's note, the letter has its own note
            val plain = traceOf("м")
            assertTrue("compass works as before: $plain", plain.contains("tick") && plain.count { it.startsWith("tone") } >= 2)
            assertTrue(traceOf("а") != plain)
            svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
            val a = traceOf("а")
            for (ch in listOf("м", "ь", "ю", "т", "щ")) assertEquals("«$ch» must sound and feel like «а»", a, traceOf(ch))
            // the compass is silent and still: no note, no tick (how often it ticks would show where the finger went)
            assertEquals(listOf("buzz 14", "tone 600.0/0.0/0.03 @0.18"), a.filter { it != "buzz 6" })
        } finally { svc.feedback.trace = null; svc.prefs.compass = "off"; svc.prefs.sound = "off"; svc.prefs.haptics = false }
    }

    @Test fun passwordsSoundAndFeelTheSameForEveryLetter() {
        svc.prefs.haptics = true; svc.prefs.sound = "voice"; svc.prefs.compass = "voice"
        val log = ArrayList<String>(); svc.feedback.trace = { log.add(it) }
        fun traceOf(ch: String): List<String> { log.clear(); letter(ch); idle(400); return log.toList() }
        try {
            // an ordinary field: letters are told apart by ear and by hand (otherwise this test would prove nothing)
            assertTrue(traceOf("а") != traceOf("м"))
            svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
            // a password: a straight stroke, a turn left, a turn right, out and back — all the same
            val a = traceOf("а")
            for (ch in listOf("м", "ь", "ю", "т", "щ")) assertEquals("«$ch» must sound and feel like «а»", a, traceOf(ch))
            assertTrue("compass silent, nothing spoken: $a", a.none { it == "tick" || it.startsWith("say") })
            assertTrue("there is a click", a.any { it.startsWith("tone") } && a.any { it.startsWith("buzz 14") })
            // with TalkBack: no letter of a password is announced
            val am = svc.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            org.robolectric.Shadows.shadowOf(am).setEnabled(true); org.robolectric.Shadows.shadowOf(am).setTouchExplorationEnabled(true)
            val said = ArrayList<String>(); svc.feedback.announcer = { said.add(it) }
            val o = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8)
            fun hv(action: Int, b: DoubleArray) { val p = px(b[0], b[1]); t0 += 16; view.onHoverEvent(MotionEvent.obtain(t0, t0, action, p[0], p[1], 0)) }
            log.clear()
            hv(MotionEvent.ACTION_HOVER_ENTER, o)
            for (i in 1..6) hv(MotionEvent.ACTION_HOVER_MOVE, doubleArrayOf(o[0], o[1] + 7.5 * i))
            idle(400)
            hv(MotionEvent.ACTION_HOVER_EXIT, doubleArrayOf(o[0], o[1] + 45))
            idle(400)
            assertTrue("nothing announced: $said", said.isEmpty())
            assertEquals(a, log.toList())
            org.robolectric.Shadows.shadowOf(am).setTouchExplorationEnabled(false)
        } finally { svc.feedback.trace = null; svc.prefs.compass = "off"; svc.prefs.sound = "off"; svc.prefs.haptics = false }
    }

    @Test fun twoTapsTypeWithoutStrokes() {
        svc.prefs.mode = "taps"
        fun tapAt(bx: Double, by: Double) = touch(listOf(doubleArrayOf(bx, by), doubleArrayOf(bx + 2, by + 1), doubleArrayOf(bx + 1, by - 1)))   // a tap with a little jitter
        fun tapPetal(p: Int) { val s = Pulse.spot(p, 'c'); tapAt(s[0], s[1]) }
        fun tapQuarter(p: Int, k: Char) { val a = Pulse.tapAngle(p, k) * PI / 180; tapAt(Pulse.PH[0] + 55 * cos(a), Pulse.PH[1] + 55 * sin(a)) }
        tapPetal(3)                                   // a petal open as another field is touched: it closes there
        svc.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }, false)
        assertEquals(-1, view.openPetal)
        tapPetal(0)                                   // «н м ь щ» open
        assertEquals(0, view.openPetal)
        picture("taps-open")
        tapQuarter(0, 'l')
        assertEquals("М", field.sb.toString())
        assertEquals(-1, view.openPetal)
        tapPetal(2); tapQuarter(2, 'c')              // «а»
        tapPetal(1); tapAt(Pulse.PH[0] + 3, Pulse.PH[1])   // opened, then the centre closes it: nothing typed
        tapAt(Pulse.PH[0], Pulse.PH[1])              // the centre with nothing open: a space
        assertEquals("Ма ", field.sb.toString())
        letter("о")                                   // and strokes still work as in «Пульс»
        assertEquals("Ма о", field.sb.toString())
    }

    @Test fun backspaceRightAfterTakesBackWhatTheKeyboardDid() {
        for (c in "прив") letter(c.toString()); stripTap(0)          // took «Привет »
        fnTap(Pulse.Fn.BKSP)
        assertEquals("Прив", field.sb.toString())                    // the suggestion is taken back
        spaceTap(); for (c in "дом") letter(c.toString()); spaceTap()
        spaceSwipe(-1.0)                                               // the word swiped away…
        assertEquals("Прив ", field.sb.toString())
        fnTap(Pulse.Fn.BKSP)
        assertEquals("Прив дом ", field.sb.toString())                // …comes back
        fnTap(Pulse.Fn.BKSP)
        assertEquals("Прив дом", field.sb.toString())                 // and then ⌫ is ⌫ again
    }

    @Test fun simpleModeKeepsOnlyTheBasics() {
        svc.prefs.simple = true; svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES })
        try {
            picture("simple")
            for (c in "прив") letter(c.toString())
            assertEquals(1, svc.cands.size)                       // one suggestion
            spaceSwipe(-1.0)                                       // no hidden gestures: just a space
            assertEquals("Прив ", field.sb.toString())
            assertTrue("no next word", svc.cands.isEmpty())
            for (c in "нама") letter(c.toString()); spaceTap()
            assertEquals("Прив нама ", field.sb.toString())       // no autocorrection
            fnTap(Pulse.Fn.SHIFT)                                  // the corner where ⇧ was: nothing happens
            assertTrue(!svc.shiftOnce)
        } finally { svc.prefs.simple = false }
    }

    @Test fun extraGesturesOnlyWhenSwitchedOn() {
        svc.prefs.gestures = emptySet()
        try {
            for (c in "дом") letter(c.toString())
            spaceSwipe(-1.0)                                   // not on yet: the space key is just a space
            assertEquals("Дом ", field.sb.toString())
            svc.prefs.gestures = setOf("space")
            for (c in "кот") letter(c.toString())
            spaceSwipe(-1.0)                                   // on: the word goes
            assertEquals("Дом ", field.sb.toString())
        } finally { svc.prefs.gestures = Prefs.GESTURES }
    }

    @Test fun oneButtonScanning() {
        svc.prefs.mode = "scan"; svc.prefs.scanMs = 1000
        try {
            val ms = 1000L
            picture("scan-groups")
            view.invalidate(); view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)))   // drawing starts the scan
            fun press() { view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, px(Pulse.PH[0], Pulse.PH[1]))); view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, px(Pulse.PH[0], Pulse.PH[1]))) }
            press()                                   // the first group is lit: the petal «о у б ю»
            assertEquals(1, view.scanPetal)
            picture("scan-letters")
            press()                                   // its first letter is lit: «о»
            assertEquals("О", field.sb.toString())
            idle(ms * 2 + 100)                        // two steps on: the petal «а в я ф»
            press()
            assertEquals(2, view.scanPetal)
            idle(ms + 100)                            // one step: «в»
            press()
            assertEquals("Ов", field.sb.toString())
            idle(ms * 3 + 100); press()               // groups: о, е, а, пробел → a space
            assertEquals("Ов ", field.sb.toString())
            svc.onKeyDown(KeyEvent.KEYCODE_SPACE, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))   // an outside switch works the same
            view.hide(); val was = field.sb.toString()
            view.scanPress()                          // folded: the press only brings the keyboard back
            assertEquals(was, field.sb.toString()); assertTrue(view.isOpen)
        } finally { svc.prefs.mode = "pulse" }
    }

    @Test fun familiarRowsWithABridgeToTheStar() {
        svc.prefs.mode = "classic"; view.refresh(); measure()
        try {
            fun key(ch: Char) {   // find the key on the drawn rows and tap it
                for (yy in view.gridTop() until view.height step 20) for (xx in 0 until view.width step 10)
                    if (view.classicKey(xx.toFloat(), yy.toFloat()) == ch) {
                        view.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(xx + 5f, yy + 5f)))
                        view.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(xx + 5f, yy + 5f))); return
                    }
                error("no key $ch")
            }
            key('м'); key('а'); key('м'); key('а'); key('S')
            assertEquals("Мама ", field.sb.toString())
            picture("classic")
            key('<')
            assertEquals("Мама", field.sb.toString())
            assertTrue(view.bridge != null)
            svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
            view.reset(open = true); key('м')
            assertTrue(view.bridge == null || view.bridge!!.v != "м")   // in a password the letter is not shown big above the keys
        } finally { svc.prefs.mode = "pulse" }
    }
}
