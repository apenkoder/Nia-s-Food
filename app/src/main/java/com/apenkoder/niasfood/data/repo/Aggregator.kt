package com.apenkoder.niasfood.data.repo

import com.apenkoder.niasfood.data.local.ExpenseEntity
import com.apenkoder.niasfood.data.local.OrderEntity
import com.apenkoder.niasfood.data.local.OrderLineEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Verification

data class TopItem(
  val name: String,
  val qty: Int,
  val revenueCents: Long,
)

/** Every number on Dashboard, Reports and the Z-report comes from this one calculation. */
data class Summary(
  val grossCents: Long = 0L,
  val expensesCents: Long = 0L,
  val tickets: Int = 0,
  val dineInTickets: Int = 0,
  val dineInCents: Long = 0L,
  val takeoutTickets: Int = 0,
  val takeoutCents: Long = 0L,
  val cashCents: Long = 0L,
  val mmVerifiedCents: Long = 0L,
  val mmUnverifiedCents: Long = 0L,
  val byOutlet: Map<String, Long> = emptyMap(),
  val topItems: List<TopItem> = emptyList(),
  val expensesByCategory: Map<ExpenseCategory, Long> = emptyMap(),
  val voidedCount: Int = 0,
  val voidedCents: Long = 0L,
) {
  /** Sales minus logged expenses. Stock cost is not included, so this is not profit. */
  val netCashflowCents: Long get() = grossCents - expensesCents
  val avgTicketCents: Long get() = if (tickets == 0) 0L else grossCents / tickets
  val avgDineInCents: Long get() = if (dineInTickets == 0) 0L else dineInCents / dineInTickets
  val avgTakeoutCents: Long get() = if (takeoutTickets == 0) 0L else takeoutCents / takeoutTickets
}

object Aggregator {
  fun summarize(
    orders: List<OrderEntity>,
    payments: List<PaymentEntity>,
    lines: List<OrderLineEntity>,
    expenses: List<ExpenseEntity>,
  ): Summary {
    val paid = orders.filter { it.status == OrderStatus.PAID }
    val paidIds = paid.map { it.id }.toHashSet()
    // Voided drafts that were never paid are not counted as voids.
    val voided = orders.filter { it.status == OrderStatus.VOIDED && it.paidAt != null }

    val dineIn = paid.filter { it.type == OrderType.DINE_IN }
    val takeout = paid.filter { it.type == OrderType.TAKEOUT }
    val pays = payments.filter { it.orderId in paidIds }
    val mm = pays.filter { it.method == PaymentMethod.MOBILE_MONEY }
    val liveExpenses = expenses.filter { !it.voided }

    val top =
      lines
        .filter { it.orderId in paidIds }
        .groupBy { it.name }
        .map { (name, group) -> TopItem(name, group.sumOf { it.qty }, group.sumOf { it.lineTotalCents }) }
        .sortedByDescending { it.revenueCents }
        .take(5)

    return Summary(
      grossCents = paid.sumOf { it.totalCents },
      expensesCents = liveExpenses.sumOf { it.amountCents },
      tickets = paid.size,
      dineInTickets = dineIn.size,
      dineInCents = dineIn.sumOf { it.totalCents },
      takeoutTickets = takeout.size,
      takeoutCents = takeout.sumOf { it.totalCents },
      cashCents = pays.filter { it.method == PaymentMethod.CASH }.sumOf { it.amountCents },
      mmVerifiedCents = mm.filter { it.verification == Verification.VERIFIED }.sumOf { it.amountCents },
      mmUnverifiedCents = mm.filter { it.verification != Verification.VERIFIED }.sumOf { it.amountCents },
      byOutlet = paid.groupBy { it.outletId }.mapValues { (_, g) -> g.sumOf { it.totalCents } },
      topItems = top,
      expensesByCategory = liveExpenses.groupBy { it.category }.mapValues { (_, g) -> g.sumOf { it.amountCents } },
      voidedCount = voided.size,
      voidedCents = voided.sumOf { it.totalCents },
    )
  }

  /** One [Summary] per business date present in the data. */
  fun summarizeByDate(
    orders: List<OrderEntity>,
    payments: List<PaymentEntity>,
    lines: List<OrderLineEntity>,
    expenses: List<ExpenseEntity>,
  ): Map<String, Summary> {
    val dateOfOrder = orders.associate { it.id to it.businessDate }
    val dates = (orders.map { it.businessDate } + expenses.map { it.businessDate }).toSortedSet()
    return dates.associateWith { date ->
      summarize(
        orders = orders.filter { it.businessDate == date },
        payments = payments.filter { dateOfOrder[it.orderId] == date },
        lines = lines.filter { dateOfOrder[it.orderId] == date },
        expenses = expenses.filter { it.businessDate == date },
      )
    }
  }
}
