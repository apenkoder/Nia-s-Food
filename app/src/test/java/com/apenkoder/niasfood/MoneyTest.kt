package com.apenkoder.niasfood

import com.apenkoder.niasfood.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
  @Test fun formatsWholeShillings() = assertEquals("KES 1,020", Money.ofShillings(1020).format())

  @Test fun formatsLargeAndSmall() {
    assertEquals("KES 0", Money.ZERO.format())
    assertEquals("KES 999", Money.ofShillings(999).format())
    assertEquals("KES 1,234,567", Money.ofShillings(1_234_567).format())
  }

  @Test fun formatsCentsAndNegatives() {
    assertEquals("KES 12.50", Money(1250).format())
    assertEquals("KES 12.05", Money(1205).format())
    assertEquals("-KES 200", Money(-20000).format())
  }

  @Test fun arithmeticStaysInCents() {
    val total = Money.ofShillings(250) * 2 + Money.ofShillings(100) - Money(50)
    assertEquals(59950L, total.cents)
  }

  @Test fun parsesUserInput() {
    assertEquals(125000L, Money.parse("1,250")?.cents)
    assertEquals(125050L, Money.parse("1250.5")?.cents)
    assertEquals(1L, Money.parse(".01")?.cents)
  }

  @Test fun rejectsBadInput() {
    assertNull(Money.parse(""))
    assertNull(Money.parse("abc"))
    assertNull(Money.parse("-5"))
    assertNull(Money.parse("1.234"))
    assertNull(Money.parse("1.2.3"))
    assertNull(Money.parse("9999999999999"))
  }
}
