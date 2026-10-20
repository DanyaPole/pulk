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
        // a touch near the corner is in the first ring, further out in the second; the star zone is not a service key
        assertEquals(Pulse.Fn.SHIFT, Pulse.hit(5.0, 60.0)?.fn)   // top, close to the corner
        assertEquals(Pulse.Fn.HIDE, Pulse.hit(-60.0, 5.0)?.fn)   // bottom edge, close to the corner
        assertEquals(Pulse.Fn.SPACE, Pulse.hit(-90.0, 90.0)?.fn)
        assertTrue(Pulse.hit(-200.0, 200.0) == null)
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
}
