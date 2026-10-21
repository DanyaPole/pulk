package app.pulk

import android.view.KeyEvent
import kotlin.math.hypot

/**
 * A joystick, a gamepad or a number pad as the star (as the web version), nothing to set up. A stick works as
 * «Толчки»: a push out and back is a letter, a short push the letter at the centre, a push that turns the letter
 * with a turn. Number pad: a digit is a direction; held together with 5 — the centre letter, with a neighbouring
 * digit — the letter with a turn; the letter goes when the key is let go. Pure Kotlin, tested.
 */
object Physical {
    /** Number pad digits as on the star: 6 right, 9 up-right, 8 up, 7 up-left, 4 left, 1 down-left, 2 down, 3 down-right. */
    val DIR = mapOf(KeyEvent.KEYCODE_NUMPAD_6 to 0, KeyEvent.KEYCODE_NUMPAD_9 to 1, KeyEvent.KEYCODE_NUMPAD_8 to 2, KeyEvent.KEYCODE_NUMPAD_7 to 3,
        KeyEvent.KEYCODE_NUMPAD_4 to 4, KeyEvent.KEYCODE_NUMPAD_1 to 5, KeyEvent.KEYCODE_NUMPAD_2 to 6, KeyEvent.KEYCODE_NUMPAD_3 to 7)
    fun isStarKey(code: Int) = code in DIR || code == KeyEvent.KEYCODE_NUMPAD_5 || code == KeyEvent.KEYCODE_NUMPAD_0
    /** The left hand's star is mirrored. */
    fun mirror(p: Int, left: Boolean) = if (left) (4 - p + 8) % 8 else p

    /** A stick's deflection (−1…1, y down) in the star's base units: a dead zone, then up to 90 units. */
    fun stick(x: Float, y: Float, left: Boolean): DoubleArray {
        val m = hypot(x.toDouble(), y.toDouble())
        if (m <= 0.22) return doubleArrayOf(0.0, 0.0)
        val k = (m - 0.22) / 0.78 * 90 / m
        return doubleArrayOf(x * k * (if (left) -1 else 1), -y * k)
    }

    /** What to type: a petal and a kind ('c', 'l', 'r', 'b'), or petal −1 for a space. */
    data class Key(val petal: Int, val kind: Char)

    /** The number pad's state: the direction key held down, and what the keys held with it make of it. */
    class Keys {
        private var petal = -1; private var kind = 'c'; private var code = 0
        /** A key goes down; returns what must be typed now (a letter still held is typed when another one starts). */
        fun down(c: Int, left: Boolean): List<Key> {
            val d = DIR[c]
            if (d != null) {
                val p = mirror(d, left)
                if (petal >= 0 && kind == 'c') {
                    val diff = (p - petal + 8) % 8
                    if (diff in 1..3) { kind = 'l'; return emptyList() }   // a neighbour held with it: the turn that way
                    if (diff in 5..7) { kind = 'r'; return emptyList() }
                }
                val out = flush()
                petal = p; kind = 'c'; code = c
                return out
            }
            if (c == KeyEvent.KEYCODE_NUMPAD_5 && petal >= 0) { kind = 'b'; return emptyList() }   // with 5: the centre letter
            return flush() + Key(-1, 's')                                                            // 5 or 0 alone: a space
        }
        /** A key goes up: the held direction is typed when its own key is let go. */
        fun up(c: Int): List<Key> = if (petal >= 0 && c == code) flush() else emptyList()
        /** Forget a key held when the keyboard closed: its letter must not fall into the next field. */
        fun reset() { petal = -1; kind = 'c'; code = 0 }
        private fun flush(): List<Key> { if (petal < 0) return emptyList(); val k = Key(petal, kind); petal = -1; kind = 'c'; code = 0; return listOf(k) }
    }
}
