package app.pulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {
    @Test fun aFewLettersBringAWholePhrase() {
        assertEquals("Сколько это стоит?", Phrases.complete("скол", emptyList())[0])
        assertTrue(Phrases.complete("ск ст", emptyList()).contains("Сколько это стоит?"))   // first letters of words
        assertTrue(Phrases.complete("п", emptyList()).isEmpty())                            // one letter is too little
    }

    @Test fun ownPhrasesComeFirst() {
        val h = Phrases.remember(Phrases.remember(emptyList(), "Принеси мне чай"), "Привет, мам")
        assertEquals(listOf("Привет, мам", "Принеси мне чай"), h)
        assertEquals("Привет, мам", Phrases.complete("при", h)[0])
        assertEquals(2, Phrases.remember(h, "привет, мам").size)                            // no repeats
    }

    @Test fun theDayBringsItsPhrasesFirst() {
        assertEquals("Сейчас · Утро", Phrases.now(7).first)
        assertEquals("Сейчас · Вечер", Phrases.now(18).first)
        assertEquals("Сейчас · Ночь", Phrases.now(23).first)
        assertEquals("Сейчас · Ночь", Phrases.now(3).first)                                // before morning it is still night
        assertEquals("Не могу уснуть", Phrases.complete("не мо", emptyList())[0])         // day phrases come by first letters too
    }
}
