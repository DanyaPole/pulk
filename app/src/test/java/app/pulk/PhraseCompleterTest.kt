package app.pulk

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhraseCompleterTest {
    private fun words(lines: List<String> = emptyList()) = Words(emptyList(), "ru").apply { loadNext(lines) }
    private fun completer(own: List<String> = emptyList(), ready: List<String> = emptyList(),
                          profile: PhraseCompleter.Profile = PhraseCompleter.Profile.SPEAK) =
        PhraseCompleter(words(), profile, myPhrases = { own }, readyPhrases = ready)
    private val prefix = listOf("мне", "пожалуйста")

    @Test fun ownPhraseIsOnlyAProposalAndHasNoSign() {
        val input = mutableListOf("Мне,", "пожалуйста,")
        val c = completer(listOf("Мне, пожалуйста, стакан воды?!…"))
        val result = c.suggest(input)!!
        assertEquals(listOf("стакан", "воды"), result.tail); assertEquals("стакан воды", result.text)
        assertEquals(listOf("Мне,", "пожалуйста,"), input)
        assertEquals(result, c.suggest(input))
    }

    @Test fun littleOrAmbiguousDataStaysQuiet() {
        val c = completer(listOf("мне пожалуйста воды", "мне пожалуйста чаю"))
        assertNull(c.suggest(prefix)); assertNull(c.suggest(listOf("мне"))); assertNull(c.suggest(emptyList()))
        assertNull(completer().suggest(prefix))
        assertNull(completer(listOf("мне пожалуйста воды")).suggest(listOf("пожалуйста", "воды")))
    }

    @Test fun sharedTailStopsAtDoubtAndAtFourWords() {
        val c = completer(listOf("мне пожалуйста стакан воды", "мне пожалуйста стакан чая"))
        assertEquals(listOf("стакан"), c.suggest(prefix)!!.tail)
        assertNull(completer(listOf("мне пожалуйста один два три четыре пять")).suggest(prefix))
        val longPrefix = prefix + listOf("один", "два", "три")
        assertEquals(listOf("четыре", "пять", "шесть", "семь"), completer(listOf("мне пожалуйста один два три четыре пять шесть семь восемь")).suggest(longPrefix)!!.tail)
    }

    @Test fun phraseEndCountsAsEvidenceAndNeverFallsBackToPairs() {
        assertNull(completer(listOf("мне пожалуйста", "мне пожалуйста воды")).suggest(prefix))
        val c = PhraseCompleter(words(listOf("пожалуйста\tводы", "воды\tещё")), myPhrases = { listOf("мне пожалуйста воды") }, readyPhrases = emptyList())
        assertEquals(listOf("воды"), c.suggest(prefix)!!.tail)
        assertNull(c.suggest(prefix + "воды"))
    }

    @Test fun profileThresholdAndDeduplication() {
        val ready = listOf("мне пожалуйста стакан воды")
        val input = prefix + "стакан"
        assertNotNull(completer(ready = ready, profile = PhraseCompleter.Profile.WRITE).suggest(input))
        assertNull(completer(ready = ready).suggest(input))
        assertNull(completer(ready = ready + ready).suggest(input))
        assertNotNull(completer(ready, ready).suggest(input))
    }

    @Test fun readyPrefixNeedsThreeWordsAndAtLeastHalfRoundedUp() {
        val phrase = "один два три четыре пять шесть семь"
        val c = completer(ready = listOf(phrase), profile = PhraseCompleter.Profile.WRITE)
        assertNull(c.suggest(listOf("один", "два", "три")))
        assertEquals("пять шесть семь", c.suggest(listOf("один", "два", "три", "четыре"))!!.text)
        val even = completer(ready = listOf("один два три четыре пять шесть"), profile = PhraseCompleter.Profile.WRITE)
        assertNull(even.suggest(listOf("один", "два")))
        assertEquals("четыре пять шесть", even.suggest(listOf("один", "два", "три"))!!.text)
    }

    @Test fun ownPrefixIsSofterButStillNeedsHalfAndTwoWords() {
        for (profile in PhraseCompleter.Profile.values()) {
            val phrase = listOf("мне пожалуйста стакан воды")
            assertEquals("стакан воды", completer(own = phrase, profile = profile).suggest(prefix)!!.text)
            assertNull(completer(ready = phrase, profile = profile).suggest(prefix))
            assertNull(completer(own = phrase, profile = profile).suggest(listOf("мне")))
            assertNull(completer(own = listOf("мне пожалуйста принеси стакан воды"), profile = profile).suggest(prefix))
            // An own duplicate keeps its own threshold instead of being counted twice.
            assertEquals("стакан воды", completer(phrase, phrase, profile).suggest(prefix)!!.text)
        }
    }

    @Test fun everyMatchingSourceMustPassLengthWithoutFallingBackToChains() {
        val short = "один два три четыре пять шесть"
        val long = "$short семь"
        val input = listOf("один", "два", "три")
        for (profile in PhraseCompleter.Profile.values()) {
            assertNull(completer(listOf(short), listOf(long), profile).suggest(input))
            assertNull(completer(listOf(long), listOf(short), profile).suggest(input))
        }
    }

    @Test fun aWeightedMajorityCannotHideABranchOrPhraseEnd() {
        val endings = listOf("воды", "чая", "сока", "молока", "кефира", "какао", "компота", "морса")
        for (profile in PhraseCompleter.Profile.values()) {
            val majority = endings.map { "мне пожалуйста стакан $it" }
            assertNull(completer(majority + "мне пожалуйста кружку кофе", profile = profile).suggest(prefix))
            val withEnd = endings.map { "мне пожалуйста принеси стакан воды $it" } + "мне пожалуйста принеси стакан"
            val input = prefix + listOf("принеси", "стакан")
            assertEquals("воды", completer(own = withEnd.dropLast(1), profile = profile).suggest(input)!!.text)
            assertNull(completer(own = withEnd, profile = profile).suggest(input))
            assertEquals("стакан", completer(own = withEnd, profile = profile).suggest(prefix + "принеси")!!.text)
        }
    }

    @Test fun passwordsDoNotReadInputOrSourcesAndKeepNoPreviousRequest() {
        var reads = 0
        val c = PhraseCompleter(words(), enabled = false, myPhrases = { reads++; error("private source") })
        val privateInput = object : AbstractList<String>() {
            override val size: Int get() = error("password inspected")
            override fun get(index: Int): String = error("password inspected")
        }
        assertNull(c.suggest(privateInput)); assertEquals(emptyList<String>(), c.punctuationHints(privateInput)); assertEquals(0, reads)
        val active = completer(listOf("мне пожалуйста воды")); assertNotNull(active.suggest(prefix))
        active.enabled = false; assertNull(active.suggest(prefix)); assertEquals(emptyList<String>(), active.punctuationHints(prefix))
        active.enabled = true; assertNull(active.suggest(listOf("другой", "текст")))
    }

    @Test fun punctuationIsSeparateAndMarkersAreWholeWords() {
        val c = completer()
        for (marker in PhraseCompleter.QUESTION_MARKERS) assertEquals("?", c.punctuationHints(listOf(marker.uppercase(), "ты")).first())
        assertEquals(listOf(".", "?", "!"), c.punctuationHints(listOf("whoever", "dish")))
        assertEquals(emptyList<String>(), c.punctuationHints(emptyList()))
        assertEquals(emptyList<String>(), c.punctuationHints(listOf("как", "дела?")))
    }

    @Test fun newPrefixesStayQuietInBothProfilesEvenWithUniquePairs() {
        val w = words(listOf("^\tснова", "мы\tпочти", "почти\tуже", "уже\tдома", "дома\tвечером ночью"))
        for (profile in PhraseCompleter.Profile.values()) {
            // Even an internal match in a known phrase is not its beginning.
            val c = PhraseCompleter(w, profile, myPhrases = { listOf("вот мы почти уже дома") }, readyPhrases = listOf("сегодня мы почти уже дома"))
            for (input in listOf(listOf("мы", "почти"), listOf("не", "почти"), listOf("совсем", "неизвестно")))
                assertNull(c.suggest(input))
        }
    }

    @Test fun repeatedLearnedPairsDoNotAuthorizeANewPhrase() {
        val w = words(listOf("мы\tпочти"))
        w.learnPair("почти", "дома")
        for (count in 1..3) {
            for (profile in PhraseCompleter.Profile.values())
                assertNull(PhraseCompleter(w, profile, readyPhrases = emptyList()).suggest(listOf("мы", "почти")))
            w.learnPair("почти", "дома")
        }
    }

    @Test fun cyclesAndPairPunctuationCannotExtendKnownPhrases() {
        val w = words(listOf("мы\tпочти", "почти\tдома", "дома\tпочти"))
        for (profile in PhraseCompleter.Profile.values()) {
            val c = PhraseCompleter(w, profile, myPhrases = { listOf("мы почти дома!") }, readyPhrases = emptyList())
            assertEquals(listOf("дома"), c.suggest(listOf("мы", "почти"))!!.tail)
            assertNull(c.suggest(listOf("мы", "почти", "дома")))
            assertNull(PhraseCompleter(w, profile, readyPhrases = emptyList()).suggest(listOf("мы", "почти")))
            w.loadNext(listOf("дома\tготово!"))
            assertEquals(listOf("дома"), c.suggest(listOf("мы", "почти"))!!.tail)
        }
    }

    @Test fun unsafeSourcesAndSentenceBoundariesAreNotRewritten() {
        for (phrase in listOf("мне пожалуйста воды. Потом чаю", "мне пожалуйста 2 таблетки", "мне пожалуйста сука"))
            assertNull(completer(listOf(phrase)).suggest(prefix))
        assertNull(completer(listOf("мне пожалуйста воды. Потом чаю")).suggest(prefix + "воды"))
        val c = completer(listOf("мне пожалуйста воды"))
        assertNull(c.suggest(listOf("мне.", "пожалуйста"))); assertNull(c.suggest(listOf("", "пожалуйста")))
    }

    @Test fun ownProviderIsFreshAndYoAndEnglishWork() {
        var own = listOf("у ёлки тихо", "could you help me?")
        val c = PhraseCompleter(words(), myPhrases = { own }, readyPhrases = emptyList())
        assertEquals("тихо", c.suggest(listOf("У", "елки"))!!.text)
        assertEquals("help me", c.suggest(listOf("Could", "you"))!!.text)
        own = emptyList(); assertNull(c.suggest(listOf("could", "you")))
    }

    @Test fun defaultReadyPhrasesAndRealCorporaWork() {
        val assets = File("src/main/assets").takeIf { it.isDirectory } ?: File("app/src/main/assets")
        for (lang in listOf("ru", "en")) {
            val w = Words(emptyList(), lang).apply { loadNext(File(assets, "next_$lang.txt").readLines()) }
            val c = PhraseCompleter(w, PhraseCompleter.Profile.WRITE)
            assertNull(c.suggest(listOf("zzunknown", "zzunknown")))
            if (lang == "ru") {
                assertNull(c.suggest(listOf("подожди", "я")))
                assertEquals("туалет", c.suggest(listOf("мне", "нужно", "в"))!!.text)
            }
        }
    }
}
