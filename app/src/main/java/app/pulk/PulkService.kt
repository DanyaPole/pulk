package app.pulk

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/** Where the typed text goes. The real one is the app being typed in; tests use a fake one. */
interface TextSink {
    fun commit(s: String)
    fun deleteBefore(n: Int)
    fun before(n: Int): String
    fun key(code: Int)
    fun action(a: Int)
    fun capsOn(inputType: Int): Boolean
}

/**
 * The keyboard service: turns the gestures of the star into text in whatever app is typing.
 * It reads text only to know where a sentence starts, to delete a word and to read it aloud;
 * nothing leaves the phone (the app has no internet permission).
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
    private var secure = false      // password fields: nothing is read aloud
    private var lastSpace = 0L

    private val realSink = object : TextSink {
        override fun commit(s: String) { currentInputConnection?.commitText(s, 1) }
        override fun deleteBefore(n: Int) { currentInputConnection?.deleteSurroundingText(n, 0) }
        override fun before(n: Int) = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString() ?: ""
        override fun key(code: Int) { sendDownUpKeyEvents(code) }
        override fun action(a: Int) { currentInputConnection?.performEditorAction(a) }
        override fun capsOn(inputType: Int) = (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0
    }
    var sink: TextSink = realSink

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); feedback = Feedback(this, prefs); lang = prefs.lang
    }

    override fun onDestroy() { feedback.shutdown(); super.onDestroy() }

    override fun onCreateInputView(): View = PulseView(this, this).also { view = it }

    override fun onEvaluateFullscreenMode() = false // never take over the whole screen, even in landscape

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        startInput(info)
    }

    /** Reads what kind of field this is. Separate from the Android callback so it can be tested. */
    fun startInput(info: EditorInfo) {
        lang = prefs.lang
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        symbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        secure = (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        editor = info
        shiftOnce = false
        updateAutoCap()
        view?.refresh()
    }
    private var editor: EditorInfo? = null
    val isSecure get() = secure

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
        updateAutoCap(); view?.invalidate()
    }

    private fun updateAutoCap() {
        val info = editor ?: currentInputEditorInfo
        autoCap = info != null && (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && sink.capsOn(info.inputType)
    }

    fun shiftActive() = shiftLock || shiftOnce || autoCap

    // ---- what the view asks for ----
    fun typeLetter(v: String, slot: Pulse.Slot) {
        val s = if (shiftActive() && !symbols) v.uppercase() else v
        sink.commit(s)
        feedback.letter(slot, s, lang, secure)
        if (!shiftLock) shiftOnce = false
        updateAutoCap(); view?.invalidate()
    }

    fun space() {
        val now = System.currentTimeMillis()
        val before = sink.before(2)
        if (now - lastSpace < 350 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            sink.deleteBefore(1); sink.commit(". "); lastSpace = 0
        } else { sink.commit(" "); lastSpace = now }
        feedback.space(lastWord(), secure)
        updateAutoCap(); view?.invalidate()
    }

    /** Space gesture outwards / inwards: a full stop or a comma that replaces the space before it. */
    fun punct(c: String) {
        val before = sink.before(40)
        if (before.isBlank()) return
        val spaces = before.length - before.trimEnd().length
        if (spaces > 0) sink.deleteBefore(spaces)
        sink.commit("$c ")
        feedback.letter(Pulse.Slot(-1, 'c', c), c, lang, secure)
        updateAutoCap(); view?.invalidate()
    }

    fun deleteWord() {
        val before = sink.before(60)
        val m = Regex("\\s*\\S+\\s*$").find(before)?.value?.length ?: return
        sink.deleteBefore(m)
        feedback.backspace(); updateAutoCap(); view?.invalidate()
    }

    fun backspace() {
        sink.key(KeyEvent.KEYCODE_DEL) // deletes the selection, or one character
        feedback.backspace(); updateAutoCap(); view?.invalidate()
    }

    fun enter() {
        val info = editor ?: currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) sink.action(action)
        else sink.key(KeyEvent.KEYCODE_ENTER)
        feedback.mode(); updateAutoCap(); view?.invalidate()
    }

    fun shiftTap(double: Boolean) {
        if (shiftLock) { shiftLock = false; shiftOnce = false }
        else if (double) shiftLock = true
        else shiftOnce = !shiftOnce
        feedback.mode(); view?.invalidate()
    }

    fun switchLang() { lang = if (lang == "ru") "en" else "ru"; prefs.lang = lang; symbols = false; feedback.mode(); view?.invalidate() }
    fun toggleSymbols() { symbols = !symbols; feedback.mode(); view?.invalidate() }

    private fun lastWord(): String? =
        sink.before(50).trimEnd().let { Regex("[\\p{L}\\p{N}]+$").find(it)?.value }
}
