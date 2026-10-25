package app.pulk

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice as TtsVoice
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalVoiceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun voice(name: String, lang: String, network: Boolean, quality: Int = 300, features: Set<String> = emptySet()) =
        TtsVoice(name, Locale(lang), quality, 300, network, features)
    private fun ready(): TextToSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance().also {
        shadowOf(it).onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun savedNetworkVoiceAndNetworkDefaultAreReplacedByLocalVoice() {
        val network = voice("online", "ru", true, 500)
        val local = voice("offline", "ru", false)
        listOf(network, local).forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app).apply { voice = network.name })
        val tts = ready().apply { setVoice(network) }
        v.say("Привет")
        assertEquals(local, shadowOf(tts).currentVoice)
        assertEquals("Привет", shadowOf(tts).lastSpokenText)
        assertEquals(listOf(local.name), v.ruVoices())
        v.shutdown()
    }

    @Test fun savedLocalVoiceMustMatchTheLanguageAndIsReselectedEachTime() {
        val ru = voice("ru", "ru", false)
        val en = voice("en", "en", false)
        listOf(ru, en).forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app).apply { voice = en.name })
        val tts = ready()
        v.say("Ёж"); assertEquals(ru, shadowOf(tts).currentVoice)
        v.say("Hello"); assertEquals(en, shadowOf(tts).currentVoice)
        v.say("Снова"); assertEquals(ru, shadowOf(tts).currentVoice)
        v.shutdown()
    }

    @Test fun savedLocalVoiceIsPreferredOverHigherQualityLocalVoice() {
        val chosen = voice("chosen", "ru", false)
        listOf(chosen, voice("better", "ru", false, 500)).forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app).apply { voice = chosen.name })
        val tts = ready()
        v.say("Да")
        assertEquals(chosen, shadowOf(tts).currentVoice)
        v.shutdown()
    }

    @Test fun onlyNetworkOrWrongLanguageMeansSilenceAndKeepsThePendingPhrase() {
        ShadowTextToSpeech.addVoice(voice("online", "ru", true))
        ShadowTextToSpeech.addVoice(voice("english", "en", false))
        var notices = 0
        val v = Voice(app, Prefs(app), onTrouble = { notices++ })
        v.say("  Хочу пить  ")
        val tts = ready()
        assertNull(shadowOf(tts).lastSpokenText)
        assertEquals("Хочу пить", v.last)
        assertEquals(Voice.NO_RUSSIAN, v.trouble)
        assertTrue(notices > 0)
        v.shutdown()
    }

    @Test fun noVoicesMeansSilenceAndKeepsThePhrase() {
        val v = Voice(app, Prefs(app))
        val tts = ready()
        v.say("Да")
        assertNull(shadowOf(tts).lastSpokenText)
        assertEquals("Да", v.last)
        v.shutdown()
    }

    @Test fun keyboardFeedbackAlsoRequiresALocalVoice() {
        val network = voice("online", "ru", true)
        ShadowTextToSpeech.addVoice(network)
        val f = Feedback(app, Prefs(app))
        f.say("Привет")
        val tts = ready()
        assertNull(shadowOf(tts).lastSpokenText)
        val local = voice("offline", "ru", false)
        ShadowTextToSpeech.addVoice(local)
        tts.setVoice(network)
        f.say("Снова")
        assertEquals(local, shadowOf(tts).currentVoice)
        assertEquals("Снова", shadowOf(tts).lastSpokenText)
        val english = voice("english", "en", false)
        ShadowTextToSpeech.addVoice(english)
        f.say("Hello")
        assertEquals(english, shadowOf(tts).currentVoice)
        f.shutdown()
    }

    @Test fun notDownloadedVoicesAreNeverSelectedOrOffered() {
        val missing = voice("missing", "ru", false, 500, setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
        val local = voice("installed", "ru", false)
        listOf(missing, local).forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app).apply { voice = missing.name })
        val tts = ready()
        v.say("Да")
        assertEquals(local, shadowOf(tts).currentVoice)
        assertEquals(listOf(local.name), v.ruVoices())
        v.shutdown()
    }

    @Test fun regionalAndThreeLetterLanguagesRemainUsable() {
        for ((code, phrase) in listOf("ru_RU" to "Привет", "RUS" to "Привет", "en_GB" to "Hello", "eng" to "Hello")) {
            ShadowTextToSpeech.reset()
            val local = voice(code, code, false)
            ShadowTextToSpeech.addVoice(local)
            val v = Voice(app, Prefs(app))
            val tts = ready()
            v.say(phrase)
            assertEquals(code, local, shadowOf(tts).currentVoice)
            assertEquals(phrase, shadowOf(tts).lastSpokenText)
            if (phrase == "Привет") assertEquals(listOf(code), v.ruVoices())
            v.shutdown()
        }
    }

    @Test fun numbersAndPunctuationUseTheChosenLanguage() {
        for (lang in listOf("ru", "en")) {
            ShadowTextToSpeech.reset()
            val local = voice(lang, lang, false)
            ShadowTextToSpeech.addVoice(local)
            val v = Voice(app, Prefs(app).apply { this.lang = lang })
            val tts = ready()
            for (phrase in listOf("123", "5?", "?!")) {
                v.say(phrase)
                assertEquals(local, shadowOf(tts).currentVoice)
                assertEquals(phrase, shadowOf(tts).lastSpokenText)
                assertNull(v.trouble)
            }
            v.shutdown()
        }
    }

    @Test fun keyboardFeedbackHonoursTheChosenRussianVoice() {
        val chosen = voice("chosen", "ru", false)
        listOf(chosen, voice("better", "ru", false, 500)).forEach { ShadowTextToSpeech.addVoice(it) }
        val f = Feedback(app, Prefs(app).apply { voice = chosen.name })
        f.say("Да")
        val tts = ready()
        assertEquals(chosen, shadowOf(tts).currentVoice)
        assertEquals("Да", shadowOf(tts).lastSpokenText)
        f.shutdown()
    }

    @Test @Config(shadows = [RejectingVoiceShadow::class])
    fun rejectedVoicesAreTriedInOrderUntilOneWorks() {
        val chosen = voice("chosen", "ru", false, 100)
        val best = voice("best", "ru", false, 500)
        val working = voice("working", "ru", false, 400)
        listOf(working, best, chosen, voice("online", "ru", true, 600)).forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app))
        val tts = ready()
        RejectingVoiceShadow.attempts.clear()
        assertTrue(Voice.useLocalVoice(tts, "ru", chosen.name))
        assertEquals(listOf("chosen", "best", "working"), RejectingVoiceShadow.attempts)
        assertEquals(working, shadowOf(tts).currentVoice)
        v.shutdown()
    }

    @Test @Config(shadows = [RejectingVoiceShadow::class])
    fun allRejectedOrUndownloadedVoicesLeaveThePhraseSilent() {
        listOf(voice("chosen", "ru", false), voice("best", "ru", false),
            voice("missing", "ru", false, 600, setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))
            .forEach { ShadowTextToSpeech.addVoice(it) }
        val v = Voice(app, Prefs(app).apply { voice = "chosen" })
        val tts = ready()
        RejectingVoiceShadow.attempts.clear()
        v.say("Да")
        assertNull(shadowOf(tts).lastSpokenText)
        assertEquals("Да", v.last)
        assertEquals(Voice.NO_RUSSIAN, v.trouble)
        assertEquals(listOf("chosen", "best"), RejectingVoiceShadow.attempts)
        v.shutdown()
    }

    @Test fun returningFromSettingsRechecksTheVoiceWithoutSpeakingOrLosingTypedText() {
        val screen = Robolectric.buildActivity(SpeakActivity::class.java).setup()
        val tts = ready()
        fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
        fun notice() = views(screen.get().window.decorView).firstOrNull { it.accessibilityLiveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE }
        assertNotNull("TalkBack can read the voice notice", notice())
        screen.get().findViewById<EditText>(Ui.ID_SPEAK).setText("Хочу пить")
        screen.pause().stop()
        ShadowTextToSpeech.addVoice(voice("downloaded", "ru_RU", false))
        screen.start().resume()
        assertNull(notice())
        assertEquals("Хочу пить", screen.get().findViewById<EditText>(Ui.ID_SPEAK).text.toString())
        assertNull(shadowOf(tts).lastSpokenText)
        screen.pause().stop().destroy()
    }
}

@Implements(TextToSpeech::class)
class RejectingVoiceShadow : ShadowTextToSpeech() {
    companion object { val attempts = mutableListOf<String>() }
    @Implementation override fun setVoice(voice: TtsVoice): Int {
        attempts.add(voice.name)
        return when (voice.name) {
            "chosen" -> TextToSpeech.ERROR
            "best" -> throw IllegalArgumentException("Voice unavailable")
            else -> super.setVoice(voice)
        }
    }
}
