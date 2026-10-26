package app.pulk

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReplacementBankTest {
    private val key = Pulse.PetalIndex(emptyList(), Pulse.table("ru", false)).key("дом")!!
    private fun bank(lines: List<String> = listOf("том\tстоит"), profile: ReplacementBank.Profile = ReplacementBank.Profile.SPEAK,
                     enabled: Boolean = true): ReplacementBank {
        val words = Words(listOf("дом", "том"), "ru").apply { loadNext(lines) }
        return ReplacementBank(words, profile, enabled)
    }
    private fun pair(b: ReplacementBank) { b.addWord("дом", key); b.addWord("стоит") }

    @Test fun queuesWithoutChangingTextAndUsesStableWordIndices() {
        val b = bank(); b.addWord("вот"); b.addWord("дом", key)
        assertEquals(0, b.count()); b.addWord("стоит")
        val change = b.pending().single()
        assertEquals(ReplacementBank.Replacement(1, "дом", "том", "вот том стоит"), change)
        assertEquals("дом → том; вот том стоит", b.listenText(1))
        assertEquals(listOf("вот", "дом", "стоит"), b.textWords())
        assertNull(b.accept(0)); assertFalse(b.keep(99)); assertNull(b.listenText(-1))
        assertEquals(listOf(change), b.pending()); assertEquals(1, b.count())
    }

    @Test fun acceptAndUndoPreserveWordsAddedLater() {
        val b = bank(); pair(b)
        assertEquals("том", b.accept(0)!!.after); assertEquals(0, b.count())
        assertEquals(listOf("том", "стоит"), b.textWords())
        b.addWord("тут"); assertTrue(b.undo())
        assertEquals(listOf("дом", "стоит", "тут"), b.textWords()); assertEquals(1, b.count())
        assertFalse(b.undo())
    }

    @Test fun keepsReplacementPairUntilPhraseEndAndCanUndoRefusal() {
        val b = bank(); pair(b); assertTrue(b.keep(0)); pair(b)
        assertEquals(0, b.count()); assertEquals(listOf("дом", "стоит", "дом", "стоит"), b.textWords())
        assertTrue(b.undo()); assertEquals(listOf(0, 2), b.pending().map { it.index })
        assertNotNull(b.accept(2)); assertNotNull(b.accept(0))
        assertTrue(b.undo()); assertEquals(listOf("дом", "стоит", "том", "стоит"), b.textWords())
        assertTrue(b.undo()); assertEquals(2, b.count())
    }

    @Test fun keepAllIsOneUndoableAction() {
        val b = bank(); pair(b); pair(b); b.keepAll()
        assertEquals(0, b.count()); b.keepAll() // an empty action must not swallow undo
        assertTrue(b.undo()); assertEquals(2, b.count()); assertFalse(b.undo())
    }

    @Test fun lockedWordsNeverChangeIncludingThroughUndo() {
        val b = bank(); pair(b); b.lock(0)
        assertEquals(0, b.count()); assertNull(b.accept(0)); b.lock(-1); assertEquals(0, b.count())
        pair(b); assertEquals(listOf(2), b.pending().map { it.index })
        assertNotNull(b.accept(2)); b.lock(2)
        assertFalse(b.undo()); assertEquals("том", b.textWords()[2])
        val future = bank(); future.lock(4); pair(future); assertEquals(0, future.count())
    }

    @Test fun passwordModeStoresNothingAndDisablingDropsUndoAndContext() {
        val b = bank(enabled = false); pair(b)
        assertEquals(-1, b.addWord("secret", key)); assertTrue(b.textWords().isEmpty())
        assertEquals(0, b.count()); assertNull(b.listenText(0)); assertNull(b.accept(0)); assertFalse(b.keep(0))
        b.keepAll(); assertFalse(b.undo()); b.lock(10)
        b.enabled = true; pair(b); assertEquals(1, b.count()); b.accept(0)
        b.enabled = false; assertTrue(b.textWords().isEmpty()); assertFalse(b.undo())
        b.enabled = true; pair(b); assertEquals(1, b.count())
    }

    @Test fun profilesOnlyChangeThresholdAndSmallEvidenceIsIgnored() {
        val rankTwo = listOf("том\tсейчас стоит")
        val write = bank(rankTwo, ReplacementBank.Profile.WRITE); pair(write); assertEquals(1, write.count())
        val speak = bank(rankTwo); pair(speak); assertEquals(0, speak.count())
        for (profile in ReplacementBank.Profile.values()) {
            val small = bank(listOf("том\tсейчас тут стоит"), profile); pair(small); assertEquals(0, small.count())
            val tie = bank(listOf("дом\tстоит", "том\tстоит"), profile); pair(tie); assertEquals(0, tie.count())
        }
    }

    @Test fun startFallbackIsNotPairEvidenceAndLeftContextWorks() {
        val w = Words(listOf("дом", "том"), "ru").apply { loadNext(listOf("^\tстоит")) }
        assertEquals(listOf("стоит"), w.next("дом")); assertTrue(w.next("дом", 3, false).isEmpty())
        val b = ReplacementBank(w); pair(b); assertEquals(0, b.count())
        val left = bank(listOf("вот\tтом")); left.addWord("вот"); left.addWord("дом", key)
        assertEquals(1, left.count()); assertEquals("вот том", left.pending().single().context)
    }

    @Test fun exactWordsAndExplicitAlternativeAreFirmAndCaseIsPreserved() {
        val b = bank(); b.addWord("дом"); b.addWord("стоит"); assertEquals(0, b.count())
        val explicit = bank(listOf("дом\tстоит")); explicit.addWord("том", key); explicit.addWord("стоит")
        assertEquals(0, explicit.count())
        val caps = bank(); caps.addWord("ДОМ", key); caps.addWord("стоит")
        assertEquals("ТОМ", caps.accept(0)!!.after); assertTrue(caps.undo()); assertEquals("ДОМ", caps.textWords()[0])
        val minor = Words(listOf("ёлка", "елка"), "ru").apply { loadNext(listOf("елка\tстоит")) }
        val m = ReplacementBank(minor); m.addWord("ёлка", Pulse.PetalIndex(emptyList(), Pulse.table("ru", false)).key("ёлка"))
        m.addWord("стоит"); assertEquals(0, m.count())
    }

    @Test fun onlyThreeBackupsAndNeverStopWords() {
        val ix = Pulse.PetalIndex(emptyList(), Pulse.table("ru", false))
        val dict = listOf("что", "сто", "стю", "зто", "што")
        assertEquals(1, dict.map { ix.key(it) }.distinct().size)
        val w = Words(dict, "ru").apply { loadNext(listOf("што\tлет")) }
        val b = ReplacementBank(w); b.addWord("что", ix.key("что")); b.addWord("лет"); assertEquals(0, b.count())
        val safe = Words(listOf("сука"), "ru").apply { user["сука"] = 100; loadNext(listOf("сука\tстоит")) }
        val s = ReplacementBank(safe); s.addWord("слово", ix.key("сука")); s.addWord("стоит"); assertEquals(0, s.count())
    }

    @Test fun phraseEndClearsWordsPendingRefusalsLocksAndUndo() {
        val b = bank(); pair(b); b.keep(0); b.lock(20); b.endPhrase()
        assertTrue(b.textWords().isEmpty()); assertEquals(0, b.count()); assertFalse(b.undo())
        pair(b); assertEquals(1, b.count()); b.accept(0); b.endPhrase(); assertFalse(b.undo())
    }

    @Test fun realCorporaInBothLanguagesAndLearnedPairs() {
        for ((lang, before, after, right) in listOf(listOf("ru", "что", "сто", "лет"), listOf("en", "you", "loo", "paper"))) {
            val w = Words(File("src/main/assets/dict_$lang.txt").readLines(), lang)
            w.loadNext(File("src/main/assets/next_$lang.txt").readLines())
            val b = ReplacementBank(w); val k = Pulse.PetalIndex(emptyList(), Pulse.table(lang, false)).key(before)
            b.addWord(before, k); b.addWord(right); assertEquals(after, b.pending().single().after)
        }
        val w = Words(listOf("дом", "том"), "ru")
        repeat(2) { w.learnPair("том", "стоит") }
        val b = ReplacementBank(w); pair(b); assertEquals(1, b.count())
        repeat(2) { w.learnPair("том", "быстро") }
        assertEquals(listOf("быстро", "стоит"), w.next("ТОМ", 3, false))
        assertEquals(0, b.count()) // equal counts have the same lexical tie-break in both versions
    }
}
