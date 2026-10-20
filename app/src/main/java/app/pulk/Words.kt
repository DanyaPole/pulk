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
    private val words: Array<String> = dict.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toTypedArray()
    private val folded: Array<String> = Array(words.size) { fold(words[it]) }
    private val rank = HashMap<String, Int>(words.size * 2).also { m -> folded.forEachIndexed { i, w -> m.putIfAbsent(w, i) } }

    /** Words this person typed, with how often. Known to suggestions and never "corrected". */
    val user = HashMap<String, Int>()

    /** Letter → petal * 4 + gesture, from the «Пульс» table. */
    private val pos = HashMap<Char, Int>().also { m ->
        val t = Pulse.table(lang, false)
        for (p in 0 until 8) for (k in 0 until 4) t[p][k].singleOrNull()?.let { m[it] = p * 4 + k }
        m['ё'] = m['е'] ?: 0; m['ъ'] = m['ь'] ?: 0
    }

    data class Cand(val word: String, val score: Double, val misses: Int)

    fun isKnown(w: String): Boolean { val f = fold(w.lowercase()); return rank.containsKey(f) || (user[f] ?: 0) >= 2 }

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
        for (i in words.indices) score(t, folded[i], allow, ln(i + 20.0))?.let { offer(Cand(words[i], it.first, it.second)) }
        for ((w, c) in user) if (c >= 1) score(t, w, allow, ln(20.0 + 2000.0 / c))?.let { offer(Cand(w, it.first, it.second)) }
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
            val s = score(t, w, allow, prior) ?: return
            if (s.second == 0) return
            if (best == null || s.first < best!!.score) best = Cand(original, s.first, s.second)
        }
        for (i in words.indices) consider(folded[i], ln(i + 20.0), words[i])
        for ((w, c) in user) if (c >= 2) consider(w, ln(20.0 + 2000.0 / c), w)
        return best?.let { shape(it.word, typed) }
    }

    fun learn(w: String) {
        val f = fold(w.lowercase())
        if (f.length < 2 || f.any { !it.isLetter() && it != '\'' && it != '-' }) return
        user[f] = (user[f] ?: 0) + 1
    }

    /** Typing a word and then refusing its correction means it is a real word for this person. */
    fun keep(w: String) { val f = fold(w.lowercase()); user[f] = maxOf(2, (user[f] ?: 0) + 1) }

    /** (score, misses) of [w] for the typed letters [t], or null if it can't be what was meant. Lower is better. */
    private fun score(t: String, w: String, allow: Int, prior: Double): Pair<Double, Int>? {
        if (w.length < t.length) return null
        var miss = 0
        for (j in t.indices) {
            val a = t[j]; val b = w[j]
            if (a == b) continue
            if (!near(a, b)) return null
            if (++miss > allow) return null
        }
        val rest = w.length - t.length
        return Pair(prior + miss * 3.0 + rest * 0.12 + (if (rest == 0) -1.0 else 0.0), miss)
    }

    private fun near(a: Char, b: Char): Boolean {
        val pa = pos[a] ?: return false; val pb = pos[b] ?: return false
        val (petA, kA) = pa / 4 to pa % 4; val (petB, kB) = pb / 4 to pb % 4
        if (petA == petB) return true
        val d = (petA - petB + 8) % 8
        return kA == kB && (d == 1 || d == 7)
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
