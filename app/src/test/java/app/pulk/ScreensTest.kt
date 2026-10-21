package app.pulk

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the app's screens, as a phone shows them (412 × 892 dp), to look at on the computer (build/shots). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    private fun shot(a: Activity, name: String) {
        val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val w = 412 * 3; val h = 892 * 3
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/shots").mkdirs()
        File("build/shots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun screens() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()).apply { lessonsDone = 1; lang = "ru"; hand = "right"; wizardDone = true }
        shot(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "screen-home")
        shot(Robolectric.buildActivity(OptionsActivity::class.java).setup().get(), "screen-settings")
        shot(Robolectric.buildActivity(OptionsActivity::class.java, android.content.Intent().putExtra(OptionsActivity.MORE, true)).setup().get(), "screen-more")
        val wiz = Robolectric.buildActivity(WizardActivity::class.java).setup().get()
        shot(wiz, "screen-wizard-hand")
        all(wiz.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Правой" }.performClick()
        shot(wiz, "screen-wizard-helps")
        val t = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        shot(t, "screen-lessons")
        all(t.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Начать" }.performClick()
        shot(t, "screen-level")
        Coach.reset()
        assertTrue(prefs.wizardDone)
    }

    @Test fun theWizardTurnsOnWhatHelps() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()).apply { mode = "pulse"; simple = false; wizardDone = false }
        val wiz = Robolectric.buildActivity(WizardActivity::class.java).setup().get()
        fun click(label: String) = all(wiz.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }.performClick()
        click("Левой"); click("Трудно смахивать"); click("Хочу попроще"); click("Дальше")
        assertTrue(prefs.left && prefs.mode == "taps" && prefs.simple)
    }
}
