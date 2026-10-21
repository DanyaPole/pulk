package app.pulk

/**
 * What two people do together in «Говорить» (as the web version): a choice the adult offers, the family's own
 * words with photos, and the conversation in turns. Kept as plain lines in the preferences. Pure Kotlin, tested.
 */
object Together {
    private const val SEP = '\u001F'

    /** A choice the adult offered: an optional question and up to six options the child touches. */
    data class Choice(val q: String, val opts: List<String>)
    fun encode(c: Choice) = (listOf(c.q) + c.opts).joinToString(SEP.toString())
    fun decode(s: String): Choice? {
        if (s.isEmpty()) return null
        val p = s.split(SEP); val opts = p.drop(1).filter { it.isNotBlank() }
        return if (opts.isEmpty()) null else Choice(p[0], opts)
    }
    /** «сок, вода; молоко» → three options with a capital letter, at most six, no repeats. */
    fun options(typed: String) = typed.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        .map { it.replaceFirstChar { c -> c.uppercase() } }.distinctBy { norm(it) }.take(6)

    /** The family's words: names, pets, favourite things — each may have a photo (a file in the app's own storage). */
    data class Word(val word: String, val photo: String)
    fun family(lines: List<String>) = lines.mapNotNull { l -> val p = l.split('\t'); if (p[0].isBlank()) null else Word(p[0], p.getOrElse(1) { "" }) }
    fun familyLines(ws: List<Word>) = ws.map { "${it.word}\t${it.photo}" }
    fun photoOf(ws: List<Word>, word: String) = ws.firstOrNull { norm(it.word) == norm(word) }?.photo?.takeIf { it.isNotEmpty() }

    /** The conversation: who said it (the child «me», the adult «you»), when, what. The last hundred lines. */
    data class Line(val me: Boolean, val at: Long, val text: String)
    fun talk(lines: List<String>) = lines.mapNotNull { l ->
        val p = l.split('\t', limit = 3); if (p.size == 3) Line(p[0] == "me", p[1].toLongOrNull() ?: 0L, p[2]) else null
    }
    fun said(lines: List<String>, me: Boolean, text: String, at: Long = System.currentTimeMillis()): List<String> {
        val t = text.replace('\n', ' ').replace('\t', ' ').trim(); if (t.isEmpty()) return lines
        return (lines + "${if (me) "me" else "you"}\t$at\t$t").takeLast(100)
    }

    /** The week, kept on this phone only: «2026-10-21:12:3» — a day, phrases said, phrases said for the first time. */
    fun logged(lines: List<String>, day: String, fresh: Boolean): List<String> {
        val m = LinkedHashMap<String, IntArray>()
        for (l in lines) { val p = l.split(':'); if (p.size == 3) m[p[0]] = intArrayOf(p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0) }
        val v = m.getOrPut(day) { intArrayOf(0, 0) }; v[0]++; if (fresh) v[1]++
        return m.entries.toList().takeLast(60).map { "${it.key}:${it.value[0]}:${it.value[1]}" }
    }
    /** Said, new and days over the last seven days, today included. */
    fun week(lines: List<String>, today: java.time.LocalDate): IntArray {
        val out = intArrayOf(0, 0, 0)
        for (l in lines) {
            val p = l.split(':'); if (p.size != 3) continue
            val d = try { java.time.LocalDate.parse(p[0]) } catch (_: Exception) { continue }
            if (!d.isAfter(today) && d.isAfter(today.minusDays(7))) { out[0] += p[1].toIntOrNull() ?: 0; out[1] += p[2].toIntOrNull() ?: 0; out[2]++ }
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()
}
