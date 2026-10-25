package app.pulk

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice as TtsVoice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalVoiceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun voice(name: String, lang: String, network: Boolean, quality: Int = 300) =
        TtsVoice(name, Locale(lang), quality, 300, network, emptySet())
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
}
