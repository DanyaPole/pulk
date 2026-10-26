package app.pulk

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
class BubbleWordsTest {
    private fun prefs() = Prefs(RuntimeEnvironment.getApplication())
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun game(): BubblesActivity {
        prefs().apply { mode = "jabs"; popped = 0; letters = ""; bubblesHelp = true; sound = "off" }
        return Robolectric.buildActivity(BubblesActivity::class.java).setup().get()
    }

    @Test fun wordsAreShortSafeAndProgressive() {
        val dict = RuntimeEnvironment.getApplication().assets.open("dict_ru.txt").bufferedReader().readLines().map { it.substringBefore('\t') }.toSet()
        val pools = (0..2).map { BubbleWords.pool(it) }
        assertTrue(pools[0].size >= 8 && pools[1].size > pools[0].size && pools[2].size > pools[1].size)
        for (lv in 0..2) for (w in pools[lv]) {
            assertTrue(w.length in 2..4)
            assertTrue(w.all { it.toString() in BubblesActivity.pool(lv) })
            assertTrue("dictionary: $w", w in dict)
        }
        val base = (0 until 1000).map { BubbleWords.next(0, emptyMap(), "", it / 1000.0) }.count { "а" in it }
        val hard = (0 until 1000).map { BubbleWords.next(0, mapOf("а" to intArrayOf(0, 9)), "", it / 1000.0) }.count { "а" in it }
        assertTrue(hard > base)
        repeat(100) { assertNotEquals("нос", BubbleWords.next(0, emptyMap(), "нос", it / 100.0)) }
    }

    @Test fun aWordSurvivesLiftsAndPopsOnlyWhenComplete() {
        val g = game(); val p = prefs(); val word = g.letter
        for ((i, ch) in word.withIndex()) {
            g.field.setText(ch.toString()) // separate commits and the space on lift must keep progress
            g.field.setText(" ")
            assertEquals(i + 1, g.words!!.done)
            assertEquals(if (i == word.lastIndex) 1 else 0, g.n)
            assertEquals("", g.field.text.toString())
        }
        g.typed("а"); assertEquals(1, g.n); assertEquals(1, p.popped)
        assertEquals(word.length, BubblesActivity.stats(p.letters).values.sumOf { it[0] })
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(0, g.words!!.done); assertNotEquals(word, g.letter)
        Coach.reset()
    }

    @Test fun helpEscalatesPerLetterAndAidedLettersAreNotHits() {
        val g = game(); val target = g.words!!.next!!
        val wrong = if (target == "а") "н" else "а"
        g.typed(wrong); assertEquals(1, g.words!!.misses); assertFalse(Coach.example); assertEquals(0, g.words!!.done)
        g.typed(wrong); assertEquals(2, g.words!!.misses); assertTrue(Coach.example)
        g.typed(wrong); assertEquals(1, g.words!!.done); assertEquals(0, g.words!!.misses); assertFalse(Coach.example)
        val st = BubblesActivity.stats(prefs().letters).getValue(target)
        assertArrayEquals(intArrayOf(0, 3), st)
        g.typed(g.words!!.next!!); assertEquals(0, g.words!!.misses)
        Coach.reset()
    }

    @Test fun refusingHelpIsRememberedAndVoiceStaysOff() {
        val g = game()
        val events = ArrayList<String>(); g.sounds.trace = { events.add(it) }
        fun button(label: String) = all(g.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }
        button("Повторить слово").performClick(); assertFalse(events.any { it.startsWith("say ") })
        prefs().sound = "voice"; button("Повторить слово").performClick(); assertTrue(events.contains("say ${g.letter}"))
        prefs().sound = "off"; prefs().bubblesHelp = false
        val controller = Robolectric.buildActivity(BubblesActivity::class.java).setup()
        val again = controller.get(); val wrong = if (again.words!!.next == "а") "н" else "а"
        repeat(4) { again.typed(wrong) }
        assertEquals(0, again.words!!.done); assertEquals(0, Coach.help); assertNull(Coach.hint); assertFalse(prefs().bubblesHelp)
        controller.pause().stop(); assertFalse(Coach.active)
        controller.start().resume(); assertTrue(Coach.active); assertTrue(Coach.jabs)
        controller.pause().stop().destroy(); assertFalse(Coach.active)
    }

    @Test fun pulseStillHasOneLetterAndNoAssistedAdvance() {
        prefs().apply { mode = "pulse"; popped = 0; letters = "" }
        val g = Robolectric.buildActivity(BubblesActivity::class.java).setup().get()
        assertNull(g.words); assertEquals(1, g.letter.length)
        repeat(3) { g.typed(if (g.letter == "а") "н" else "а") }; assertEquals(0, g.n)
        g.typed(g.letter); assertEquals(1, g.n); assertEquals(1, prefs().popped)
        Coach.reset()
    }

    @Test fun aSuccessFadesHelpAtHigherLevels() {
        prefs().apply { mode = "jabs"; popped = 15; letters = ""; bubblesHelp = true; sound = "off" }
        val g = Robolectric.buildActivity(BubblesActivity::class.java).setup().get()
        assertEquals(0, Coach.help); assertNull(Coach.hint)
        val target = g.words!!.next!!
        repeat(2) { g.typed(if (target == "а") "н" else "а") }
        assertEquals(2, Coach.help); assertNotNull(Coach.hint); assertTrue(Coach.example)
        g.typed(target)
        assertEquals(0, Coach.help); assertNull(Coach.hint); assertFalse(Coach.example)
        Coach.reset()
    }

    @Test fun aNewBubbleInBackgroundWaitsToSpeakAndKeepsCoachPaused() {
        prefs().apply { mode = "jabs"; popped = 0; letters = ""; bubblesHelp = true; sound = "off" }
        val controller = Robolectric.buildActivity(BubblesActivity::class.java).setup()
        val g = controller.get(); val events = ArrayList<String>()
        g.sounds.trace = { events.add(it) }; prefs().sound = "voice"
        g.letter.forEach { g.typed(it.toString()) }
        controller.pause().stop(); events.clear()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertFalse("no training in another field", Coach.active)
        assertFalse("no word spoken in background", events.any { it.startsWith("say ") })
        controller.start().resume()
        assertTrue(Coach.active); assertEquals(g.words!!.next, Coach.next)
        assertTrue(events.contains("say ${g.letter}"))
        controller.pause().stop().destroy()
    }

    @Test fun aWordRoundGivesOneDropAndCanRestart() {
        val g = game(); val p = prefs(); val drops = p.drops
        repeat(BubblesActivity.ROUND) {
            g.field.setText(g.letter)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        }
        assertEquals(BubblesActivity.ROUND, g.n); assertEquals(BubblesActivity.ROUND, p.popped)
        assertEquals(drops + 1, p.drops); assertFalse(Coach.active)
        g.typed("а"); assertEquals(BubblesActivity.ROUND, p.popped)
        all(g.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Выкл" }.performClick()
        assertFalse("finished round must not resume training when help changes", Coach.active)
        all(g.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Ещё раунд" }.performClick()
        assertEquals(0, g.n); assertEquals(0, g.words!!.done); assertTrue(Coach.jabs)
        Coach.reset()
    }
}
