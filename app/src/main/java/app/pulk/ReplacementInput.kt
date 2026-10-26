package app.pulk

/** Text typed since the last boundary, with word ranges independent of the proposal queue. */
internal class ReplacementInput(words: Words, profile: ReplacementBank.Profile, enabled: Boolean) {
    val bank = ReplacementBank(words, profile, enabled)
    var text = ""; private set
    private data class Span(val start: Int, val end: Int)
    private val spans = ArrayList<Span>()
    private var scanned = 0
    private val token = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")

    fun append(s: String, key: String? = null) {
        if (!bank.enabled || s.isEmpty()) return
        // Continuing a word after closing a review is an edit to a completed word.
        if (spans.lastOrNull()?.end == text.length && s.first().isLetterOrDigit()) endPhrase()
        val start = text.length
        text += s
        scan(false, start, key)
    }

    private fun scan(finish: Boolean, autoStart: Int = -1, key: String? = null) {
        for (m in token.findAll(text, scanned)) {
            val end = m.range.last + 1
            if (end == text.length && !finish) break
            bank.addWord(m.value, if (m.range.first == autoStart) key else null)
            spans.add(Span(m.range.first, end)); scanned = end
        }
    }
    fun finishWord() = scan(true)

    /** Known edits before the cursor: punctuation and correction of the unfinished word keep prior ranges. */
    fun trim(n: Int) {
        if (n > text.length - (spans.lastOrNull()?.end ?: 0)) { endPhrase(); return }
        text = text.dropLast(n); scanned = minOf(scanned, text.length)
    }

    fun accept(i: Int): Boolean {
        if (bank.accept(i) == null) return false
        rewrite(); return true
    }
    fun undo(): Boolean {
        if (!bank.undo()) return false
        rewrite(); return true
    }
    private fun rewrite() {
        val ws = bank.textWords()
        val out = StringBuilder(); var at = 0
        for (i in spans.indices) {
            val old = spans[i]; out.append(text.substring(at, old.start))
            val start = out.length; out.append(ws[i]); spans[i] = Span(start, out.length)
            at = old.end
        }
        out.append(text.substring(at)); text = out.toString()
        scanned = spans.lastOrNull()?.end ?: 0
    }
    fun endPhrase() { bank.endPhrase(); spans.clear(); text = ""; scanned = 0 }
}
