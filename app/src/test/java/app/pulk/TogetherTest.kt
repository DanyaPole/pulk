package app.pulk

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
class TogetherTest {
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun prefs() = Prefs(org.robolectric.RuntimeEnvironment.getApplication())

    @Test fun aChoiceIsWrittenSimply() {
        assertEquals(listOf("Сок", "Вода", "Молоко"), Together.options("сок, вода; молоко, Сок"))
        val c = Together.Choice("Что будешь пить?", listOf("Сок", "Вода"))
        assertEquals(c, Together.decode(Together.encode(c)))
        assertNull(Together.decode("")); assertNull(Together.decode(Together.encode(Together.Choice("Вопрос", emptyList()))))
    }

    @Test fun theConversationKeepsTurns() {
        var t = Together.said(emptyList(), false, "Ты хочешь гулять?", 1)
        t = Together.said(t, true, "Да", 2)
        val lines = Together.talk(t)
        assertEquals(listOf(false, true), lines.map { it.me }); assertEquals("Да", lines[1].text)
        assertEquals(100, (1..150).fold(emptyList<String>()) { a, i -> Together.said(a, true, "$i") }.size)
    }

    @Test fun theAdultOffersAChoiceAndTheChildAnswers() {
        val p = prefs(); p.choice = ""; p.talk = emptyList(); p.family = Together.familyLines(listOf(Together.Word("Барсик", "")))
        val adult = Robolectric.buildActivity(MyPhrasesActivity::class.java).setup().get()
        adult.offer("Что будешь пить?", listOf("сок, вода"))
        assertEquals(listOf("Сок", "Вода"), Together.decode(p.choice)?.opts)
        assertEquals("Что будешь пить?", Together.talk(p.talk).last().text)
        val sp = Robolectric.buildActivity(SpeakActivity::class.java).setup().get()
        all(sp.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Вода" }.performClick()
        assertEquals("Вода", sp.lastSaid); assertEquals(1, p.choicePicked)
        assertTrue(Together.talk(p.talk).last().let { it.me && it.text == "Вода" })
        // the family's words have their own tab
        assertTrue(all(sp.window.decorView).filterIsInstance<Button>().any { it.text.toString() == "✓ Семья" })
        all(sp.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Готово — убрать выбор" }.performClick()
        assertEquals("", p.choice)
    }

    @Test fun answersAndUrgentAreAtHand() {
        val p = prefs(); p.choice = ""; p.family = emptyList(); p.calm = false
        val sp = Robolectric.buildActivity(SpeakActivity::class.java).setup().get()
        fun b(label: String) = all(sp.window.decorView).filterIsInstance<Button>().first { it.text.toString() == label }
        b("Подожди").performClick(); assertEquals("Подожди, я печатаю", sp.lastSaid)
        b("Срочно").performClick()
        assertTrue(all(sp.window.decorView).filterIsInstance<Button>().any { it.text.toString() == "Вызовите скорую" })
    }

    @Test fun theTalkFeedShowsBothSides() {
        val p = prefs(); p.talk = emptyList()
        val t = Robolectric.buildActivity(TalkActivity::class.java).setup().get()
        t.adult.setText("Ты хочешь гулять?"); t.adult.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND)
        t.child.setText("Да"); t.child.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEND)
        assertEquals("Да", t.lastSaid)
        val texts = all(t.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.contains("Ты хочешь гулять?") && texts.contains("Да"))
        // «Новый разговор» asks once more before it clears
        all(t.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Новый разговор" }.performClick()
        assertFalse(p.talk.isEmpty())
        all(t.window.decorView).filterIsInstance<Button>().first { it.text.toString().startsWith("Точно стереть") }.performClick()
        assertTrue(p.talk.isEmpty())
    }

    @Test fun familyWordsComeFirst() {
        val p = prefs(); p.family = Together.familyLines(listOf(Together.Word("Барсик", ""), Together.Word("Ксюша", "")))
        val fam = Together.family(p.family)
        assertEquals("Барсик", fam[0].word); assertNull(Together.photoOf(fam, "барсик"))
        assertTrue(all(Robolectric.buildActivity(ParentsActivity::class.java).setup().get().window.decorView).filterIsInstance<TextView>()
            .any { it.text.toString().contains("Предложить выбор") })
        val edit = all(Robolectric.buildActivity(MyPhrasesActivity::class.java).setup().get().window.decorView).filterIsInstance<EditText>()
        assertTrue(edit.size >= 4)
    }

    @Test fun bubblesPopAndHardLettersComeMoreOften() {
        assertTrue(BubblesActivity.pool(0).containsAll(listOf("а", "н", "т")) && BubblesActivity.pool(0).size == 8)
        assertTrue(BubblesActivity.pool(2).size > BubblesActivity.pool(1).size)
        val hard = mapOf("а" to intArrayOf(0, 9))
        val picks = (0 until 400).map { BubblesActivity.next(0, hard, "", it / 400.0) }
        assertTrue(picks.count { it == "а" } > 400 / 8 * 2)   // far more often than a fair share
        val p = prefs(); p.popped = 0; p.letters = ""; val d0 = p.drops
        val g = Robolectric.buildActivity(BubblesActivity::class.java).setup().get()
        g.typed(if (g.letter == "о") "а" else "о"); assertEquals(0, g.n)          // a miss: nothing lost, the bubble waits
        repeat(BubblesActivity.ROUND) { g.typed(g.letter); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)) }
        assertEquals(BubblesActivity.ROUND, g.n); assertEquals(d0 + 1, p.drops); assertEquals(BubblesActivity.ROUND, p.popped)
        Coach.reset()
    }
}
