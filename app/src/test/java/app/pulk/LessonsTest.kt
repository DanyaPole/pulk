package app.pulk

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class LessonsTest {
    @Test fun gestureWords() {
        assertEquals("смахни вправо и поверни вверх", Lessons.how("м", "ru", left = false))
        assertEquals("смахни влево и поверни вверх", Lessons.how("м", "ru", left = true))   // mirrored for the left hand
        assertEquals("смахни вверх", Lessons.how("а", "ru", false))
        assertEquals("смахни влево-вниз и задержи палец", Lessons.how("ё", "ru", false))
        assertEquals("смахни вправо-вверх и верни палец назад", Lessons.how("ю", "ru", false))
        assertEquals("коснись центра звезды", Lessons.how(" ", "ru", false))
    }

    @Test fun everyStepCanBeExplained() {
        for (lang in listOf("ru", "en")) for (left in listOf(false, true)) for (l in Lessons.LEVELS.indices) for (st in Lessons.steps(l, lang, left)) {
            val letters = st.want?.let { listOf(it) } ?: st.word!!.map { it.toString() }
            for (c in letters) assertNotNull("$lang $l $c", Lessons.how(c, lang, left, Lessons.isJabs(l)))
        }
    }

    @Test fun verdictsAreGenerous() {
        val word = Lessons.Step("", word = "привет")
        assertEquals(Lessons.Verdict.Going, Lessons.check(word, "при"))
        assertEquals(Lessons.Verdict.Done, Lessons.check(word, "Привет"))
        assertEquals(Lessons.Verdict.Done, Lessons.check(word, "пнивет"))      // one slip is still a success
        assertEquals(Lessons.Verdict.Going, Lessons.check(word, "прм"))       // not yet judged
        assertTrue(Lessons.check(word, "пакетик") is Lessons.Verdict.Off)
        val yo = Lessons.Step("", want = "ё")
        assertTrue(Lessons.check(yo, "е") is Lessons.Verdict.Off)             // here ё itself is the point
        assertEquals(Lessons.Verdict.Done, Lessons.check(yo, "Ё"))
        assertEquals("в", Lessons.next(word, "при"))
        assertEquals(null, Lessons.next(word, "прм"))
    }

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun click(a: android.app.Activity, label: String) {
        val b = all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }
        b.performClick()
    }
    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun aLevelFromStartToEnd() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication())
        prefs.lang = "ru"; prefs.hand = "right"; prefs.lessonsDone = 0; prefs.helpNo = 0; prefs.sound = "off"; prefs.blind = false
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        click(a, "Начать")                          // level 1 is the first card's button
        assertTrue(Coach.active)
        val field = all(a.window.decorView).filterIsInstance<EditText>().single()
        // two slips: help is offered, not forced
        field.setText("о"); idle(50); field.setText("о"); idle(50)
        val texts = all(a.window.decorView).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
        assertTrue(texts.contains("Подсказать, как?"))
        click(a, "Да")
        assertEquals(1, Coach.help)
        assertEquals("а", Coach.next)
        assertEquals("«а»: смахни вверх", Coach.hint)
        // the steps of level 1: а, о, е, н, space
        for (s in listOf("а", "о", "е", "н", " ")) { field.setText(s); idle(800) }
        assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "«День 1 · утро» — получилось!" })
        assertEquals(1 shl 8, prefs.lessonsDone and (1 shl 8))   // the first «Начать» is «День 1 · утро»
        assertFalse(Coach.next != null)
    }

    @Test fun theLastLevelRestoresSound() {
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication())
        prefs.sound = "off"; prefs.blind = false; prefs.lessonsDone = 7
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        val buttons = all(a.window.decorView).filterIsInstance<Button>().filter { it.text.toString() == "Начать" }
        buttons.last().performClick()             // level 4
        assertTrue(Coach.hideLabels); assertEquals("voice", prefs.sound); assertTrue(prefs.blind)
        click(a, "Меню")
        assertEquals("off", prefs.sound); assertFalse(prefs.blind); assertFalse(Coach.active)
    }

    @Test fun jabsCourse() {
        assertEquals(14, Lessons.LEVELS.size); assertEquals(14, Lessons.ABOUT.size)
        assertEquals("толкни вправо, поверни вверх и вернись", Lessons.how("м", "ru", false, jabs = true))
        val a = Lessons.steps(4, "ru", false)[0]
        assertEquals(Lessons.Verdict.Done, Lessons.check(a, "а "))      // the lifted finger adds a space
        assertEquals(Lessons.Verdict.Done, Lessons.check(Lessons.Step("", word = "сон"), "Сон "))
        assertTrue(Lessons.isBlind(7) && Lessons.isJabs(7) && !Lessons.isJabs(3))
    }

    @Test fun theMenuHasBothCourses() {
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        val texts = all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.startsWith("«Толчки»") })
        assertTrue(texts.any { it.startsWith("«Пульс»") })
        val start = all(a.window.decorView).filterIsInstance<Button>().filter { it.text.toString() == "Начать" }
        start[10].performClick()   // the first level of «Толчки» (after «Три дня» and «Пульс»)
        assertTrue(Coach.active && Coach.jabs)
        click(a, "Меню"); assertFalse(Coach.active)
    }

    @Test fun threeDaysInShortSessions() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3), (8..13).map { Lessons.dayOf(it) })
        assertTrue(Lessons.isBlind(13) && !Lessons.isJabs(9))
        for (l in 8..13) assertTrue(Lessons.steps(l, "ru", false).size in 3..6)
        val prefs = Prefs(org.robolectric.RuntimeEnvironment.getApplication())
        prefs.lessonsDone = 0; prefs.course3Start = -1
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        val texts = all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.startsWith("Три дня") })
        assertTrue(texts.any { it.startsWith("Сегодня: «День 1 · утро»") })
    }
}
