package app.pulk

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
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
        shot(Robolectric.buildActivity(AudienceActivity::class.java).setup().get(), "screen-audience")
        val sp = Robolectric.buildActivity(SpeakActivity::class.java).setup().get()
        prefs.phrases = listOf("Принеси, пожалуйста, чай")
        shot(sp, "screen-speak")
        // ⏎ on the keyboard says the phrase and keeps it in the history
        val f = all(sp.window.decorView).filterIsInstance<android.widget.EditText>().first()
        f.setText("Я скоро буду"); f.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND)
        assertTrue(prefs.phrases.first() == "Я скоро буду" && f.text.isEmpty() && sp.lastSaid == "Я скоро буду")
        prefs.choice = Together.encode(Together.Choice("Что будешь пить?", listOf("Сок", "Вода", "Молоко")))
        prefs.family = Together.familyLines(listOf(Together.Word("Мама", ""), Together.Word("Барсик", ""), Together.Word("Качели", "")))
        prefs.aboutMe = "Меня зовут Костя. Я не говорю, но всё понимаю."
        shot(Robolectric.buildActivity(SpeakActivity::class.java).setup().get(), "screen-speak-choice")
        prefs.talk = Together.said(Together.said(emptyList(), false, "Ты хочешь гулять?"), true, "Да, на качели")
        shot(Robolectric.buildActivity(TalkActivity::class.java).setup().get(), "screen-talk")
        shot(Robolectric.buildActivity(MyPhrasesActivity::class.java).setup().get(), "screen-adult")
        shot(Robolectric.buildActivity(ParentsActivity::class.java).setup().get(), "screen-parents")
        shot(Robolectric.buildActivity(SpeakLessonActivity::class.java).setup().get().also { l -> l.step = 3; l.show() }, "screen-speak-lesson-guided")
        prefs.choice = ""; prefs.family = emptyList(); prefs.talk = emptyList(); prefs.aboutMe = ""
        Coach.reset()
        prefs.drops = 33
        shot(Robolectric.buildActivity(PondActivity::class.java).setup().get(), "screen-pond")
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
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()).apply { mode = "pulse"; simple = false; guard = 0; wizardDone = false; doors = "" }
        val wiz = Robolectric.buildActivity(WizardActivity::class.java).setup().get()
        fun click(label: String) = all(wiz.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }.performClick()
        click("Левой"); click("Подстроить под человека — 2 минуты")
        click("Да, смахивать трудно"); click("Нет"); click("Да"); click("Да"); click("Нет")   // hands, eyes, speech, calm, helper
        assertTrue(prefs.left && prefs.mode == "taps" && prefs.simple && prefs.calm && prefs.guard == 2)
        assertEquals(mapOf("hands" to 2, "eyes" to 0, "speech" to 1, "calm" to 1, "helper" to 0), Comfort.decode(prefs.doors))
        assertTrue(all(wiz.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString().startsWith("Включено: левая рука, защита от случайных касаний (сильная)") })
        shot(wiz, "screen-wizard-summary")
        val c = Robolectric.buildActivity(ComfortActivity::class.java).setup().get()
        shot(c, "screen-comfort")
        assertTrue(all(c.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Внимание" })
    }

    @Test fun startingAtOnceAsksNothingMore() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication()).apply { mode = "pulse"; simple = false; guard = 0; wizardDone = false; doors = ""; offered = false; calibrated = false }
        val wiz = Robolectric.buildActivity(WizardActivity::class.java).setup().get()
        fun click(label: String) = all(wiz.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }.performClick()
        click("Правой"); click("Начать сразу")
        assertTrue(prefs.doors == "quick" && prefs.mode == "pulse" && !prefs.simple && prefs.guard == 0)
        prefs.wizardDone = true
        val home = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(all(home.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString().startsWith("Подстроить Пульк под человека") })
    }
}
