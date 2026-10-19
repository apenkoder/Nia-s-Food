package com.apenkoder.niasfood

import com.apenkoder.niasfood.data.local.ExpenseEntity
import com.apenkoder.niasfood.data.local.OrderEntity
import com.apenkoder.niasfood.data.local.OrderLineEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.data.repo.Aggregator
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.ExpenseChannel
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.domain.Verification
import org.junit.Assert.assertEquals
import org.junit.Test

class AggregatorTest {
  private fun order(
    id: String,
    date: String,
    total: Long,
    type: OrderType = OrderType.DINE_IN,
    status: OrderStatus = OrderStatus.PAID,
    outlet: String = "o1",
    paidAt: Long? = 1L,
  ) = OrderEntity(
    id = id,
    outletId = outlet,
    token = id.hashCode(),
    businessDate = date,
    type = type,
    status = status,
    createdBy = "s",
    createdByName = "S",
    createdAt = 0L,
    note = "",
    discountCents = 0L,
    discountReason = null,
    discountApprovedBy = null,
    totalCents = total,
    shiftId = "sh",
    paidAt = paidAt,
    voidReason = null,
    voidedBy = null,
    voidedAt = null,
  )

  private fun pay(
    order: String,
    amount: Long,
    method: PaymentMethod,
    v: Verification,
  ) = PaymentEntity(
    id = "p-$order",
    orderId = order,
    outletId = "o1",
    shiftId = "sh",
    method = method,
    amountCents = amount,
    tenderedCents = null,
    reference = if (method == PaymentMethod.MOBILE_MONEY) "CODE$order" else null,
    verification = v,
    verifiedBy = null,
    flagReason = null,
    createdAt = 0L,
  )

  private fun line(
    order: String,
    name: String,
    qty: Int,
    total: Long,
  ) = OrderLineEntity("l-$order-$name", order, "i-$name", name, Variant.FULL, qty, total / qty, "", "", 0L, total)

  private fun expense(
    id: String,
    date: String,
    amount: Long,
    voided: Boolean = false,
  ) = ExpenseEntity(
    id = id,
    outletId = "o1",
    shiftId = "sh",
    businessDate = date,
    category = ExpenseCategory.INGREDIENTS,
    amountCents = amount,
    channel = ExpenseChannel.PETTY_CASH,
    description = "x",
    createdBy = "m",
    createdByName = "M",
    createdAt = 0L,
    voided = voided,
  )

  private val orders =
    listOf(
      order("a", "2026-10-14", 50_000L),
      order("b", "2026-10-14", 30_000L, OrderType.TAKEOUT),
      order("c", "2026-10-15", 20_000L),
      order("v", "2026-10-15", 99_000L, status = OrderStatus.VOIDED),
      order("draft", "2026-10-15", 5_000L, status = OrderStatus.VOIDED, paidAt = null),
    )
  private val payments =
    listOf(
      pay("a", 50_000L, PaymentMethod.CASH, Verification.NOT_REQUIRED),
      pay("b", 30_000L, PaymentMethod.MOBILE_MONEY, Verification.VERIFIED),
      pay("c", 20_000L, PaymentMethod.MOBILE_MONEY, Verification.UNVERIFIED),
      pay("v", 99_000L, PaymentMethod.CASH, Verification.NOT_REQUIRED),
    )
  private val lines = listOf(line("a", "Pilau", 2, 50_000L), line("b", "Pilau", 1, 30_000L), line("c", "Samosa", 5, 20_000L))
  private val expenses =
    listOf(expense("e1", "2026-10-14", 12_000L), expense("e2", "2026-10-15", 8_000L), expense("e3", "2026-10-15", 7_000L, voided = true))

  @Test fun grossAndPaymentSplitAlwaysAddUp() {
    val s = Aggregator.summarize(orders, payments, lines, expenses)
    assertEquals(100_000L, s.grossCents)
    assertEquals(s.grossCents, s.cashCents + s.mmVerifiedCents + s.mmUnverifiedCents)
    assertEquals(50_000L, s.cashCents)
    assertEquals(30_000L, s.mmVerifiedCents)
    assertEquals(20_000L, s.mmUnverifiedCents)
  }

  @Test fun voidedOrdersAreExcludedFromSalesButCounted() {
    val s = Aggregator.summarize(orders, payments, lines, expenses)
    assertEquals(3, s.tickets)
    assertEquals(1, s.voidedCount)
    assertEquals(99_000L, s.voidedCents)
  }

  @Test fun netCashflowIsSalesMinusLoggedExpenses() {
    val s = Aggregator.summarize(orders, payments, lines, expenses)
    assertEquals(20_000L, s.expensesCents)
    assertEquals(80_000L, s.netCashflowCents)
    assertEquals(100_000L / 3L, s.avgTicketCents)
  }

  @Test fun dineInAndTakeoutSplit() {
    val s = Aggregator.summarize(orders, payments, lines, expenses)
    assertEquals(2, s.dineInTickets)
    assertEquals(70_000L, s.dineInCents)
    assertEquals(1, s.takeoutTickets)
    assertEquals(30_000L, s.takeoutCents)
  }

  @Test fun topItemsRankByRevenue() {
    val s = Aggregator.summarize(orders, payments, lines, expenses)
    assertEquals("Pilau", s.topItems.first().name)
    assertEquals(3, s.topItems.first().qty)
    assertEquals(80_000L, s.topItems.first().revenueCents)
  }

  @Test fun perDayTotalsEqualTheRangeTotal() {
    val range = Aggregator.summarize(orders, payments, lines, expenses)
    val days = Aggregator.summarizeByDate(orders, payments, lines, expenses)
    assertEquals(setOf("2026-10-14", "2026-10-15"), days.keys)
    assertEquals(range.grossCents, days.values.sumOf { it.grossCents })
    assertEquals(range.expensesCents, days.values.sumOf { it.expensesCents })
    assertEquals(range.netCashflowCents, days.values.sumOf { it.netCashflowCents })
    assertEquals(range.tickets, days.values.sumOf { it.tickets })
  }

  @Test fun emptyInputGivesZeros() {
    val s = Aggregator.summarize(emptyList(), emptyList(), emptyList(), emptyList())
    assertEquals(0L, s.grossCents)
    assertEquals(0L, s.avgTicketCents)
    assertEquals(0L, s.netCashflowCents)
  }
}
