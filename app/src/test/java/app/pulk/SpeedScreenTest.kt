package app.pulk

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The speed check never calls a slip a «mistake» (skill inclusive-product: no «ошибка», no red). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeedScreenTest {
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun introTextNeverSaysMistake() {
        val a = Robolectric.buildActivity(SpeedActivity::class.java).setup().get()
        val texts = all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("the intro screen is empty", texts.any { it.contains("Три короткие фразы") })
        for (t in texts) assertFalse("«$t» says a mistake", t.contains("ошибк", ignoreCase = true))
    }
}
