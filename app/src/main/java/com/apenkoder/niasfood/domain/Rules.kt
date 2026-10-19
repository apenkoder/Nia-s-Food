package com.apenkoder.niasfood.domain

import kotlin.math.max

/** M-Pesa style transaction codes: 10 characters, uppercase letters and digits. */
object MobileMoneyCode {
    private val pattern = Regex("^[A-Z0-9]{10}$")

    fun normalize(raw: String): String = raw.trim().uppercase()

    fun isValid(raw: String): Boolean = pattern.matches(normalize(raw))
}

object PinRules {
    fun isWellFormed(pin: String, role: Role): Boolean =
        pin.length == role.pinLength && pin.all { it in '0'..'9' }

    /** Rejects 1111, 123456, 654321 and similar. */
    fun isWeak(pin: String): Boolean {
        if (pin.isEmpty()) return true
        if (pin.all { it == pin[0] }) return true
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        return ascending || descending
    }
}

/**
 * 5 wrong PINs lock the profile for 5 minutes. Every further 5 wrong PINs lock for
 * another 5 minutes longer (10th failure = 10 min, 15th = 15 min). Success resets the counter.
 */
object LockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val BASE_LOCK_MS = 5L * 60L * 1000L

    data class Failure(val failures: Int, val lockUntil: Long?)

    fun onFailure(previousFailures: Int, nowMillis: Long): Failure {
        val failures = previousFailures + 1
        val lockUntil = if (failures % MAX_ATTEMPTS == 0) {
            nowMillis + BASE_LOCK_MS * (failures / MAX_ATTEMPTS)
        } else {
            null
        }
        return Failure(failures, lockUntil)
    }

    fun remainingMs(lockUntil: Long?, nowMillis: Long): Long =
        if (lockUntil == null) 0L else max(0L, lockUntil - nowMillis)

    fun attemptsLeft(failures: Int): Int = MAX_ATTEMPTS - (failures % MAX_ATTEMPTS)
}
