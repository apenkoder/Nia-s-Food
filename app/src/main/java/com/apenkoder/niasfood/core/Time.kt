package com.apenkoder.niasfood.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

interface AppClock {
  fun nowMillis(): Long
}

object SystemAppClock : AppClock {
  override fun nowMillis(): Long = System.currentTimeMillis()
}

/** All business time is Africa/Nairobi. A business day starts at the outlet's opening time. */
object NairobiTime {
  val zone: ZoneId = ZoneId.of("Africa/Nairobi")

  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
  private val shortDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

  /** ISO date (yyyy-MM-dd) of the business day an instant belongs to. */
  fun businessDate(
    epochMillis: Long,
    openMinutes: Int,
  ): String {
    val shifted = Instant.ofEpochMilli(epochMillis).atZone(zone).minusMinutes(openMinutes.toLong())
    return shifted.toLocalDate().toString()
  }

  fun shiftDate(
    isoDate: String,
    days: Long,
  ): String = LocalDate.parse(isoDate).plusDays(days).toString()

  fun shortDate(isoDate: String): String = LocalDate.parse(isoDate).format(shortDateFormat)

  fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormat)

  fun epochAt(
    isoDate: String,
    hour: Int,
    minute: Int,
  ): Long =
    LocalDate
      .parse(isoDate)
      .atTime(hour, minute)
      .atZone(zone)
      .toInstant()
      .toEpochMilli()

  /** "06:00" -> 360. Returns null when malformed. */
  fun parseMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
  }

  fun minutesToText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
  }
}
