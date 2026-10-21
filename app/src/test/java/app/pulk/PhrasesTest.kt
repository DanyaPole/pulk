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
}
