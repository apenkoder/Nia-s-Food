package com.apenkoder.niasfood.domain

enum class Role(
  val pinLength: Int,
) {
  SERVER(4),
  MANAGER(6),
  OWNER(6),
}

enum class OrderType { DINE_IN, TAKEOUT }

enum class OrderStatus { HELD, PAID, VOIDED }

enum class PaymentMethod { CASH, MOBILE_MONEY }

enum class Verification { NOT_REQUIRED, UNVERIFIED, VERIFIED, FLAGGED }

enum class Variant { FULL, HALF }

enum class ShiftStatus { OPEN, CLOSED }

enum class ExpenseCategory { INGREDIENTS, GAS_CHARCOAL, WAGES, RENT, UTILITIES, TRANSPORT, OTHER }

enum class ExpenseChannel { MOBILE_MONEY_TILL, PETTY_CASH, CASH_FROM_TILL }

/** The signed-in person. Owner has no fixed outlet. */
data class Session(
  val staffId: String,
  val name: String,
  val role: Role,
  val outletId: String?,
)
