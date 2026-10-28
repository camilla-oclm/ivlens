package com.ivlens.app.ui

/**
 * The card's number pad: the digits typed and where the cursor sits (0 to [text]'s length).
 * Digits go in and ⌫ takes out at the cursor, so one wrong digit (CP 2209 for 2309) is fixed
 * by tapping next to it, not by deleting from the end (1.20). Leading zeros are dropped.
 */
data class NumberInput(val text: String, val cursor: Int) {
    fun type(c: Char): NumberInput =
        if (text.length >= MAX_DIGITS) this else trimmed(text.take(cursor) + c + text.drop(cursor), cursor + 1)

    fun backspace(): NumberInput =
        if (cursor == 0) this else trimmed(text.take(cursor - 1) + text.drop(cursor), cursor - 1)

    fun moveTo(at: Int): NumberInput = copy(cursor = at.coerceIn(0, text.length))

    /** The ← and → keys (1.22): one digit at a time, when a tap lands in the wrong gap. */
    fun left(): NumberInput = moveTo(cursor - 1)

    fun right(): NumberInput = moveTo(cursor + 1)

    private fun trimmed(t: String, at: Int): NumberInput {
        val s = t.trimStart('0')
        return NumberInput(s, (at - (t.length - s.length)).coerceIn(0, s.length))
    }

    companion object {
        const val MAX_DIGITS = 6
    }
}
