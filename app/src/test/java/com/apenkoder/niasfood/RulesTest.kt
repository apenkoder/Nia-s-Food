package com.apenkoder.niasfood

import com.apenkoder.niasfood.domain.LockoutPolicy
import com.apenkoder.niasfood.domain.MobileMoneyCode
import com.apenkoder.niasfood.domain.PinRules
import com.apenkoder.niasfood.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test fun mobileMoneyCodeValidation() {
        assertTrue(MobileMoneyCode.isValid("QGH7X2K9LP"))
        assertTrue(MobileMoneyCode.isValid("  qgh7x2k9lp "))
        assertEquals("QGH7X2K9LP", MobileMoneyCode.normalize(" qgh7x2k9lp "))
        assertFalse(MobileMoneyCode.isValid("SHORT1"))
        assertFalse(MobileMoneyCode.isValid("QGH7X2K9LP1"))
        assertFalse(MobileMoneyCode.isValid("QGH7X2K9L-"))
        assertFalse(MobileMoneyCode.isValid(""))
    }

    @Test fun pinFormatByRole() {
        assertTrue(PinRules.isWellFormed("2580", Role.SERVER))
        assertFalse(PinRules.isWellFormed("258012", Role.SERVER))
        assertTrue(PinRules.isWellFormed("482915", Role.OWNER))
        assertFalse(PinRules.isWellFormed("4829", Role.MANAGER))
        assertFalse(PinRules.isWellFormed("48291a", Role.OWNER))
    }

    @Test fun weakPinsAreRejected() {
        assertTrue(PinRules.isWeak("1111"))
        assertTrue(PinRules.isWeak("1234"))
        assertTrue(PinRules.isWeak("4321"))
        assertTrue(PinRules.isWeak("123456"))
        assertTrue(PinRules.isWeak("000000"))
        assertFalse(PinRules.isWeak("2580"))
        assertFalse(PinRules.isWeak("482915"))
    }

    @Test fun fiveWrongPinsLockForFiveMinutes() {
        var failures = 0
        var lock: Long? = null
        repeat(4) {
            val f = LockoutPolicy.onFailure(failures, 1_000L)
            failures = f.failures
            lock = f.lockUntil
            assertNull(lock)
        }
        val fifth = LockoutPolicy.onFailure(failures, 1_000L)
        assertEquals(5, fifth.failures)
        assertEquals(1_000L + 5 * 60_000L, fifth.lockUntil)
    }

    @Test fun repeatedLockoutsGrow() {
        val tenth = LockoutPolicy.onFailure(9, 0L)
        assertEquals(10 * 60_000L, tenth.lockUntil)
        val fifteenth = LockoutPolicy.onFailure(14, 0L)
        assertEquals(15 * 60_000L, fifteenth.lockUntil)
    }

    @Test fun remainingTimeAndAttemptsLeft() {
        assertEquals(0L, LockoutPolicy.remainingMs(null, 5L))
        assertEquals(0L, LockoutPolicy.remainingMs(100L, 500L))
        assertEquals(400L, LockoutPolicy.remainingMs(500L, 100L))
        assertEquals(5, LockoutPolicy.attemptsLeft(0))
        assertEquals(1, LockoutPolicy.attemptsLeft(4))
        assertEquals(5, LockoutPolicy.attemptsLeft(5))
    }
}
