package app.pulk

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Пульк does not fall or lose what was typed: turning the phone, Android closing a screen, odd photos, keys held. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SturdyTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun turningThePhoneOrPluggingAKeyboardKeepsEveryScreen() {
        val acts = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities!!
        assertTrue(acts.size >= 17)
        for (a in acts) for (c in listOf(ActivityInfo.CONFIG_ORIENTATION, ActivityInfo.CONFIG_SCREEN_SIZE, ActivityInfo.CONFIG_KEYBOARD, ActivityInfo.CONFIG_KEYBOARD_HIDDEN))
            assertTrue("${a.name} is made anew", a.configChanges and c != 0)
    }

    @Test fun whatWasTypedComesBackWhenAndroidReopensAScreen() {
        Prefs(app).apply { wizardDone = true; choice = ""; aboutMe = "" }
        val speak = Robolectric.buildActivity(SpeakActivity::class.java).setup()
        speak.get().findViewById<EditText>(Ui.ID_SPEAK).setText("Мне нужно в туалет")
        speak.recreate()
        assertEquals("Мне нужно в туалет", speak.get().findViewById<EditText>(Ui.ID_SPEAK).text.toString())

        val talk = Robolectric.buildActivity(TalkActivity::class.java).setup()
        talk.get().findViewById<EditText>(Ui.ID_TALK_CHILD).setText("Да, на качели")
        talk.get().findViewById<EditText>(Ui.ID_TALK_ADULT).setText("Ты хочешь гулять?")
        talk.recreate()
        assertEquals("Да, на качели", talk.get().child.text.toString())
        assertEquals("Ты хочешь гулять?", talk.get().adult.text.toString())

        val adult = Robolectric.buildActivity(MyPhrasesActivity::class.java).setup()
        val pic = File(app.filesDir, "family/kept.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        adult.get().photo = pic.path
        adult.get().findViewById<EditText>(Ui.ID_ADULT + 2).setText("Барсик")   // the family's word, typed and not added yet
        adult.recreate()
        assertEquals(pic.path, adult.get().photo)
        assertEquals("Барсик", adult.get().findViewById<EditText>(Ui.ID_ADULT + 2).text.toString())
        pic.delete()
    }

    @Test fun anyPhotoBecomesASmallSquareOrNothing() {
        assertEquals(16, Photos.sample(8000, 6000))
        assertEquals(1, Photos.sample(300, 200))
        val dir = File(app.filesDir, "family-test")
        // a big wide picture: a 240 px square comes out
        val big = Bitmap.createBitmap(3000, 1200, Bitmap.Config.ARGB_8888)
        val src = File(app.cacheDir, "big.png").apply { outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val made = Photos.square({ src.inputStream() }, dir)
        assertNotNull(made)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(made!!.path, o)
        assertEquals(240, o.outWidth); assertEquals(240, o.outHeight)
        // not a picture, an empty file, a picture that cannot be opened: nothing, and no crash
        val junk = File(app.cacheDir, "junk.jpg").apply { writeText("это не фото") }
        assertNull(Photos.square({ junk.inputStream() }, dir))
        assertNull(Photos.square({ ByteArray(0).inputStream() }, dir))
        assertNull(Photos.square({ null }, dir))
        assertNull(Photos.load(junk.path)); assertNull(Photos.load(null)); assertNull(Photos.load("/нет/такого.jpg"))
        assertNotNull(Photos.load(made.path))
        dir.deleteRecursively()
    }

    @Test fun handingTheDeviceOnLeavesNothingBehind() {
        val p = Prefs(app).apply { wizardDone = true; aboutMe = "Меня зовут Костя"; phrases = listOf("Хочу пить"); drops = 12; hand = "left" }
        File(app.filesDir, "family").apply { mkdirs() }.resolve("1.jpg").writeBytes(byteArrayOf(1))
        File(app.filesDir, "words_ru.txt").writeText("барсик\t9")
        val a = Robolectric.buildActivity(OptionsActivity::class.java, android.content.Intent().putExtra(OptionsActivity.MORE, true)).setup().get()
        fun button() = all(a.window.decorView).filterIsInstance<android.widget.Button>().first { it.text.startsWith("Передать устройство") || it.text.startsWith("Точно стереть") }
        button().performClick()                                   // the first press only asks
        assertEquals("Меня зовут Костя", p.aboutMe)
        button().performClick()                                   // the second one cleans
        assertTrue(Clean.leftovers(app).isEmpty())
        assertTrue(!p.wizardDone && p.aboutMe.isEmpty() && p.phrases.isEmpty() && p.drops == 0 && p.hand == "right" && p.forget)
        p.forget = false
    }
    private fun all(v: android.view.View): List<android.view.View> = listOf(v) + if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun aKeyHeldAsTheFieldClosesTypesNothingLater() {
        val k = Physical.Keys()
        assertTrue(k.down(KeyEvent.KEYCODE_NUMPAD_6, false).isEmpty())   // held…
        k.reset()                                                          // …the keyboard closed
        assertTrue(k.up(KeyEvent.KEYCODE_NUMPAD_6).isEmpty())
        assertEquals(listOf(Physical.Key(-1, 's')), k.down(KeyEvent.KEYCODE_NUMPAD_0, false))   // only the space, no stale letter
    }

    @Test fun thePhonesSpeechEngineCanBeFound() {
        // since Android 11 an app sees a speech engine only if its manifest asks for TTS_SERVICE; and still no internet
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""<queries>[\s\S]*android\.intent\.action\.TTS_SERVICE[\s\S]*</queries>""").containsMatchIn(m))
        assertEquals(listOf("android.permission.VIBRATE"), Regex("uses-permission android:name=\"([^\"]+)\"").findAll(m).map { it.groupValues[1] }.toList())
    }

    @Test fun noSpeechEngineIsToldAfterTheScreenHasItsVoice() {
        val heard = mutableListOf<String>()
        val v = Voice(app, Prefs(app), onTrouble = { heard += "trouble" })
        val tts = org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance()
        org.robolectric.Shadows.shadowOf(tts).onInitListener.onInit(android.speech.tts.TextToSpeech.ERROR)   // no engine: Android answers at once
        assertTrue(heard.isEmpty())                                                                         // not yet, inside the constructor…
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf("trouble"), heard); assertEquals(Voice.NO_ENGINE, v.trouble)                   // …but right after
    }

    @Test fun theVoiceNeverThrows() {
        val v = Voice(app, Prefs(app))
        v.say("Привет"); v.say(""); assertEquals("Привет", v.last)
        v.shutdown(); v.say("После"); v.shutdown()
    }
}
