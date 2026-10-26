package app.pulk

import kotlin.math.ln

/**
 * Word suggestions and autocorrection for «Пульс».
 * A word is matched letter by letter; a letter may differ from the typed one only the way «Пульс»
 * strokes get confused: the same petal with another gesture (a turn read as straight), or the
 * neighbouring petal with the same gesture (aim off by a direction). Frequency decides the order.
 * Pure Kotlin so it is tested on the computer.
 */
class Words(dict: List<String>, lang: String) {
    private val words: Array<String> = dict.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !StopWords.bad(it) }.toTypedArray()
    private val petals by lazy { Pulse.PetalIndex(words.toList(), Pulse.table(lang, false)) }
    fun petalCandidates(key: String): List<String> = petals.candidates(key, user).filterNot { StopWords.bad(it) }
    private val folded: Array<String> = Array(words.size) { fold(words[it]) }
    private val rank = HashMap<String, Int>(words.size * 2).also { m -> folded.forEachIndexed { i, w -> m.putIfAbsent(w, i) } }
    /** Frequency prior of each word, computed once: rarer words cost more (Zipf). */
    private val prior = DoubleArray(words.size) { ln(it + 20.0) }
    /** Words by their first letter, so a search looks only where the typed first letter can lead. */
    private val byFirst: Map<Char, IntArray> = folded.indices.groupBy { folded[it][0] }.mapValues { it.value.toIntArray() }

    /** What usually comes next: from a corpus of everyday sentences (Tatoeba), "^" for the start of a sentence. */
    private val nextCorpus = HashMap<String, List<String>>()
    /** What comes next for this person: learned from what they type. */
    val pairs = HashMap<String, HashMap<String, Int>>()

    fun loadNext(lines: List<String>) {
        for (l in lines) { val t = l.indexOf('\t'); if (t > 0) nextCorpus[l.substring(0, t)] = l.substring(t + 1).split(' ').filter { it.isNotEmpty() } }
    }

    /** The next word, before a letter of it is typed: this person's own habits first, then the corpus. */
    fun next(prev: String, n: Int = 3): List<String> {
        val p = fold(prev.lowercase())
        val out = ArrayList<String>()
        pairs[p]?.entries?.filter { it.value >= 2 }?.sortedByDescending { it.value }?.forEach { if (it.key !in out) out.add(it.key) }
        (nextCorpus[p] ?: nextCorpus[prev.lowercase()])?.forEach { if (it !in out) out.add(it) }
        if (out.isEmpty()) nextCorpus["^"]?.let { out.addAll(it) }
        return out.filterNot { StopWords.bad(it) }.take(n)
    }

    fun learnPair(prev: String, w: String) {
        val a = fold(prev.lowercase()); val b = fold(w.lowercase())
        if (b.length < 1 || b.any { !it.isLetter() && it != '\'' && it != '-' }) return
        val m = pairs.getOrPut(a) { HashMap() }; m[b] = (m[b] ?: 0) + 1
    }

    /** Words this person typed, with how often. Known to suggestions and never "corrected". */
    val user = HashMap<String, Int>()

    /** Letter → petal * 4 + gesture, from the «Пульс» table (-1: not on the star). An array: no boxing on the hot path. */
    private val pos = IntArray(0x500) { -1 }.also { m ->
        val t = Pulse.table(lang, false)
        for (p in 0 until 8) for (k in 0 until 4) t[p][k].singleOrNull()?.let { if (it.code < m.size) m[it.code] = p * 4 + k }
        m['ё'.code] = m['е'.code]; m['ъ'.code] = m['ь'.code]
    }
    private fun posOf(c: Char) = if (c.code < pos.size) pos[c.code] else -1

    /** The first letters a word may start with when [c] was typed: [c] itself, and with a miss allowed, its neighbours. */
    private fun firsts(c: Char, allow: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        byFirst[c]?.let { out.add(it) }
        if (allow > 0) for ((f, idx) in byFirst) if (f != c && near(c, f)) out.add(idx)
        return out
    }

    data class Cand(val word: String, val score: Double, val misses: Int)

    fun isKnown(w: String): Boolean { val f = fold(w.lowercase()); return rank.containsKey(f) || (user[f] ?: 0) >= 2 || StopWords.bad(f) }

    /** Up to [n] words for the letters typed so far, best first. */
    fun suggest(typed: String, n: Int = 3): List<String> {
        if (typed.isEmpty()) return emptyList()
        val t = fold(typed.lowercase())
        val best = ArrayList<Cand>()
        fun offer(c: Cand) {
            if (best.any { it.word == c.word }) return
            best.add(c); best.sortBy { it.score }; if (best.size > n) best.removeAt(best.size - 1)
        }
        val allow = allowance(t.length)
        for (idx in firsts(t[0], allow)) for (i in idx) { val sc = score(t, folded[i], allow, prior[i]); if (!sc.isNaN()) offer(Cand(words[i], sc, miss)) }
        for ((w, c) in user) if (c >= 1 && !StopWords.bad(w)) { val sc = score(t, w, allow, ln(20.0 + 2000.0 / c)); if (!sc.isNaN()) offer(Cand(w, sc, miss)) }
        return best.map { shape(it.word, typed) }
    }

    /** The word to put instead of a finished unknown word, or null to leave it as typed. */
    fun correction(typed: String): String? {
        if (typed.length < 2 || isKnown(typed)) return null
        val t = fold(typed.lowercase())
        val allow = allowance(t.length)
        var best: Cand? = null
        fun consider(w: String, prior: Double, original: String) {
            if (w.length != t.length) return
            val sc = score(t, w, allow, prior)
            if (sc.isNaN() || miss == 0) return
            if (best == null || sc < best!!.score) best = Cand(original, sc, miss)
        }
        for (idx in firsts(t[0], allow)) for (i in idx) consider(folded[i], prior[i], words[i])
        for ((w, c) in user) if (c >= 2 && !StopWords.bad(w)) consider(w, ln(20.0 + 2000.0 / c), w)
        return best?.let { shape(it.word, typed) }
    }

    fun learn(w: String) {
        val f = fold(w.lowercase())
        if (f.length < 2 || f.any { !it.isLetter() && it != '\'' && it != '-' }) return
        user[f] = (user[f] ?: 0) + 1
    }

    /** Typing a word and then refusing its correction means it is a real word for this person. */
    fun keep(w: String) { val f = fold(w.lowercase()); user[f] = maxOf(2, (user[f] ?: 0) + 1) }

    /** Misses of the last [score] (kept here so scoring allocates nothing). */
    private var miss = 0

    /** Score of [w] for the typed letters [t] (lower is better), or NaN if it can't be what was meant. */
    private fun score(t: String, w: String, allow: Int, prior: Double): Double {
        if (w.length < t.length) return Double.NaN
        var m = 0
        for (j in t.indices) {
            val a = t[j]; val b = w[j]
            if (a == b) continue
            if (!near(a, b) || ++m > allow) return Double.NaN
        }
        miss = m
        val rest = w.length - t.length
        return prior + m * 3.0 + rest * 0.12 + (if (rest == 0) -1.0 else 0.0)
    }

    private fun near(a: Char, b: Char): Boolean {
        val pa = posOf(a); val pb = posOf(b)
        if (pa < 0 || pb < 0) return false
        if (pa / 4 == pb / 4) return true
        val d = (pa / 4 - pb / 4 + 8) % 8
        return pa % 4 == pb % 4 && (d == 1 || d == 7)
    }

    private fun allowance(len: Int) = when { len <= 2 -> 0; len <= 5 -> 1; else -> 2 }

    companion object {
        fun fold(s: String) = s.replace('ё', 'е')

        /** Keeps the case the person typed: «Прив» → «Привет», «ПРИВ» → «ПРИВЕТ». */
        fun shape(w: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> w.replaceFirstChar { it.uppercase() }
            else -> w
        }
    }
}
