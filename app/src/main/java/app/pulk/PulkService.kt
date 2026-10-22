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
    private var prose = false       // ordinary text, not a password, an address or a number: two spaces may make a full stop
    private var lastSpace = 0L

    // ---- words ----
    private val dicts = HashMap<String, Words>()
    private var suggestHere = false   // this field wants suggestions
    private var correctHere = false   // and autocorrection
    private var learnHere = false     // and may teach the keyboard new words
    var cands: List<String> = emptyList(); private set
    /** The last autocorrection, so ⌫ right after it brings back what was typed. */
    /**
     * What ⌫ right after would take back: the text that was there ([was]) instead of what the keyboard put
     * ([now], just before the cursor); [keep] — the typed word refused a correction, remember it.
     */
    private class Undo(val was: String, val now: String, val keep: String? = null)
    private var undo: Undo? = null
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
        set(v) { field = v; io = CachedText(v) }
    /** Every question about the text goes through here: the app is read once per change, not once per question. */
    private var io = CachedText(realSink)

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

    /** Scanning: an outside switch (it acts as a keyboard key: space, enter, the centre of a d-pad) is the one button. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val v = view
        if (prefs.mode == "scan" && isInputViewShown && v != null &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            if (event.repeatCount == 0) v.scanPress()
            return true
        }
        if (isInputViewShown && physicalKey(keyCode, event, down = true)) return true
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // the switch's release is ours too: an Enter let through would move the app to its next field at every press
        if (prefs.mode == "scan" && isInputViewShown && view != null &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) return true
        if (isInputViewShown && physicalKey(keyCode, event, down = false)) return true
        return super.onKeyUp(keyCode, event)
    }

    // ---- a gamepad's buttons, the number pad and a joystick as the star (see [Physical]) ----
    private val keyStar = Physical.Keys()
    private var stickJabs: Pulse.Jabs? = null
    internal fun physicalKey(code: Int, event: KeyEvent?, down: Boolean): Boolean {
        val button = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> { { space() } }
            KeyEvent.KEYCODE_BUTTON_B -> { { backspace() } }
            KeyEvent.KEYCODE_BUTTON_X -> { { enter() } }
            KeyEvent.KEYCODE_BUTTON_Y -> { { shiftTap(false) } }
            else -> null
        }
        if (button != null) { if (down && (event?.repeatCount ?: 0) == 0) button(); return true }
        if (!Physical.isStarKey(code)) return false
        if (down && (event?.repeatCount ?: 0) > 0) return true
        (if (down) keyStar.down(code, prefs.left) else keyStar.up(code)).forEach { typeKey(it) }
        return true
    }
    private fun typeKey(k: Physical.Key) {
        if (k.petal < 0) { space(); return }
        val s = Pulse.at(Pulse.table(lang, symbols), k.petal, k.kind); typeLetter(s.v, s)
    }
    /** A stick moved: each push out and back is a letter. */
    internal fun stickAt(x: Float, y: Float) {
        val b = Physical.stick(x, y, prefs.left)
        val j = stickJabs ?: Pulse.Jabs(0.0, 0.0).also { stickJabs = it }
        j.add(b[0], b[1])?.let { val s = Pulse.jab(Pulse.table(lang, symbols), it); typeLetter(s.v, s) }
    }
    override fun onGenericMotionEvent(e: android.view.MotionEvent): Boolean {
        if (isInputViewShown && e.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK) && e.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
            stickAt(e.getAxisValue(android.view.MotionEvent.AXIS_X), e.getAxisValue(android.view.MotionEvent.AXIS_Y)); return true
        }
        return super.onGenericMotionEvent(e)
    }

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
        if (!restarting) { view?.newField(); view?.open(sound = true) } // a new field: the keyboard comes out again if it was hidden
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        save(); keyStar.reset(); stickJabs = null   // a key or stick held as the field closed types nothing later
        super.onFinishInputView(finishingInput)
    }
    /** Android is short of memory and may close Пульк without a word: the learned words are written down first. */
    override fun onTrimMemory(level: Int) { save(); super.onTrimMemory(level) }

    /** Reads what kind of field this is. Separate from the Android callback so it can be tested. */
    fun startInput(info: EditorInfo) {
        lang = prefs.lang
        mergeFamily()
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
        prose = text && !secure && !address
        suggestHere = prose && prefs.suggest
        correctHere = suggestHere && !noSuggest && prefs.autocorrect && !prefs.simple
        learnHere = suggestHere && prefs.learn && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        editor = info
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        io.drop(); io.expect = if (selStart == selEnd) selStart else -1
        shiftOnce = false; undo = null
        updateAutoCap(); refreshCands(); refreshText()
        view?.refresh()
    }
    private var editor: EditorInfo? = null
    val isSecure get() = secure

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
        selStart = c; selEnd = d
        // our own typing moves the cursor exactly where we expect it: then what we know of the text is still right
        if (c != d || c != io.expect) io.drop()
        io.expect = if (c == d) c else -1
        updateAutoCap(); refreshCands(); refreshText(); view?.invalidate()
    }

    private fun updateAutoCap() {
        val info = editor ?: currentInputEditorInfo
        autoCap = info != null && (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && io.capsOn(info.inputType)
    }

    fun shiftActive() = shiftLock || shiftOnce || autoCap

    // ---- the dictionary ----
    /** The dictionary for [l], loaded once (with this person's own words). Null while loading on another thread. */
    @Synchronized fun words(l: String): Words = dicts.getOrPut(l) {
        val list = assets.open("dict_$l.txt").bufferedReader().readLines()
        Words(list, l).also { w ->
            try { w.loadNext(assets.open("next_$l.txt").bufferedReader().readLines()) } catch (_: Exception) {}
            val pf = pairsFile(l)
            if (pf.exists()) lines(pf).forEach { line ->
                val p = line.split('\t'); if (p.size == 3) p[2].toIntOrNull()?.let { c -> w.pairs.getOrPut(p[0]) { HashMap() }[p[1]] = c }
            }
            val f = userFile(l)
            if (f.exists()) lines(f).forEach { line ->
                val p = line.split('\t'); if (p.size == 2) p[1].toIntOrNull()?.let { w.user[p[0]] = it }
            }
            familyInto(w)
        }
    }
    private fun wordsIfReady(l: String): Words? = synchronized(this) { dicts[l] }
    /** The family's words («Для взрослого» → «Словарь семьи») are this person's own: known, and first in suggestions. */
    private fun familyInto(w: Words) {
        for (f in Together.family(prefs.family)) { val k = Words.fold(f.word.lowercase()); if (k.length >= 2) w.user[k] = maxOf(w.user[k] ?: 0, 5) }
    }
    internal fun mergeFamily() { for ((_, w) in synchronized(this) { dicts.toMap() }) familyInto(w) }
    private fun userFile(l: String) = File(filesDir, "words_$l.txt")
    private fun pairsFile(l: String) = File(filesDir, "pairs_$l.txt")
    /** A file of words that cannot be read is as if empty: the keyboard opens anyway. */
    private fun lines(f: File): List<String> = try { f.readLines() } catch (_: Exception) { emptyList() }
    /** All or nothing: written beside and then put in place, so a phone that dies mid-write keeps the old words. */
    private fun writeWhole(f: File, text: String) {
        val tmp = File(f.path + ".new")
        try { tmp.writeText(text); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) } } catch (_: Exception) { tmp.delete() }
    }

    private fun save() {
        if (unsaved == 0) return
        unsaved = 0
        for ((l, w) in synchronized(this) { dicts.toMap() }) {
            if (w.user.isEmpty()) continue
            val top = w.user.entries.sortedByDescending { it.value }.take(5000)
            writeWhole(userFile(l), top.joinToString("\n") { "${it.key}\t${it.value}" })
            val ps = w.pairs.flatMap { (a, m) -> m.map { Triple(a, it.key, it.value) } }.sortedByDescending { it.third }.take(20000)
            writeWhole(pairsFile(l), ps.joinToString("\n") { "${it.first}\t${it.second}\t${it.third}" })
        }
    }

    /** Clears the words this keyboard has learned (from the settings screen). */
    fun forget() { synchronized(this) { dicts.values.forEach { it.user.clear() } }; listOf("ru", "en").forEach { l -> for (f in listOf(userFile(l), pairsFile(l))) { f.delete(); File(f.path + ".new").delete() } }; synchronized(this) { dicts.values.forEach { it.pairs.clear() } } }

    private fun currentWord(): String = Regex("\\p{L}[\\p{L}']*$").find(io.before(48))?.value ?: ""

    private fun refreshCands() {
        val w = currentWord()
        val dict = wordsIfReady(lang)
        cands = when {
            !suggestHere || symbols || Coach.active || dict == null -> emptyList()
            w.isNotEmpty() -> if (prefs.simple) dict.suggest(w, 1) else dict.suggest(w)
            prefs.predict && !prefs.simple -> prevWord()?.let { p -> dict.next(p).map { if (shiftActive()) it.replaceFirstChar { c -> c.uppercase() } else it } } ?: emptyList()
            else -> emptyList()
        }
    }

    /** Before a new word: the word before it, "^" at the start of a sentence, or null in the middle of something else. */
    private fun prevWord(): String? {
        val b = io.before(60)
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
        val before = io.before(80).trimEnd().let { if (it.endsWith(w)) it.dropLast(w.length) else it }.trimEnd()
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
                io.deleteBefore(w.length); io.commit(fix)
                undo = Undo(w, fix, keep = w)
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
        io.commit(s)
        feedback.letter(slot, s, lang, secure)
        if (!shiftLock) shiftOnce = false
        after()
    }

    fun space() {
        feedback.hush()
        val now = System.currentTimeMillis()
        val before = io.before(2)
        // a password, an address or a phone number keeps both spaces: a full stop there would be a wrong character
        if (prose && now - lastSpace < 350 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            io.deleteBefore(1); io.commit(". "); lastSpace = 0; undo = null
            feedback.space(null, secure)
        } else {
            val word = finishWord()
            io.commit(" "); lastSpace = now
            feedback.space(word, secure)
        }
        after()
    }

    /** Picks a suggestion: it replaces the word being typed, and a space follows. */
    fun pick(i: Int) {
        val w = cands.getOrNull(i) ?: return
        val typed = currentWord()
        io.deleteBefore(typed.length); io.commit("$w ")
        undo = Undo(typed, "$w ", keep = typed)   // refusing a suggestion also says: this word is mine
        learn(w)
        feedback.space(w, secure)
        after()
    }

    /** The gesture for the best suggestion: along the space key upwards. Without suggestions, a space. */
    fun pickBest() { if (cands.isNotEmpty()) pick(0) else space() }

    /**
     * Space gesture outwards / inwards: a full stop or a comma that replaces the space before it.
     * In a password, an address or a number it is just the character: «ivan.petrov@mail.ru» needs no spaces.
     */
    fun punct(c: String) {
        if (!prose) { feedback.hush(); undo = null; io.commit(c); feedback.letter(Pulse.Slot(-1, 'c', c), c, lang, secure); after(); return }
        if (io.before(40).isBlank()) return
        finishWord()
        val before = io.before(40)
        val spaces = before.length - before.trimEnd().length
        if (spaces > 0) io.deleteBefore(spaces)
        io.commit("$c ")
        feedback.letter(Pulse.Slot(-1, 'c', c), c, lang, secure)
        after()
    }

    fun deleteWord() {
        val before = io.before(60)
        val m = Regex("\\S+\\s*$").find(before)?.value?.length ?: return // the word and the spaces after it
        undo = Undo(before.takeLast(m), "")
        io.deleteBefore(m)
        feedback.backspace(); after()
    }

    fun backspace() {
        feedback.hush()
        val u = undo
        undo = null
        if (u != null) {
            // the correction may have been followed by its space: take that back too
            val tail = io.before(u.now.length + 1)
            val n = when { u.now.isEmpty() -> 0; tail == u.now + " " -> u.now.length + 1; tail.endsWith(u.now) -> u.now.length; else -> -1 }
            if (n >= 0) {
                if (n > 0) io.deleteBefore(n)
                io.commit(u.was)
                u.keep?.let { typed -> refused.add(Words.fold(typed.lowercase())); if (learnHere) { wordsIfReady(lang)?.keep(typed); unsaved++ } }
                feedback.backspace(); after(); return
            }
        }
        io.key(KeyEvent.KEYCODE_DEL) // deletes the selection, or one character
        feedback.backspace(); after()
    }

    fun enter() {
        val info = editor ?: currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        finishWord(); undo = null
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) io.action(action)
        else io.key(KeyEvent.KEYCODE_ENTER)
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
        io.moveCursor(delta)
        feedback.touch(); after()
    }

    var textBefore = ""; private set
    var textAfter = ""; private set
    private fun refreshText() {
        if (!prefs.textWindow) return   // nobody is looking: no need to read the text
        val b = io.before(160); val a = io.after(60)
        textBefore = if (secure) "•".repeat(minOf(b.length, 24)) else b
        textAfter = if (secure) "" else a
    }
}

/**
 * Reads the text around the cursor once and answers from it until something changes it. Our own typing updates
 * it in place (we know what we typed); anything else — a key whose effect we cannot know, the app changing the
 * text, the cursor moving — drops it, and the next question reads again.
 */
class CachedText(private val s: TextSink) : TextSink {
    private var b: String? = null
    private var a: String? = null
    private var caps: Pair<Int, Boolean>? = null
    /** Where the cursor should be after our edits (-1: not known). */
    var expect = -1

    fun drop() { b = null; a = null; caps = null }

    override fun before(n: Int): String {
        if (n > SPAN) return s.before(n)
        val c = b ?: s.before(SPAN).also { b = it }
        return if (c.length > n) c.substring(c.length - n) else c
    }
    override fun after(n: Int): String {
        if (n > SPAN) return s.after(n)
        val c = a ?: s.after(SPAN).also { a = it }
        return if (c.length > n) c.substring(0, n) else c
    }
    override fun capsOn(inputType: Int): Boolean {
        caps?.let { if (it.first == inputType) return it.second }
        return s.capsOn(inputType).also { caps = inputType to it }
    }
    override fun commit(s: String) {
        b = b?.let { (it + s).takeLast(SPAN) }; caps = null
        if (expect >= 0) expect += s.length
        this.s.commit(s)
    }
    override fun deleteBefore(n: Int) {
        val c = b
        // only when what we know reaches back far enough; otherwise read again next time
        b = if (c != null && (c.length >= n || c.length < SPAN)) c.dropLast(n) else null; caps = null
        if (expect >= 0) expect = maxOf(0, expect - n)
        s.deleteBefore(n)
    }
    override fun key(code: Int) {
        // ⌫ with nothing selected (we know where the cursor is): one character goes, as we can tell ourselves
        val c = b
        if (code == KeyEvent.KEYCODE_DEL && expect > 0 && c != null && c.isNotEmpty()) { b = c.dropLast(1); caps = null; expect--; a = a }
        else { drop(); expect = -1 }
        s.key(code)
    }
    override fun action(a: Int) { drop(); expect = -1; s.action(a) }
    override fun moveCursor(delta: Int) { drop(); expect = -1; s.moveCursor(delta) }

    companion object { const val SPAN = 200 }
}
