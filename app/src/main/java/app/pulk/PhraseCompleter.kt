package app.pulk

/** Offline proposals only: no editor, voice, learning or retained input. Feed completed words from
 * the current sentence. The caller applies a tail and chooses punctuation separately.
 * Pass myPhrases = { prefs.myPhrases + prefs.phrases }; the provider is read afresh, only when enabled.
 */
class PhraseCompleter(
    private val words: Words,
    private val profile: Profile = Profile.SPEAK,
    var enabled: Boolean = true,
    private val myPhrases: () -> List<String> = { emptyList() },
    private val readyPhrases: List<String> = Phrases.QUICK + Phrases.SITUATIONS.flatMap { it.second } + Phrases.DAY.flatMap { it.third }
) {
    enum class Profile { WRITE, SPEAK }
    data class Completion(val tail: List<String>) { val text: String get() = tail.joinToString(" ") }
    private data class Source(val tokens: List<String>, val weight: Double)

    fun suggest(words: List<String>): Completion? {
        if (!enabled) return null
        val prefix = clean(words) ?: return null
        // One word does not identify an intent. Never use sentence-start fallback as evidence.
        if (prefix.size < MIN_CONTEXT) return null
        val sources = LinkedHashMap<List<String>, Source>()
        fun add(phrases: List<String>, weight: Double) {
            for (phrase in phrases) {
                // Strip only the final sign; never join separate sentences or discard a number.
                val tokens = clean(phrase.trim().trimEnd('.', '?', '!', '…').split(Regex("\\s+"))) ?: continue
                if (tokens.any { StopWords.bad(it) }) continue
                val key = tokens.map(::normal)
                if (key !in sources) sources[key] = Source(tokens, weight)
            }
        }
        add(myPhrases(), OWN_WEIGHT)
        add(readyPhrases, 1.0)
        var matching = sources.values.filter { starts(it.tokens, prefix) }
        val fromPhrases = matching.isNotEmpty()
        // A bigram alone ignores the earlier intent. Require an unambiguous observed left pair.
        if (!fromPhrases && next(prefix[prefix.lastIndex - 1]).map(::normal) != listOf(normal(prefix.last()))) return null
        val context = prefix.toMutableList()
        val tail = ArrayList<String>()
        val threshold = if (profile == Profile.WRITE) WRITE_THRESHOLD else SPEAK_THRESHOLD
        repeat(MAX_TAIL) {
            val candidate: String
            val confidence: Double
            if (fromPhrases) {
                val votes = LinkedHashMap<String, Double>()
                for (s in matching) {
                    val key = s.tokens.getOrNull(context.size)?.let(::normal) ?: "" // end is evidence too
                    votes[key] = (votes[key] ?: 0.0) + s.weight
                }
                val best = votes.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).first()
                if (best.key.isEmpty()) return result(tail)
                candidate = matching.first { it.tokens.getOrNull(context.size)?.let(::normal) == best.key }.tokens[context.size]
                confidence = best.value / (votes.values.sum() + EVIDENCE_FLOOR)
            } else {
                val options = next(context.last())
                if (options.size != 1) return result(tail)
                candidate = clean(options)?.singleOrNull() ?: return result(tail)
                val count = this.words.pairs[normal(context.last())]?.get(normal(candidate)) ?: 0
                val evidence = if (count >= 2) count.toDouble() else 1.0
                confidence = evidence / (evidence + EVIDENCE_FLOOR)
                // A bigram cycle is not a phrase ending.
                if (tail.any { normal(it) == normal(candidate) }) return result(tail)
            }
            if (confidence <= threshold || StopWords.bad(candidate)) return result(tail)
            tail.add(candidate); context.add(candidate)
            if (fromPhrases) matching = matching.filter { starts(it.tokens, context) }
        }
        return result(tail)
    }

    /** Ordered candidates only. Marker matching is a heuristic, not a grammatical diagnosis. */
    fun punctuationHints(words: List<String>): List<String> {
        if (!enabled) return emptyList()
        val tokens = clean(words) ?: return emptyList()
        if (tokens.isEmpty()) return emptyList()
        return if (tokens.any { normal(it) in QUESTION_MARKERS }) listOf("?", ".", "!") else listOf(".", "?", "!")
    }

    private fun next(word: String) = words.next(word, Int.MAX_VALUE, false)
    private fun result(tail: List<String>) = if (tail.isEmpty()) null else Completion(tail.toList())
    private fun normal(word: String) = Words.fold(word.lowercase())
    private fun starts(tokens: List<String>, prefix: List<String>) = tokens.size >= prefix.size && prefix.indices.all { normal(tokens[it]) == normal(prefix[it]) }
    private fun clean(tokens: List<String>): List<String>? {
        val out = tokens.map { it.trim().trim(',', ';', ':', '«', '»', '“', '”', '"', '(', ')').lowercase() }
        return out.takeIf { it.all { token -> TOKEN.matches(token) } }
    }

    companion object {
        const val MIN_CONTEXT = 2
        const val MAX_TAIL = 4
        const val OWN_WEIGHT = 2.0
        // Heuristic evidence share, NOT a calibrated probability. One unique corpus/ready continuation
        // scores .80; an explicitly saved own phrase (or a learned pair seen twice) scores .889.
        // WRITE permits the former; SPEAK needs stronger evidence. No on-device calibration.
        const val EVIDENCE_FLOOR = 0.25
        const val WRITE_THRESHOLD = 0.75
        const val SPEAK_THRESHOLD = 0.85
        private val TOKEN = Regex("\\p{L}+(?:['’-]\\p{L}+)*")
        val QUESTION_MARKERS = setOf("как", "почему", "ли", "где", "кто", "зачем", "когда", "сколько", "что", "какой", "какая", "какие",
            "how", "why", "what", "where", "who", "when", "which", "whose", "whom", "do", "does", "did", "is", "are", "am", "was", "were", "can", "could", "will", "would", "should", "have", "has")
    }
}
