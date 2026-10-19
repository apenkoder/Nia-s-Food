package com.apenkoder.niasfood.data.demo

import androidx.room.withTransaction
import com.apenkoder.niasfood.BuildConfig
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.data.local.AddOnEntity
import com.apenkoder.niasfood.data.local.CategoryEntity
import com.apenkoder.niasfood.data.local.ExpenseEntity
import com.apenkoder.niasfood.data.local.MenuItemEntity
import com.apenkoder.niasfood.data.local.OrderEntity
import com.apenkoder.niasfood.data.local.OrderLineEntity
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.data.local.ShiftEntity
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.data.security.PinHasher
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.ExpenseChannel
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.ShiftStatus
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.domain.Verification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * DEBUG ONLY. Seeds 2 outlets, 6 staff, a sample menu and 7 days of consistent orders, payments,
 * expenses and shifts. Prices are placeholders. Demo PINs are listed in README.md and never shown in the UI.
 */
object DemoSeeder {
  private class Spec(
    val id: String,
    val outletId: String?,
    val name: String,
    val role: Role,
    val pin: String,
  )

  private class MenuSpec(
    val id: String,
    val catId: String,
    val name: String,
    val desc: String,
    val price: Long,
    val half: Long?,
  )

  suspend fun seedIfNeeded(container: AppContainer): Boolean {
    if (!BuildConfig.DEMO_SEED) return false
    if (container.db.outletDao().count() > 0) return false
    withContext(Dispatchers.Default) { seed(container) }
    return true
  }

  private suspend fun seed(c: AppContainer) {
    val db = c.db
    val now = c.clock.nowMillis()
    val rnd = Random(42)

    val bogani = OutletEntity("outlet-bogani", "Bogani Road", "Karen", 360, 1320)
    val jogoo = OutletEntity("outlet-jogoo", "Jogoo Road", "Eastlands", 360, 1320)
    val outlets = listOf(bogani, jogoo)

    val specs =
      listOf(
        Spec("staff-owner", null, "Nia", Role.OWNER, "482915"),
        Spec("staff-amina", bogani.id, "Amina Hassan", Role.MANAGER, "603817"),
        Spec("staff-brian", jogoo.id, "Brian Otieno", Role.MANAGER, "715204"),
        Spec("staff-akinyi", bogani.id, "Akinyi Odhiambo", Role.SERVER, "2580"),
        Spec("staff-kevin", bogani.id, "Kevin Mutua", Role.SERVER, "4096"),
        Spec("staff-faith", jogoo.id, "Faith Wanjiru", Role.SERVER, "7391"),
      )
    val staff = specs.map { StaffEntity(it.id, it.outletId, it.name, it.role, PinHasher.hash(it.pin)) }
    val managerOf = mapOf(bogani.id to ("staff-amina" to "Amina Hassan"), jogoo.id to ("staff-brian" to "Brian Otieno"))
    val serversOf =
      mapOf(
        bogani.id to listOf("staff-akinyi" to "Akinyi Odhiambo", "staff-kevin" to "Kevin Mutua"),
        jogoo.id to listOf("staff-faith" to "Faith Wanjiru"),
      )

    val categories =
      listOf(
        CategoryEntity("cat-rice", "Rice & Mains", 1),
        CategoryEntity("cat-stews", "Stews & Sides", 2),
        CategoryEntity("cat-snacks", "Snacks", 3),
        CategoryEntity("cat-drinks", "Drinks", 4),
      )
    val menuSpecs =
      listOf(
        MenuSpec("item-pilau", "cat-rice", "Pilau", "Spiced rice with beef", 250, 150),
        MenuSpec("item-fried-rice", "cat-rice", "Fried Rice", "Pan-tossed rice with vegetables", 200, 130),
        MenuSpec("item-white-rice", "cat-rice", "White Rice", "Steamed white rice", 100, 60),
        MenuSpec("item-beans", "cat-stews", "Beans Stew", "Beans in tomato stew", 120, null),
        MenuSpec("item-meat-stew", "cat-stews", "Meat Stew", "Beef stew", 220, null),
        MenuSpec("item-samosa", "cat-snacks", "Samosa", "Beef samosa", 40, null),
        MenuSpec("item-sausage", "cat-snacks", "Sausage in Bun", "Grilled sausage in a bun", 80, null),
        MenuSpec("item-cake", "cat-snacks", "Cake Slice", "Slice of plain cake", 100, null),
        MenuSpec("item-coke", "cat-drinks", "Coca-Cola", "300ml bottle", 70, null),
        MenuSpec("item-fanta", "cat-drinks", "Fanta", "300ml bottle", 70, null),
      )
    val menu = menuSpecs.map { MenuItemEntity(it.id, it.catId, it.name, it.desc, it.price * 100L, it.half?.let { h -> h * 100L }) }
    val addOns =
      listOf(
        AddOnEntity("addon-stew", "Extra stew", 5000),
        AddOnEntity("addon-kachumbari", "Kachumbari", 3000),
        AddOnEntity("addon-chapati", "Chapati", 3000),
      )

    val orders = ArrayList<OrderEntity>()
    val lines = ArrayList<OrderLineEntity>()
    val payments = ArrayList<PaymentEntity>()
    val expenses = ArrayList<ExpenseEntity>()
    val shifts = ArrayList<ShiftEntity>()
    val usedCodes = HashSet<String>()
    val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"

    fun newCode(): String {
      while (true) {
        val code = String(CharArray(10) { codeChars[rnd.nextInt(codeChars.length)] })
        if (usedCodes.add(code)) return code
      }
    }

    val today = NairobiTime.businessDate(now, 360)
    val floatCents = 500_000L
    val expenseText =
      mapOf(
        ExpenseCategory.INGREDIENTS to "Rice, spices and vegetables",
        ExpenseCategory.GAS_CHARCOAL to "Gas refill",
        ExpenseCategory.WAGES to "Casual helper wages",
        ExpenseCategory.RENT to "Rent contribution",
        ExpenseCategory.UTILITIES to "Water and power tokens",
        ExpenseCategory.TRANSPORT to "Delivery transport",
        ExpenseCategory.OTHER to "Cleaning supplies",
      )

    for (outlet in outlets) {
      val (managerId, managerName) = managerOf.getValue(outlet.id)
      val servers = serversOf.getValue(outlet.id)
      for (offset in -6..0) {
        val date = NairobiTime.shiftDate(today, offset.toLong())
        val isToday = offset == 0
        val shiftId = "shift-${outlet.id}-$date"
        val openAt = minOf(NairobiTime.epochAt(date, 6, 30), now - 60_000L)
        val start = NairobiTime.epochAt(date, 7, 0)
        val end = if (isToday) minOf(now - 5 * 60_000L, NairobiTime.epochAt(date, 21, 0)) else NairobiTime.epochAt(date, 21, 0)
        val count =
          if (end > start + 60_000L) {
            if (outlet.id == bogani.id) 16 + rnd.nextInt(9) else 9 + rnd.nextInt(7)
          } else {
            0
          }
        val times = List(count) { start + rnd.nextLong((end - start).coerceAtLeast(1L)) }.sorted()

        var cashSales = 0L
        var discountGiven = false
        for ((index, createdAt) in times.withIndex()) {
          val n = index + 1
          val orderId = "order-${outlet.id}-$date-$n"
          val picks = menuSpecs.shuffled(rnd).take(1 + rnd.nextInt(3))
          val orderLines =
            picks.map { m ->
              val halfPrice = m.half
              val useHalf = halfPrice != null && rnd.nextInt(100) < 20
              val unit = (if (useHalf && halfPrice != null) halfPrice else m.price) * 100L
              val qty = 1 + rnd.nextInt(2)
              val addOn = if (rnd.nextInt(100) < 15) addOns[rnd.nextInt(addOns.size)] else null
              val addCents = addOn?.priceCents ?: 0L
              OrderLineEntity(
                id = "line-$orderId-${m.id}",
                orderId = orderId,
                itemId = m.id,
                name = m.name,
                variant = if (useHalf) Variant.HALF else Variant.FULL,
                qty = qty,
                unitCents = unit,
                addOnIds = addOn?.id.orEmpty(),
                addOnsText = addOn?.name.orEmpty(),
                addOnsCents = addCents,
                lineTotalCents = (unit + addCents) * qty,
              )
            }
          val subtotal = orderLines.sumOf { it.lineTotalCents }
          val discount = if (offset == -2 && outlet.id == bogani.id && !discountGiven && subtotal > 10_000L) 5_000L else 0L
          if (discount > 0L) discountGiven = true
          val total = subtotal - discount
          val voided = offset == -4 && outlet.id == bogani.id && n == 2
          val (serverId, serverName) = servers[rnd.nextInt(servers.size)]
          orders.add(
            OrderEntity(
              id = orderId,
              outletId = outlet.id,
              token = n,
              businessDate = date,
              type = if (rnd.nextInt(100) < 58) OrderType.DINE_IN else OrderType.TAKEOUT,
              status = if (voided) OrderStatus.VOIDED else OrderStatus.PAID,
              createdBy = serverId,
              createdByName = serverName,
              createdAt = createdAt,
              note = "",
              discountCents = discount,
              discountReason = if (discount > 0L) "Regular customer" else null,
              discountApprovedBy = if (discount > 0L) managerId else null,
              totalCents = total,
              shiftId = shiftId,
              paidAt = createdAt + 90_000L,
              voidReason = if (voided) "Wrong order entered" else null,
              voidedBy = if (voided) managerId else null,
              voidedAt = if (voided) createdAt + 300_000L else null,
            ),
          )
          lines.addAll(orderLines)

          val mobile = rnd.nextInt(100) < 70
          val flagged = offset == -3 && outlet.id == jogoo.id && n == 3 && mobile
          val recent = isToday && createdAt > now - 2 * 60 * 60_000L
          payments.add(
            PaymentEntity(
              id = "pay-$orderId",
              orderId = orderId,
              outletId = outlet.id,
              shiftId = shiftId,
              method = if (mobile) PaymentMethod.MOBILE_MONEY else PaymentMethod.CASH,
              amountCents = total,
              tenderedCents = if (mobile) null else ((total + 4_999L) / 5_000L) * 5_000L,
              reference = if (mobile) newCode() else null,
              verification =
                when {
                  !mobile -> Verification.NOT_REQUIRED
                  flagged -> Verification.FLAGGED
                  recent -> Verification.UNVERIFIED
                  else -> Verification.VERIFIED
                },
              verifiedBy = if (mobile && !recent) managerId else null,
              flagReason = if (flagged) "Not found on till statement" else null,
              createdAt = createdAt + 90_000L,
            ),
          )
          if (!mobile && !voided) cashSales += total
        }

        if (isToday && outlet.id == bogani.id && count > 0) {
          val held = menuSpecs.shuffled(rnd).take(2)
          val heldId = "order-${outlet.id}-$date-held"
          val heldLines =
            held.map { m ->
              OrderLineEntity(
                id = "line-$heldId-${m.id}",
                orderId = heldId,
                itemId = m.id,
                name = m.name,
                variant = Variant.FULL,
                qty = 1,
                unitCents = m.price * 100L,
                addOnIds = "",
                addOnsText = "",
                addOnsCents = 0L,
                lineTotalCents = m.price * 100L,
              )
            }
          orders.add(
            OrderEntity(
              id = heldId,
              outletId = outlet.id,
              token = count + 1,
              businessDate = date,
              type = OrderType.DINE_IN,
              status = OrderStatus.HELD,
              createdBy = "staff-akinyi",
              createdByName = "Akinyi Odhiambo",
              createdAt = now - 10 * 60_000L,
              note = "Table by the window",
              discountCents = 0L,
              discountReason = null,
              discountApprovedBy = null,
              totalCents = heldLines.sumOf { it.lineTotalCents },
              shiftId = null,
              paidAt = null,
              voidReason = null,
              voidedBy = null,
              voidedAt = null,
            ),
          )
          lines.addAll(heldLines)
        }

        var cashOut = 0L
        val categoriesAll = ExpenseCategory.values()
        repeat(2 + rnd.nextInt(3)) { i ->
          val category = categoriesAll[rnd.nextInt(categoriesAll.size)]
          val channel = ExpenseChannel.values()[rnd.nextInt(ExpenseChannel.values().size)]
          val amount = (8 + rnd.nextInt(53)) * 10_000L
          val at = minOf(openAt + 600_000L * (i + 1), now - 60_000L)
          if (channel == ExpenseChannel.CASH_FROM_TILL) cashOut += amount
          expenses.add(
            ExpenseEntity(
              id = "exp-${outlet.id}-$date-$i",
              outletId = outlet.id,
              shiftId = shiftId,
              businessDate = date,
              category = category,
              amountCents = amount,
              channel = channel,
              description = expenseText.getValue(category),
              createdBy = managerId,
              createdByName = managerName,
              createdAt = at,
            ),
          )
        }

        val expected = floatCents + cashSales - cashOut
        val shortBy = if (offset == -3 && outlet.id == bogani.id) 20_000L else 0L
        val number = offset + 7
        shifts.add(
          ShiftEntity(
            id = shiftId,
            outletId = outlet.id,
            businessDate = date,
            number = number,
            openedBy = managerId,
            openedByName = managerName,
            openedAt = openAt,
            openingFloatCents = floatCents,
            status = if (isToday) ShiftStatus.OPEN else ShiftStatus.CLOSED,
            closedBy = if (isToday) null else managerId,
            closedByName = if (isToday) null else managerName,
            closedAt = if (isToday) null else NairobiTime.epochAt(date, 22, 0),
            expectedCashCents = if (isToday) null else expected,
            countedCashCents = if (isToday) null else expected - shortBy,
            varianceCents = if (isToday) null else -shortBy,
            varianceReason = if (!isToday && shortBy > 0L) "Change given out and not recorded" else null,
            zNumber = if (isToday) null else number,
          ),
        )
      }
    }

    db.withTransaction {
      db.outletDao().upsertAll(outlets)
      db.staffDao().upsertAll(staff)
      db.menuDao().upsertCategories(categories)
      db.menuDao().upsertItems(menu)
      db.menuDao().upsertAddOns(addOns)
      db.shiftDao().insertAll(shifts)
      db.orderDao().insertOrders(orders)
      db.orderDao().insertLinesReplace(lines)
      db.paymentDao().insertAll(payments)
      db.expenseDao().insertAll(expenses)
    }
  }
}
