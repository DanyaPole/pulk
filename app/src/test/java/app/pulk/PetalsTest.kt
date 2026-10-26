package app.pulk

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PetalsTest {
    @Test fun directionsNotKindsAndSharedNormalisation() {
        val ru = Pulse.PetalIndex(listOf("дом", "том", "дам"), Pulse.table("ru", false))
        assertEquals(listOf("дом", "том"), ru.candidates(ru.key("дом")!!))
        assertEquals(ru.key("еь"), ru.key("ЁЪ"))
        assertNull(ru.key("hello")); assertNull(ru.key(""))
        assertTrue(ru.candidates("08").isEmpty())
        val en = Pulse.PetalIndex(listOf("don't", "well-known"), Pulse.table("en", false))
        assertNotNull(en.key("don't")); assertNotNull(en.key("well-known")); assertNull(en.key("ё"))
        for (p in 0..7) for (r in listOf(15.0, 22.0, 60.0)) {
            val a = p * Math.PI / 4
            assertEquals(p, Pulse.jabPetal(doubleArrayOf(0.0, 0.0, kotlin.math.cos(a) * r, kotlin.math.sin(a) * r, 0.0, 0.0)))
        }
    }

    @Test fun reusesUserWordsAndNeverOffersStopWords() {
        val w = Words(listOf("дом", "том", "сука", "fuck"), "ru")
        val ix = Pulse.PetalIndex(emptyList(), Pulse.table("ru", false))
        w.user["том"] = 5
        assertEquals(listOf("том", "дом"), w.petalCandidates(ix.key("дом")!!))
        w.user["сука"] = 100
        assertFalse(w.petalCandidates(ix.key("сука")!!).contains("сука"))
        assertFalse(w.suggest("сука").contains("сука"))
        w.user.clear()
        assertEquals("дом", w.petalCandidates(ix.key("дом")!!)[0])
    }

    @Test fun dictionaryCoverageAndLookupCost() {
        for (lang in listOf("ru", "en")) {
            val dict = File("src/main/assets/dict_$lang.txt").readLines().filter { it.isNotBlank() }
            val ix = Pulse.PetalIndex(dict, Pulse.table(lang, false))
            val cold = System.nanoTime(); ix.candidates("0")
            val buildMs = (System.nanoTime() - cold) / 1e6
            var total = 0.0; var first = 0.0; var top3 = 0.0
            for ((i, w) in dict.withIndex()) {
                val weight = 1.0 / (i + 1); total += weight
                val key = ix.key(w) ?: continue
                val got = ix.candidates(key)
                if (got.firstOrNull() == w) first += weight
                if (w in got.take(3)) top3 += weight
            }
            val words = Words(dict, lang)
            dict.take(3000).forEach { words.user[it] = 2 }
            val keys = dict.take(1000).mapNotNull { ix.key(it) }
            keys.forEach { words.petalCandidates(it) } // JIT and the lazy index
            val times = keys.map { k -> val t = System.nanoTime(); words.petalCandidates(k); (System.nanoTime() - t) / 1e6 }.sorted()
            val p95 = times[times.size * 95 / 100]
            println("petals $lang first=${first / total} top3=${top3 / total} buildMs=$buildMs lookupP95Ms=$p95 (JVM desktop, 3000 user words)")
            assertTrue("$lang first", first / total >= .88)
            assertTrue("$lang top3", top3 / total >= .99)
            assertTrue("$lang lookup p95=$p95", p95 < 5)
        }
    }
}
