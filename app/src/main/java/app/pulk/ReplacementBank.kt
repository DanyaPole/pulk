package app.pulk

/**
 * Phrase-local replacement proposals. Feed completed words in order; pass a petal key only for
 * the automatically chosen first candidate. With no numeric petal confidence, ambiguity (at least
 * one distinct backup) marks that word as weak. Explicit choices and letter-by-letter words are firm.
 * All i arguments are stable, zero-based WORD indices, not positions in pending().
 * textWords() is a snapshot for the caller; only accept/undo change its words. No text sink or voice.
 * End/reset the phrase on a boundary or external edit; refusals and undo live until endPhrase().
 */
class ReplacementBank(private val words: Words, private val profile: Profile = Profile.SPEAK, enabled: Boolean = true) {
    enum class Profile { WRITE, SPEAK }
    data class Replacement(val index: Int, val before: String, val after: String, val context: String)
    private data class Entry(val word: String, val backups: List<String>)
    private data class Action(val index: Int = -1, val previous: Entry? = null,
                              val refusals: Set<Pair<String, String>> = emptySet())
    private val phrase = ArrayList<Entry>()
    private val refused = HashSet<Pair<String, String>>()
    private val history = ArrayList<Action>()
    private var locked = -1
    var enabled = enabled
        set(value) { if (!value) endPhrase(); field = value }

    fun addWord(word: String, petalKey: String? = null): Int {
        if (!enabled || word.isBlank()) return -1
        val candidates = petalKey?.let { words.petalCandidates(it) } ?: emptyList()
        val backups = if (candidates.firstOrNull()?.let { normal(it) == normal(word) } == true)
            candidates.filter { normal(it) != normal(word) }.distinctBy { normal(it) }
                .take(MAX_ALTERNATIVES).map { Words.shape(it, word) }
        else emptyList()
        phrase.add(Entry(word, backups))
        return phrase.lastIndex
    }

    fun textWords(): List<String> = phrase.map { it.word }

    private fun support(prev: String, word: String): Double {
        val rank = words.next(prev, Int.MAX_VALUE, false).indexOfFirst { normal(it) == normal(word) }
        return if (rank < 0) 0.0 else 1.0 / (rank + 1)
    }

    fun pending(): List<Replacement> {
        if (!enabled) return emptyList()
        val out = ArrayList<Replacement>()
        for ((i, entry) in phrase.withIndex()) {
            if (i <= locked || entry.backups.isEmpty()) continue
            val candidates = listOf(entry.word) + entry.backups
            val scores = candidates.map { c ->
                (if (i > 0) support(phrase[i - 1].word, c) else 0.0) +
                    (if (i < phrase.lastIndex) support(c, phrase[i + 1].word) else 0.0)
            }
            val best = scores.indices.maxByOrNull { scores[it] } ?: continue
            if (best == 0) continue
            val confidence = scores[best] / (EVIDENCE_FLOOR + scores.sum())
            val threshold = if (profile == Profile.WRITE) WRITE_THRESHOLD else SPEAK_THRESHOLD
            if (confidence <= threshold) continue
            val after = candidates[best]
            if ((normal(entry.word) to normal(after)) in refused) continue
            val context = (maxOf(0, i - 1)..minOf(phrase.lastIndex, i + 1))
                .joinToString(" ") { if (it == i) after else phrase[it].word }
            out.add(Replacement(i, entry.word, after, context))
        }
        return out
    }

    fun count(): Int = pending().size
    fun listenText(i: Int): String? = pending().find { it.index == i }?.let { "${it.before} → ${it.after}; ${it.context}" }

    fun accept(i: Int): Replacement? {
        val change = pending().find { it.index == i } ?: return null
        history.add(Action(i, phrase[i]))
        phrase[i] = Entry(change.after, emptyList())
        return change
    }

    fun keep(i: Int): Boolean {
        val change = pending().find { it.index == i } ?: return false
        refuse(listOf(change)); return true
    }

    fun keepAll() { refuse(pending()) }
    private fun refuse(changes: List<Replacement>) {
        val keys = changes.map { normal(it.before) to normal(it.after) }.toSet()
        if (keys.isEmpty()) return
        refused.addAll(keys); history.add(Action(refusals = keys))
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val action = history.removeAt(history.lastIndex)
        action.previous?.let { phrase[action.index] = it }
        refused.removeAll(action.refusals)
        return true
    }

    /** Inclusive and monotonic. Locking also retires undo actions that would change spoken text. */
    fun lock(upToIndex: Int) {
        if (!enabled) return
        locked = maxOf(locked, upToIndex)
        history.removeAll { it.previous != null && it.index <= locked }
    }

    fun endPhrase() { phrase.clear(); refused.clear(); history.clear(); locked = -1 }

    private fun normal(word: String) = Words.fold(word.lowercase())

    companion object {
        const val MAX_ALTERNATIVES = 3
        // Heuristic evidence share, NOT a calibrated probability: inverse pair rank, plus a .25
        // evidence floor. A lone #1 pair gives .80, #2 gives .667, #3 gives .571. Thus writing
        // permits #2; speaking needs #1 or agreement of both neighbours. Ties/minor evidence stay quiet.
        // Tune only after real usage checks. build-web.js exports these constants for the same JS logic.
        const val EVIDENCE_FLOOR = 0.25
        const val WRITE_THRESHOLD = 0.60
        const val SPEAK_THRESHOLD = 0.75
    }
}
