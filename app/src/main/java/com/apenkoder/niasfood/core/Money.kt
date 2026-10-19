package com.apenkoder.niasfood.core

import kotlin.math.abs

/**
 * Money in KES stored as whole cents (Long). Never use Float or Double for money.
 */
@JvmInline
value class Money(
  val cents: Long,
) : Comparable<Money> {
  operator fun plus(other: Money): Money = Money(cents + other.cents)

  operator fun minus(other: Money): Money = Money(cents - other.cents)

  operator fun times(quantity: Int): Money = Money(cents * quantity)

  override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

  /** "KES 1,020" (whole shillings) or "KES 1,020.50" when cents exist. */
  fun format(): String = MoneyFormatter.format(cents)

  companion object {
    val ZERO = Money(0L)

    fun ofShillings(shillings: Long): Money = Money(shillings * 100L)

    /** Parses "1,250" or "1250.5". Returns null when empty, negative, malformed or too large. */
    fun parse(text: String): Money? {
      val cleaned = text.trim().replace(",", "")
      if (cleaned.isEmpty()) return null
      val parts = cleaned.split(".")
      if (parts.size > 2) return null
      val whole = parts[0]
      val fraction = if (parts.size == 2) parts[1] else ""
      if (whole.isEmpty() && fraction.isEmpty()) return null
      if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
      if (fraction.length > 2 || whole.length > 12) return null
      val w = if (whole.isEmpty()) 0L else whole.toLong()
      val f =
        when (fraction.length) {
          0 -> 0L
          1 -> fraction.toLong() * 10L
          else -> fraction.toLong()
        }
      return Money(w * 100L + f)
    }
  }
}

object MoneyFormatter {
  fun format(cents: Long): String {
    val negative = cents < 0
    val absCents = abs(cents)
    val shillings = absCents / 100L
    val rest = absCents % 100L
    val grouped = group(shillings)
    val body = if (rest == 0L) grouped else grouped + "." + rest.toString().padStart(2, '0')
    return (if (negative) "-" else "") + "KES " + body
  }

  private fun group(value: Long): String {
    val digits = value.toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
      if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
      sb.append(digits[i])
    }
    return sb.toString()
  }
}
