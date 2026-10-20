package app.pulk

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.io.File

/** Where the typed text goes. The real one is the app being typed in; tests use a fake one. */
interface TextSink {
    fun commit(s: String)
    fun deleteBefore(n: Int)
    fun before(n: Int): String
    fun key(code: Int)
    fun action(a: Int)
    fun capsOn(inputType: Int): Boolean
    fun after(n: Int): String = ""
    /** Moves the cursor by [delta] characters (negative: back). */
    fun moveCursor(delta: Int) {}
}

/**
 * The keyboard service: turns the gestures of the star into text in whatever app is typing.
 * It reads text only to know where a sentence starts, to suggest and correct the word being typed,
 * to delete a word and to read it aloud; nothing leaves the phone (the app has no internet permission).
 */
class PulkService : InputMethodService() {
    lateinit var prefs: Prefs
    lateinit var feedback: Feedback
    private var view: PulseView? = null

    var lang = "ru"; private set
    var symbols = false; private set
    var shiftOnce = false; private set
    var shiftLock = false; private set
    private var autoCap = false
    private var secure = false      // password fields: nothing is read aloud or remembered
    private var lastSpace = 0L

    // ---- words ----
    private val dicts = HashMap<String, Words>()
    private var suggestHere = false   // this field wants suggestions
    private var correctHere = false   // and autocorrection
    private var learnHere = false     // and may teach the keyboard new words
    var cands: List<String> = emptyList(); private set
    /** The last autocorrection, so ⌫ right after it brings back what was typed. */
    private var undo: Pair<String, String>? = null
    private val refused = HashSet<String>()
    private var unsaved = 0

    private val realSink = object : TextSink {
        override fun commit(s: String) { currentInputConnection?.commitText(s, 1) }
        override fun deleteBefore(n: Int) { currentInputConnection?.deleteSurroundingText(n, 0) }
        override fun before(n: Int) = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString() ?: ""
        override fun key(code: Int) { sendDownUpKeyEvents(code) }
        override fun action(a: Int) { currentInputConnection?.performEditorAction(a) }
        override fun capsOn(inputType: Int) = (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0
        override fun after(n: Int) = currentInputConnection?.getTextAfterCursor(n, 0)?.toString() ?: ""
        override fun moveCursor(delta: Int) {
            val ic = currentInputConnection ?: return
            if (selStart >= 0) { val p = maxOf(0, selStart + delta); ic.setSelection(p, p); selStart = p; selEnd = p }
            else repeat(kotlin.math.abs(delta)) { sendDownUpKeyEvents(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT) }
        }
    }
    /** Where the cursor is in the field, as the app last told us (-1: not known). */
    private var selStart = -1
    private var selEnd = -1
    var sink: TextSink = realSink

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); feedback = Feedback(this, prefs); lang = prefs.lang
        val a11y = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        feedback.exploring = { a11y.isEnabled && a11y.isTouchExplorationEnabled }
        feedback.announcer = { t -> view?.announceForAccessibility(t) }
        Thread { words("ru"); words("en") }.start() // ready before the first word, without holding up the keyboard
    }

    override fun onDestroy() { save(); feedback.shutdown(); Coach.onChange = null; super.onDestroy() }

    override fun onCreateInputView(): View = PulseView(this, this).also { v ->
        view = v
        Coach.onChange = { v.post { refreshCands(); v.refresh() } }
    }

    override fun onEvaluateFullscreenMode() = false // never take over the whole screen, even in landscape

    override fun onWindowShown() {
        super.onWindowShown()
        // the window itself is see-through: only the keyboard's shape is drawn
        window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        view?.open(sound = true)
    }
    override fun onWindowHidden() { view?.reset(open = false); super.onWindowHidden() }

    private val region = android.graphics.Region()
    /**
     * Touches outside the keyboard's shape go to the app, and the app is laid out down to the arc's height
     * in the middle of the screen, so what is behind the see-through parts is the app itself.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = view ?: return
        if (v.width == 0) return
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val top = loc[1] + v.contentTop()
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        v.touchRegion(region)
        region.translate(loc[0], loc[1])
        outInsets.touchableRegion.set(region)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
    }

    /** The keyboard opened, hid or changed shape: Android asks for the insets again. */
    fun insetsChanged() { view?.requestLayout() }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        startInput(info)
        if (!restarting) view?.open(sound = true) // a new field: the keyboard comes out again if it was hidden
    }

    override fun onFinishInputView(finishingInput: Boolean) { save(); super.onFinishInputView(finishingInput) }

    /** Reads what kind of field this is. Separate from the Android callback so it can be tested. */
    fun startInput(info: EditorInfo) {
        lang = prefs.lang
        prefs.applyBias()
        if (prefs.forget) { prefs.forget = false; forget() }
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val text = cls == InputType.TYPE_CLASS_TEXT
        symbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        secure = (text && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val address = variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_FILTER
        val noSuggest = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        suggestHere = text && !secure && !address && prefs.suggest
        correctHere = suggestHere && !noSuggest && prefs.autocorrect
        learnHere = suggestHere && prefs.learn && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        editor = info
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        shiftOnce = false; undo = null
        updateAutoCap(); refreshCands(); refreshText()
        view?.refresh()
    }
    private var editor: EditorInfo? = null
    val isSecure get() = secure

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
        selStart = c; selEnd = d
        updateAutoCap(); refreshCands(); refreshText(); view?.invalidate()
    }

    private fun updateAutoCap() {
        val info = editor ?: currentInputEditorInfo
        autoCap = info != null && (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && sink.capsOn(info.inputType)
    }

    fun shiftActive() = shiftLock || shiftOnce || autoCap

    // ---- the dictionary ----
    /** The dictionary for [l], loaded once (with this person's own words). Null while loading on another thread. */
    @Synchronized fun words(l: String): Words = dicts.getOrPut(l) {
        val list = assets.open("dict_$l.txt").bufferedReader().readLines()
        Words(list, l).also { w ->
            try { w.loadNext(assets.open("next_$l.txt").bufferedReader().readLines()) } catch (_: Exception) {}
            val pf = pairsFile(l)
            if (pf.exists()) pf.readLines().forEach { line ->
                val p = line.split('\t'); if (p.size == 3) p[2].toIntOrNull()?.let { c -> w.pairs.getOrPut(p[0]) { HashMap() }[p[1]] = c }
            }
            val f = userFile(l)
            if (f.exists()) f.readLines().forEach { line ->
                val p = line.split('\t'); if (p.size == 2) p[1].toIntOrNull()?.let { w.user[p[0]] = it }
            }
        }
    }
    private fun wordsIfReady(l: String): Words? = synchronized(this) { dicts[l] }
    private fun userFile(l: String) = File(filesDir, "words_$l.txt")
    private fun pairsFile(l: String) = File(filesDir, "pairs_$l.txt")

    private fun save() {
        if (unsaved == 0) return
        unsaved = 0
        for ((l, w) in synchronized(this) { dicts.toMap() }) {
            if (w.user.isEmpty()) continue
            val top = w.user.entries.sortedByDescending { it.value }.take(5000)
            try { userFile(l).writeText(top.joinToString("\n") { "${it.key}\t${it.value}" }) } catch (_: Exception) {}
            val ps = w.pairs.flatMap { (a, m) -> m.map { Triple(a, it.key, it.value) } }.sortedByDescending { it.third }.take(20000)
            try { pairsFile(l).writeText(ps.joinToString("\n") { "${it.first}\t${it.second}\t${it.third}" }) } catch (_: Exception) {}
        }
    }

    /** Clears the words this keyboard has learned (from the settings screen). */
    fun forget() { synchronized(this) { dicts.values.forEach { it.user.clear() } }; listOf("ru", "en").forEach { userFile(it).delete(); pairsFile(it).delete() }; synchronized(this) { dicts.values.forEach { it.pairs.clear() } } }

    private fun currentWord(): String = Regex("\\p{L}[\\p{L}']*$").find(sink.before(48))?.value ?: ""

    private fun refreshCands() {
        val w = currentWord()
        val dict = wordsIfReady(lang)
        cands = when {
            !suggestHere || symbols || Coach.active || dict == null -> emptyList()
            w.isNotEmpty() -> dict.suggest(w)
            prefs.predict -> prevWord()?.let { p -> dict.next(p).map { if (shiftActive()) it.replaceFirstChar { c -> c.uppercase() } else it } } ?: emptyList()
            else -> emptyList()
        }
    }

    /** Before a new word: the word before it, "^" at the start of a sentence, or null in the middle of something else. */
    private fun prevWord(): String? {
        val b = sink.before(60)
        if (b.isBlank()) return "^"
        if (!b.last().isWhitespace()) return null
        val t = b.trimEnd()
        if (t.last() in ".!?…\n") return "^"
        return Regex("[\\p{L}']+$").find(t)?.value ?: "^"
    }

    private fun learn(w: String) {
        if (!learnHere) return
        val dict = wordsIfReady(lang) ?: return
        // and what came before it, so next time it can be offered before a letter is typed
        val before = sink.before(80).trimEnd().let { if (it.endsWith(w)) it.dropLast(w.length) else it }.trimEnd()
        val prev = if (before.isEmpty() || before.last() in ".!?…\n") "^" else Regex("[\\p{L}']+$").find(before)?.value
        if (prev != null) dict.learnPair(prev, w)
        if (w.length >= 2) dict.learn(w)
        if (++unsaved >= 20) save()
    }

    /**
     * The word just before the cursor is finished (a space or punctuation follows). An unknown word is
     * replaced with the closest one, which ⌫ can undo; a word that stays is remembered.
     */
    private fun finishWord(): String? {
        val w = currentWord()
        if (w.isEmpty()) return null
        val dict = wordsIfReady(lang)
        if (correctHere && dict != null && Words.fold(w.lowercase()) !in refused) {
            val fix = dict.correction(w)
            if (fix != null && fix != w) {
                sink.deleteBefore(w.length); sink.commit(fix)
                undo = w to fix
                return fix
            }
        }
        learn(w)
        return w
    }

    // ---- what the view asks for ----
    fun typeLetter(v: String, slot: Pulse.Slot) {
        val s = if (shiftActive() && !symbols) v.uppercase() else v
        feedback.hush()
        undo = null
        sink.commit(s)
        feedback.letter(slot, s, lang, secure)
        if (!shiftLock) shiftOnce = false
        after()
    }

    fun space() {
        feedback.hush()
        val now = System.currentTimeMillis()
        val before = sink.before(2)
        if (now - lastSpace < 350 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            sink.deleteBefore(1); sink.commit(". "); lastSpace = 0; undo = null
            feedback.space(null, secure)
        } else {
            val word = finishWord()
            sink.commit(" "); lastSpace = now
            feedback.space(word, secure)
        }
        after()
    }

    /** Picks a suggestion: it replaces the word being typed, and a space follows. */
    fun pick(i: Int) {
        val w = cands.getOrNull(i) ?: return
        val typed = currentWord()
        sink.deleteBefore(typed.length); sink.commit("$w ")
        undo = null
        learn(w)
        feedback.space(w, secure)
        after()
    }

    /** The gesture for the best suggestion: along the space key upwards. Without suggestions, a space. */
    fun pickBest() { if (cands.isNotEmpty()) pick(0) else space() }

    /** Space gesture outwards / inwards: a full stop or a comma that replaces the space before it. */
    fun punct(c: String) {
        if (sink.before(40).isBlank()) return
        finishWord()
        val before = sink.before(40)
        val spaces = before.length - before.trimEnd().length
        if (spaces > 0) sink.deleteBefore(spaces)
        sink.commit("$c ")
        feedback.letter(Pulse.Slot(-1, 'c', c), c, lang, secure)
        after()
    }

    fun deleteWord() {
        val before = sink.before(60)
        val m = Regex("\\S+\\s*$").find(before)?.value?.length ?: return // the word and the spaces after it
        sink.deleteBefore(m); undo = null
        feedback.backspace(); after()
    }

    fun backspace() {
        feedback.hush()
        val u = undo
        undo = null
        if (u != null) {
            val (typed, fix) = u
            val tail = sink.before(fix.length + 1)
            if (tail == "$fix " || tail.endsWith(fix)) {
                // ⌫ right after a correction: the typed word comes back and stays
                sink.deleteBefore(if (tail.endsWith(" ")) fix.length + 1 else fix.length)
                sink.commit(typed)
                refused.add(Words.fold(typed.lowercase()))
                if (learnHere) { wordsIfReady(lang)?.keep(typed); unsaved++ }
                feedback.backspace(); after(); return
            }
        }
        sink.key(KeyEvent.KEYCODE_DEL) // deletes the selection, or one character
        feedback.backspace(); after()
    }

    fun enter() {
        val info = editor ?: currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        finishWord(); undo = null
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) sink.action(action)
        else sink.key(KeyEvent.KEYCODE_ENTER)
        feedback.mode(); after()
    }

    fun shiftTap(double: Boolean) {
        if (shiftLock) { shiftLock = false; shiftOnce = false }
        else if (double) shiftLock = true
        else shiftOnce = !shiftOnce
        feedback.mode(); view?.invalidate()
    }

    fun switchLang() { lang = if (lang == "ru") "en" else "ru"; prefs.lang = lang; symbols = false; feedback.mode(); after() }
    fun toggleSymbols() { symbols = !symbols; feedback.mode(); after() }

    /** Long press on ⌄: the app's settings, right from the keyboard. */
    fun openSettings() { startActivity(android.content.Intent(this, SettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); requestHideSelf(0) }

    /** Long press on the language key: Android's list of keyboards, to get back to another one. */
    fun pickKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }

    private fun after() { updateAutoCap(); refreshCands(); refreshText(); view?.invalidate() }

    /** The text around the cursor, for the keyboard's own text window. Passwords show as dots. */
    /** The view asks: the cursor goes [delta] characters along; suggestions start over at the new place. */
    fun moveCursor(delta: Int) {
        if (delta == 0) return
        undo = null
        sink.moveCursor(delta)
        feedback.touch(); after()
    }

    var textBefore = ""; private set
    var textAfter = ""; private set
    private fun refreshText() {
        val b = sink.before(160); val a = sink.after(60)
        textBefore = if (secure) "•".repeat(minOf(b.length, 24)) else b
        textAfter = if (secure) "" else a
    }
}
