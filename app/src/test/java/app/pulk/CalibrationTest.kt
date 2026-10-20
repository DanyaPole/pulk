package app.pulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CalibrationTest {
    private fun strokes(len: Double, aimOff: (Int) -> Double) =
        Calibration.plan(1).map { p -> val a = (p * 45 + aimOff(p)) * PI / 180; Calibration.Stroke(p, cos(a) * len, sin(a) * len) }

    @Test fun shortStrokesMakeItSensitiveLongOnesCalm() {
        assertEquals(0.6, Calibration.compute(0.0, strokes(22.0) { 0.0 }).tol, 1e-9)
        assertEquals(1.0, Calibration.compute(0.0, strokes(45.0) { 0.0 }).tol, 1e-9)
        assertEquals(1.6, Calibration.compute(0.0, strokes(72.0) { 0.0 }).tol, 1e-9)
    }

    @Test fun aimIsLearnedAndBounded() {
        val r = Calibration.compute(0.0, strokes(45.0) { p -> if (p == 2) -14.0 else if (p == 6) 40.0 else 0.0 })
        assertEquals(-14.0, r.bias[2], 0.01)
        assertEquals(Calibration.MAX_BIAS, r.bias[6], 0.01)   // never so far that directions swap
        assertEquals(0.0, r.bias[0], 0.01)
    }

    @Test fun tremorPicksTheFilter() {
        val still = Calibration.tremorMm(List(50) { 100.0 + (it % 2) * 0.5 }, List(50) { 200.0 })
        val shaky = Calibration.tremorMm(List(50) { 100.0 + (it % 2) * 14.0 }, List(50) { 200.0 + (it % 3) * 10.0 })
        assertEquals(0.0, Calibration.smoothFor(still), 1e-9)
        assertTrue(Calibration.smoothFor(shaky) >= 0.55)
        assertEquals(3, Calibration.describe(Calibration.compute(shaky, strokes(45.0) { 0.0 })).size)
    }

    @Test fun planAsksEachDirectionTwice() {
        val p = Calibration.plan(7)
        assertEquals(16, p.size)
        for (d in 0 until 8) assertEquals(2, p.count { it == d })
    }
}
