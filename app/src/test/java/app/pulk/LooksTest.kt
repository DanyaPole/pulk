package app.pulk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The keyboard in the dark theme, on its side and for the left hand, as pictures (build/shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LooksTest {
    private fun shot(wDp: Int, name: String, set: (Prefs) -> Unit): View {
        val svc = Robolectric.setupService(PulkService::class.java)
        svc.prefs.apply { hand = "right"; look = "petals"; theme = "auto"; textWindow = false; size = 100; blind = false; mode = "pulse"; zone = "corner" }
        set(svc.prefs)
        svc.startInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        val v = svc.onCreateInputView() as PulseView
        v.measure(View.MeasureSpec.makeMeasureSpec(wDp * 3, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight); v.reset(open = true)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF15191C.toInt()); v.draw(c)
        File("build/shots").mkdirs(); File("build/shots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        return v
    }

    @Test @Config(sdk = [34], qualifiers = "w412dp-h892dp-night-xxhdpi")
    fun dark() { shot(412, "dark-petals") {}; shot(412, "dark-ripples-left") { it.look = "ripples"; it.hand = "left" } }

    @Test @Config(sdk = [34], qualifiers = "w892dp-h412dp-land-xxhdpi")
    fun onItsSide() {
        val v = shot(892, "landscape") {}
        // on its side the keyboard must still leave most of the screen to the app
        assertTrue("height ${v.height / 3} dp", v.height / 3 <= 412 * 0.8)
    }
}
