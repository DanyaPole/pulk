package app.pulk

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrillsTest {
    @Test fun answersAboutTheSceneCount() {
        val water = Drills.THEMES[0].tasks[0]
        assertTrue(Drills.fits("хочу пить", water))
        assertTrue(Drills.fits("Воды!", water))
        assertFalse(Drills.fits("привет", water))
        for (t in Drills.THEMES) for (task in t.tasks) {
            assertTrue("${task.scene}: the model fits", Drills.fits(task.model, task))
            assertTrue(task.choices.contains(task.model))
        }
    }

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun aThemeFromStartToEnd() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()); prefs.drillsDone = 0
        val a = Robolectric.buildActivity(DrillsActivity::class.java).setup().get()
        all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Начать" }.performClick()   // «Просьбы»
        val f = all(a.window.decorView).filterIsInstance<EditText>().single()
        f.setText("привет"); f.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND)            // off the scene: said, a model shown
        assertEquals("привет", a.lastSaid); assertEquals(0, a.at)
        f.setText("дай воды"); f.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND); idle(1300)
        assertEquals(1, a.at)
        for (i in 1..3) { all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == Drills.THEMES[0].tasks[i].model }.performClick(); idle(1300) }
        assertEquals(1, prefs.drillsDone and 1)
    }

    @Test fun aDoubleTapIsOneAnswer() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()); prefs.drillsDone = 0
        val a = Robolectric.buildActivity(DrillsActivity::class.java).setup().get()
        all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Начать" }.performClick()
        val drops = prefs.drops
        val right = all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == Drills.THEMES[0].tasks[0].model }
        right.performClick(); right.performClick(); idle(1300)
        assertEquals(1, a.at); assertEquals(drops + 1, prefs.drops)          // one scene on, one drop
        all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == Drills.THEMES[0].tasks[1].model }.performClick()
        all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "К темам" }.performClick(); idle(3000)
        assertEquals(-1, a.theme)                                             // the menu stays: the waiting scene is dropped
    }
}
