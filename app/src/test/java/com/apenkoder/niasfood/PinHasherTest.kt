package com.apenkoder.niasfood

import com.apenkoder.niasfood.data.security.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun hashVerifiesAndRejectsWrongPin() {
        val hash = PinHasher.hash("482915")
        assertTrue(PinHasher.verify("482915", hash))
        assertFalse(PinHasher.verify("482916", hash))
    }

    @Test fun isRealBcryptWithCost12AndRandomSalt() {
        val a = PinHasher.hash("2580")
        val b = PinHasher.hash("2580")
        assertTrue(a.startsWith("\$2a\$12\$") || a.startsWith("\$2b\$12\$") || a.startsWith("\$2y\$12\$"))
        assertNotEquals(a, b)
        assertFalse(a.contains("2580"))
    }
}
