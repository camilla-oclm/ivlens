package com.ivlens.app

import com.ivlens.app.ui.NumberInput
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The owner's Venusaur (2026-10-25): to fix one digit of the CP on the number pad, the whole
 * number had to be deleted from the end and typed again.
 */
class NumberInputTest {
    @Test
    fun oneDigitIsFixedInPlace() {
        // 2209 to 2309: tap between the two 2s' right side, delete, type.
        val fixed = NumberInput("2209", 4).moveTo(2).backspace().type('3')
        assertEquals(NumberInput("2309", 2), fixed)
    }

    @Test
    fun typingAtTheEndWorksAsBefore() {
        assertEquals(NumberInput("1234", 4), NumberInput("", 0).type('1').type('2').type('3').type('4'))
        assertEquals(NumberInput("123", 3), NumberInput("1234", 4).backspace())
    }

    @Test
    fun leadingZerosAreDropped() {
        assertEquals(NumberInput("9", 0), NumberInput("2009", 1).backspace())
        assertEquals(NumberInput("209", 0), NumberInput("209", 0).type('0'))
        assertEquals(NumberInput("", 0), NumberInput("", 0).type('0'))
    }

    /** 1.22: the owner's Torchic, CP 610 read as 10. ← → put the cursor in front without a fiddly tap. */
    @Test
    fun arrowsMoveOneDigit() {
        val fixed = NumberInput("10", 2).left().left().left().type('6')
        assertEquals(NumberInput("610", 1), fixed)
        assertEquals(NumberInput("610", 3), fixed.right().right().right())
    }

    @Test
    fun edgesHold() {
        assertEquals(NumberInput("209", 0), NumberInput("209", 0).backspace())
        assertEquals(NumberInput("209", 3), NumberInput("209", 1).moveTo(9))
        assertEquals(NumberInput("209", 0), NumberInput("209", 1).moveTo(-1))
        assertEquals(NumberInput("123456", 2), NumberInput("123456", 2).type('7'))
    }
}
