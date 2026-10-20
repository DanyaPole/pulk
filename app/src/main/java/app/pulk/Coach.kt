package app.pulk

/**
 * The link between the tutorial screen and the keyboard. Both run in the same app, so the tutorial
 * tells the keyboard what the learner should type next and how much help to give, and the keyboard
 * shows it right on the star. Help is offered, never forced (see the tutorial).
 */
object Coach {
    @Volatile var active = false
    /** The letter (or " ") the learner is about to type, if the text so far is on track. */
    @Volatile var next: String? = null
    /** 0 none · 1 hint in words · 2 the keyboard lights the letter and its path · 3 hand in hand: near strokes become the letter. */
    @Volatile var help = 0
    /** The last level is typed by ear: the letters on the star are hidden. */
    @Volatile var hideLabels = false
    /** The lesson teaches «Толчки» (a word per touch) rather than a stroke per letter. */
    @Volatile var jabs = false
    /** One short line for the strip above the star. */
    @Volatile var hint: String? = null

    var onChange: (() -> Unit)? = null
    fun changed() { onChange?.invoke() }

    fun reset() { active = false; next = null; help = 0; hideLabels = false; hint = null; jabs = false; changed() }
}
