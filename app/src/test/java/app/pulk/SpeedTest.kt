package app.pulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTest {
    @Test fun countsSpeedAccuracyAndErases() {
        val runs = listOf(
            Speed.Run("я уже еду", "я уже еду", 6000, 0),            // 9 signs in 6 s
            Speed.Run("скоро буду дома", "скоро буду дама", 9000, 2), // one slip
        )
        val r = Speed.summary(runs, "jabs")
        assertEquals((24 * 60000.0 / 15000).toInt(), r.cpm)
        assertEquals(95, r.accuracy)                                  // 1 error in 24
        assertEquals(2, r.erases)
        val back = Speed.parse(r.line())!!
        assertEquals(r, back)
        assertTrue(Speed.report(listOf(r.line())).contains("Толчки"))
        assertEquals(3, Speed.pick(3, 1).toSet().size)
    }
}
