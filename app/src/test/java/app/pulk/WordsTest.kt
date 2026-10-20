package app.pulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordsTest {
    private val ru = Words(File("src/main/assets/dict_ru.txt").readLines(), "ru")
    private val en = Words(File("src/main/assets/dict_en.txt").readLines(), "en")

    @Test fun completesFrequentWords() {
        assertEquals("привет", ru.suggest("прив")[0])
        assertTrue(ru.suggest("спас").contains("спасибо"))
        assertTrue(en.suggest("hell").contains("hello"))   // «hell» itself is a word too
        assertEquals(3, ru.suggest("п").size)
    }

    @Test fun keepsTheCase() {
        assertEquals("Привет", ru.suggest("Прив")[0])
        assertEquals("ПРИВЕТ", ru.suggest("ПРИВ")[0])
    }

    @Test fun findsWordsThroughStrokeConfusions() {
        // «м» is «н» turned: a turn read as straight gives «нама», meant «мама»
        assertEquals("мама", ru.correction("нама"))
        // neighbouring petal, same gesture: «т» and «р» are next to each other
        assertTrue(ru.correction("тесро") in setOf("тесто", "тесно"))   // both are one stroke away
        assertEquals("привет", ru.correction("пнивет"))
        assertTrue(ru.suggest("пнив").contains("привет"))
    }

    @Test fun leavesKnownWordsAlone() {
        assertNull(ru.correction("мама"))
        assertNull(ru.correction("еще"))          // ё typed as е is fine
        assertNull(ru.correction("дом"))
    }

    @Test fun learnsOwnWords() {
        val w = Words(listOf("дом", "дым"), "ru")
        w.keep("пульк")
        assertTrue(w.isKnown("пульк"))
        assertNull(w.correction("пульк"))
        assertEquals("пульк", w.suggest("пул")[0])
    }

    @Test fun isFastEnough() {
        val t0 = System.nanoTime()
        repeat(200) { ru.suggest("пр"); ru.correction("нама") }
        val ms = (System.nanoTime() - t0) / 1e6 / 200
        println("per keystroke: %.2f ms".format(ms))
        assertTrue("too slow: $ms ms", ms < 40)
    }
}
