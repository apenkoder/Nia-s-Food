package com.apenkoder.niasfood

import com.apenkoder.niasfood.core.NairobiTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NairobiTimeTest {
  private val open = 360 // 06:00

  @Test fun beforeOpeningBelongsToPreviousBusinessDay() {
    val twoAm = NairobiTime.epochAt("2026-10-15", 2, 0)
    assertEquals("2026-10-14", NairobiTime.businessDate(twoAm, open))
  }

  @Test fun atAndAfterOpeningBelongsToTheSameDay() {
    assertEquals("2026-10-15", NairobiTime.businessDate(NairobiTime.epochAt("2026-10-15", 6, 0), open))
    assertEquals("2026-10-15", NairobiTime.businessDate(NairobiTime.epochAt("2026-10-15", 21, 59), open))
  }

  @Test fun lateNightStaysOnTheDayItStartedWhenOpeningIsZero() {
    assertEquals("2026-10-15", NairobiTime.businessDate(NairobiTime.epochAt("2026-10-15", 23, 30), 0))
  }

  @Test fun shiftDateAndParsing() {
    assertEquals("2026-10-09", NairobiTime.shiftDate("2026-10-15", -6))
    assertEquals(360, NairobiTime.parseMinutes("06:00"))
    assertEquals(1320, NairobiTime.parseMinutes("22:00"))
    assertNull(NairobiTime.parseMinutes("25:00"))
    assertNull(NairobiTime.parseMinutes("6"))
    assertEquals("06:30", NairobiTime.minutesToText(390))
  }
}
