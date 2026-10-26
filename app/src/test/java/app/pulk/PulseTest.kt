package app.pulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The same checks as in the web prototype: every letter by its gesture, then sloppy hands. */
class PulseTest {
    private val rnd = Random(21)
    private fun g() = rnd.nextGaussian()

    /** A stroke from [o] through segments (angle in degrees, length), sampled like finger events. */
    private fun stroke(o: DoubleArray, segs: List<Pair<Double, Double>>, noise: Double = 0.0, bend: Double = 0.0): DoubleArray {
        val pts = ArrayList<Double>(); pts.add(o[0]); pts.add(o[1])
        var x = o[0]; var y = o[1]
        for ((ang, len) in segs) for (s in 1..10) {
            val a = (ang + bend * (s / 10.0 - 0.5)) * PI / 180
            x += cos(a) * len / 10; y += sin(a) * len / 10
            pts.add(x + g() * noise); pts.add(y + g() * noise)
        }
        return pts.toDoubleArray()
    }

    private fun gesture(p: Int, kind: Char, noise: Double = 0.0): DoubleArray {
        val a = p * 45.0
        val o = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8)
        return when (kind) {
            'c' -> stroke(o, listOf(a to 45.0), noise)
            'l' -> stroke(o, listOf(a to 40.0, (a + 70) to 24.0), noise)
            'r' -> stroke(o, listOf(a to 40.0, (a - 70) to 24.0), noise)
            else -> stroke(o, listOf(a to 40.0, (a + 180) to 34.0), noise)
        }
    }

    @Test fun everyLetterOfBothLanguagesAndSymbols() {
        for ((lang, sym) in listOf("ru" to false, "en" to false, "ru" to true)) {
            val t = Pulse.table(lang, sym)
            for (p in 0 until 8) for ((i, k) in Pulse.KINDS.withIndex()) {
                val want = t[p][i]
                if (want.isEmpty()) continue
                val got = Pulse.resolve(t, gesture(p, k))
                assertEquals("$lang sym=$sym petal $p kind $k", want, got.v)
            }
        }
    }

    @Test fun tapsOnLabelsAndTheCentre() {
        val t = Pulse.table("ru", false)
        for (p in 0 until 8) for ((i, k) in Pulse.KINDS.withIndex()) {
            val s = Pulse.spot(p, k)
            val got = Pulse.resolve(t, doubleArrayOf(s[0], s[1]))
            assertEquals("tap $p$k", t[p][i], got.v)
        }
        assertTrue(Pulse.resolve(t, doubleArrayOf(Pulse.PH[0], Pulse.PH[1])).isSpace)
        assertTrue(Pulse.resolve(t, doubleArrayOf(-250.0, 20.0)).isSpace) // outside the star
    }

    /** A thumb can settle in another direction before leaving the start circle. */
    private fun wobblyStart(p: Int, side: Int, end: List<Pair<Double, Double>>): DoubleArray {
        val a = p * 45.0
        val wobble = (a + side * 40) * PI / 180
        return doubleArrayOf(0.0, 0.0, 11.8 * cos(wobble), 11.8 * sin(wobble)) +
            stroke(doubleArrayOf(0.0, 0.0), listOf(a to 12.0) + end).drop(2).toDoubleArray()
    }

    @Test fun startWobbleDoesNotChooseThePetalOrTurn() {
        val t = Pulse.table("ru", false)
        for (p in 0 until 8) for (side in listOf(-1, 1)) {
            val a = p * 45.0
            for (len in listOf(32.0, 45.0, 60.0)) {
                val pts = wobblyStart(p, side, listOf(a to (len - 12)))
                assertEquals("straight $p/$side/$len", Pulse.at(t, p, 'c'), Pulse.resolve(t, pts))
                assertEquals("jab $p/$side/$len", Pulse.at(t, p, 'c'), Pulse.jab(t, pts))
            }
            for (sign in listOf(-1, 1)) {
                val pts = wobblyStart(p, side, listOf(a to 28.0, (a + sign * 70) to 24.0))
                val want = Pulse.at(t, p, if (sign > 0) 'l' else 'r')
                assertEquals("turn $p/$side/$sign", want, Pulse.resolve(t, pts))
                assertEquals("turning jab $p/$side/$sign", want, Pulse.jab(t, pts))
            }
        }
    }

    @Test fun startCirclePreservesShortAndSparseStrokes() {
        val t = Pulse.table("ru", false)
        for (p in 0 until 8) {
            val a = p * 45 * PI / 180
            fun ray(r: Double) = doubleArrayOf(r * cos(a), r * sin(a))
            assertTrue(Pulse.resolve(t, doubleArrayOf(0.0, 0.0) + ray(Pulse.P_MIN - .001)).isSpace)
            assertEquals(Pulse.at(t, p, 'c'), Pulse.resolve(t, doubleArrayOf(0.0, 0.0) + ray(Pulse.P_MIN)))
            for (len in listOf(14.0, 16.0, 22.0, 29.0, 30.01)) {
                val kind = if (len < Pulse.JAB_SHORT) 'b' else 'c'
                assertEquals("short jab $p/$len", Pulse.at(t, p, kind), Pulse.jab(t, doubleArrayOf(0.0, 0.0) + ray(len)))
            }
            val back = doubleArrayOf(0.0, 0.0) + ray(24.01) + doubleArrayOf(0.0, 0.0)
            assertEquals("short return $p", Pulse.at(t, p, 'b'), Pulse.resolve(t, back))
            val j = Pulse.Jabs(0.0, 0.0)
            val pts = wobblyStart(p, 1, listOf(p * 45.0 to 20.0)) + doubleArrayOf(0.0, 0.0)
            val got = pts.toList().chunked(2).mapNotNull { j.add(it[0], it[1]) }.map { Pulse.jab(t, it) }
            assertEquals("whole jab $p", listOf(Pulse.at(t, p, 'c')), got)
        }
    }

    @Test fun startCircleUsesCalibratedAndSmoothedCoordinates() {
        val t = Pulse.table("ru", false)
        for (tol in listOf(.6, .75, 1.0, 1.6)) for (smooth in listOf(0.0, .6)) for (p in 0 until 8) {
            val steady = Pulse.Steady(-120.0, 400.0, tol, smooth)
            val pts = wobblyStart(p, -1, listOf(p * 45.0 to 48.0)).toList().chunked(2)
                .flatMapIndexed { i, q -> steady.at(-120 + q[0] * tol, 400 + q[1] * tol, i * 16L).toList() }.toDoubleArray()
            assertEquals("calibration $tol/$smooth/$p", Pulse.at(t, p, 'c'), Pulse.resolve(t, pts))
            assertEquals("calibrated jab $tol/$smooth/$p", Pulse.at(t, p, 'c'), Pulse.jab(t, pts))
        }
    }

    @Test fun blindTapAnywhereIsSpace() {
        val t = Pulse.table("ru", false)
        val s = Pulse.spot(1, 'c')
        assertTrue(Pulse.resolve(t, doubleArrayOf(s[0], s[1]), blind = true).isSpace)
    }

    private fun rate(aimSd: Double, bendMax: Double, turnMin: Double, turnMax: Double, noise: Double): Double {
        val t = Pulse.table("ru", false)
        var ok = 0; var n = 0
        repeat(40) { for (p in 0 until 8) {
            val o = doubleArrayOf(Pulse.PH[0] + g() * 40, Pulse.PH[1] + g() * 40)
            val aim = p * 45 + g() * aimSd
            val tm = turnMin + rnd.nextDouble() * (turnMax - turnMin)
            val cases = mapOf(
                'c' to stroke(o, listOf(aim to (25 + rnd.nextDouble() * 45)), noise, (rnd.nextDouble() - .5) * 2 * bendMax),
                'l' to stroke(o, listOf(aim to (30 + rnd.nextDouble() * 20), (aim + tm) to (16 + rnd.nextDouble() * 14)), noise),
                'r' to stroke(o, listOf(aim to (30 + rnd.nextDouble() * 20), (aim - tm) to (16 + rnd.nextDouble() * 14)), noise),
                'b' to stroke(o, listOf(aim to (30 + rnd.nextDouble() * 20), (aim + 180 + g() * 20) to (22 + rnd.nextDouble() * 18)), noise),
            )
            for ((k, pts) in cases) { n++; val r = Pulse.resolve(t, pts); if (r.petal == p && r.kind == k) ok++ }
        } }
        return ok.toDouble() / n
    }

    @Test fun sloppyHandsStillMostlyRight() {
        val normal = rate(8.0, 30.0, 45.0, 95.0, 1.2)
        val sloppy = rate(13.0, 50.0, 35.0, 100.0, 2.0)
        println("normal=$normal sloppy=$sloppy")
        assertTrue("normal strokes $normal", normal >= 0.95)   // web prototype: 98-100%
        assertTrue("sloppy strokes $sloppy", sloppy >= 0.80)   // web prototype: 84-90%
    }

    @Test fun serviceKeysAreFoundByPosition() {
        fun at(r: Double, deg: Double) = Pulse.hit(r * cos(deg * PI / 180), r * sin(deg * PI / 180))?.fn
        assertEquals(Pulse.Fn.SPACE, at(90.0, 120.0))     // the corner is one big space…
        assertEquals(Pulse.Fn.ENTER, at(90.0, 165.0))     // …and ⏎ along the bottom
        assertEquals(Pulse.Fn.SPACE, at(10.0, 100.0))     // even the very corner: no dead spot
        assertEquals(Pulse.Fn.BKSP, at(230.0, 100.0))     // ⌫ up the right edge
        assertEquals(Pulse.Fn.BKSP, at(290.0, 92.0))
        assertEquals(Pulse.Fn.SHIFT, at(175.0, 170.0))    // the rare keys along the bottom edge
        assertEquals(Pulse.Fn.LANG, at(210.0, 170.0))
        assertEquals(Pulse.Fn.SYM, at(250.0, 170.0))
        assertEquals(Pulse.Fn.HIDE, at(285.0, 170.0))
        assertEquals(null, at(230.0, 135.0))              // the star
        assertEquals(null, at(320.0, 100.0))              // beyond the zone: still the star's strokes
        // every letter of the star is away from the keys
        for (p in 0 until 8) for (k in Pulse.KINDS) { val s = Pulse.spot(p, k); assertEquals("$p$k", null, Pulse.hit(s[0], s[1])) }
    }

    @Test fun liftOffDoesNotTurnAStrokeIntoOutAndBack() {
        // a straight stroke towards «е» (225°), then the thumb rolls back 3 mm in the last 30 ms as it lifts
        val t = Pulse.table("ru", false)
        val o = doubleArrayOf(Pulse.PH[0] + 12, Pulse.PH[1] - 8)
        val pts = ArrayList<Double>(); val times = ArrayList<Long>()
        val a = 225 * PI / 180
        for (i in 0..5) { pts.add(o[0] + cos(a) * 6.0 * i); pts.add(o[1] + sin(a) * 6.0 * i); times.add(i * 16L) }
        for (i in 1..2) { pts.add(o[0] + cos(a) * (30.0 - 9 * i)); pts.add(o[1] + sin(a) * (30.0 - 9 * i)); times.add(80L + i * 8) }
        val raw = pts.toDoubleArray()
        assertEquals("the raw stroke looks like out-and-back", 'b', Pulse.resolve(t, raw).kind)
        assertEquals("е", Pulse.resolve(t, Pulse.trimLift(raw, times.toLongArray(), 100L)).v)
        // a real out-and-back keeps its way back
        val back = gesture(1, 'b'); val bt = LongArray(back.size / 2) { it * 16L }
        assertEquals("ю", Pulse.resolve(t, Pulse.trimLift(back, bt, bt.last() + 8)).v)
    }

    /** A whole word as one touch of jabs: each letter out from home and back, with a bit of hand noise. */
    private fun jabWord(word: String, lang: String = "ru", noise: Double = 0.0, drift: Boolean = false): String {
        val t = Pulse.table(lang, false)
        var x = Pulse.PH[0]; var y = Pulse.PH[1]
        val jabs = Pulse.Jabs(x, y); val out = StringBuilder()
        fun go(a: Double, len: Double) { for (s in 1..8) { x += cos(a * PI / 180) * len / 8 + g() * noise; y += sin(a * PI / 180) * len / 8 + g() * noise
            jabs.add(x, y)?.let { out.append(Pulse.jab(t, it).v) } } }
        for (ch in word) {
            var p = -1; var k = -1
            for (i in 0 until 8) { val j = t[i].indexOf(ch.toString()); if (j >= 0) { p = i; k = j } }
            val a = p * 45.0 + g() * noise * 4
            when (Pulse.KINDS[k]) {
                'c' -> { go(a, 42.0); go(a + 180, if (drift) 36.0 else 42.0) }
                'l' -> { go(a, 36.0); go(a + 70, 22.0); go(a + 200, 50.0) }
                'r' -> { go(a, 36.0); go(a - 70, 22.0); go(a - 200, 50.0) }
                else -> { go(a, 20.0); go(a + 180, 20.0) }
            }
        }
        jabs.finish()?.let { out.append(Pulse.jab(t, it).v) }
        return out.toString()
    }

    @Test fun jabsTypeWholeWords() {
        for (w in listOf("мама", "привет", "касса", "сон", "щука", "семья", "человек")) assertEquals(w, jabWord(w))
        assertEquals("hello", jabWord("hello", "en"))
        // a hand that drifts and shakes a little
        var ok = 0; val words = listOf("мама", "привет", "касса", "дом", "работа", "человек", "сегодня", "хорошо")
        repeat(10) { for (w in words) if (jabWord(w, noise = 0.6, drift = true) == w) ok++ }
        println("jabs sloppy words=" + ok / (10.0 * words.size))
        assertTrue(ok >= 10 * words.size * 0.8)
    }

    @Test fun personalAimMovesTheBorders() {
        val t = Pulse.table("ru", false)
        val o = doubleArrayOf(Pulse.PH[0], Pulse.PH[1])
        fun at(deg: Double) = Pulse.resolve(t, stroke(o, listOf(deg to 45.0))).v
        assertEquals("о", at(60.0))                 // textbook: 60° is nearer 45° («о») than 90° («а»)
        Pulse.bias[2] = -25.0                       // this thumb aims «up» at about 65°
        try { assertEquals("а", at(60.0)); assertEquals("а", at(90.0)) } finally { Pulse.bias.fill(0.0) }
    }

    @Test fun steadyHandFiltersTremorAndScalesLength() {
        // a long-stroke thumb (tol 1.6): a 20-unit wobble is only a tap, a real 70-unit stroke is a letter
        val s = Pulse.Steady(0.0, 0.0, 1.6, 0.0)
        val wobble = s.at(20.0, 0.0, 0L)
        assertTrue(kotlin.math.hypot(wobble[0], wobble[1]) < Pulse.P_MIN)
        // smoothing: jitter around a straight line comes out much smaller
        // the finger resting with a tremor of ±3 units at 120 Hz: what comes out barely moves
        val sm = Pulse.Steady(0.0, 0.0, 1.0, 0.6)
        var maxOff = 0.0
        for (i in 1..60) { val q = sm.at(if (i % 2 == 0) 3.0 else -3.0, if (i % 3 == 0) 3.0 else -2.0, i * 8L); if (i > 10) maxOff = maxOf(maxOff, kotlin.math.hypot(q[0], q[1])) }
        assertTrue("resting tremor $maxOff", maxOff < 2.0)          // the raw tremor reaches 4.2
        // a quick stroke (45 units in 80 ms) keeps almost all of its length
        val fast = Pulse.Steady(0.0, 0.0, 1.0, 0.6)
        var end = doubleArrayOf(0.0, 0.0)
        fast.at(0.0, 0.0, 0L)
        for (i in 1..10) end = fast.at(4.5 * i, 0.0, i * 8L)
        assertTrue("stroke kept undefined", end[0] > 30)
    }

    @Test fun twoTapsReachEveryLetter() {
        for (lang in listOf("ru", "en")) {
            val t = Pulse.table(lang, false)
            for (p in 0 until 8) for ((i, k) in Pulse.KINDS.withIndex()) {
                if (t[p][i].isEmpty()) continue
                // the second tap anywhere in the quarter, a bit off its middle
                for (off in listOf(-30.0, 0.0, 30.0)) {
                    val a = (Pulse.tapAngle(p, k) + off) * PI / 180
                    val s = Pulse.tapPick(t, p, Pulse.PH[0] + 60 * cos(a), Pulse.PH[1] + 60 * sin(a))
                    assertEquals("$lang $p $k $off", t[p][i], s?.v)
                }
            }
            assertEquals(null, Pulse.tapPick(t, 3, Pulse.PH[0] + 5, Pulse.PH[1]))   // the centre closes it
        }
    }

    @Test fun aNumberPadAndAStickAreTheStar() {
        val k = Physical.Keys(); val n8 = android.view.KeyEvent.KEYCODE_NUMPAD_8; val n9 = android.view.KeyEvent.KEYCODE_NUMPAD_9
        val n5 = android.view.KeyEvent.KEYCODE_NUMPAD_5; val n6 = android.view.KeyEvent.KEYCODE_NUMPAD_6
        assertEquals(emptyList<Physical.Key>(), k.down(n8, false)); assertEquals(listOf(Physical.Key(2, 'c')), k.up(n8))      // 8: up, the big letter
        k.down(n8, false); k.down(n5, false); assertEquals(listOf(Physical.Key(2, 'b')), k.up(n8))                         // with 5: the centre one
        k.down(n6, false); k.down(n9, false); assertEquals(listOf(Physical.Key(0, 'l')), k.up(n6))                         // 6 with 9: the turn up
        assertEquals(listOf(Physical.Key(-1, 's')), k.down(n5, false))                                                    // 5 alone: a space
        assertEquals(listOf(Physical.Key(4, 'c')), k.down(n6, true).also { k.up(n6) }.let { k.down(n6, true); k.up(n6) }) // left hand: mirrored
        // the stick: out and back up, then right — two big letters; a short push — the centre letter
        val j = Pulse.Jabs(0.0, 0.0); val got = ArrayList<String>(); val t = Pulse.table("ru", false)
        fun push(x: Float, y: Float) { for (i in 1..6) j.add(Physical.stick(x * i / 6, y * i / 6, false)[0], Physical.stick(x * i / 6, y * i / 6, false)[1])?.let { got.add(Pulse.jab(t, it).v) }
            for (i in 5 downTo 0) j.add(Physical.stick(x * i / 6, y * i / 6, false)[0], Physical.stick(x * i / 6, y * i / 6, false)[1])?.let { got.add(Pulse.jab(t, it).v) } }
        push(0f, -1f); push(1f, 0f); push(0f, -0.45f)
        assertEquals(listOf(t[2][0], t[0][0], t[2][3]), got)
    }
}
