package app.pulk

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the app's screens, to look at on the computer (build/shots). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    private fun shot(a: Activity, name: String) {
        val scroll = a.window.decorView.let { all(it) }.filterIsInstance<ScrollView>().first()
        val content = scroll.getChildAt(0)
        val w = 412 * 3
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val bmp = Bitmap.createBitmap(w, minOf(content.measuredHeight, 6000), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF0D1215.toInt()); content.draw(c)
        File("build/shots").mkdirs()
        File("build/shots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun screens() {
        Prefs(org.robolectric.RuntimeEnvironment.getApplication()).apply { lessonsDone = 1; lang = "ru"; hand = "right" }
        shot(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "screen-settings")
        val t = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        shot(t, "screen-lessons")
        all(t.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Начать" }.performClick()
        shot(t, "screen-level")
        Coach.reset()
    }
}
