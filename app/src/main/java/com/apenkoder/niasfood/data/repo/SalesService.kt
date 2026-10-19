package com.apenkoder.niasfood.data.repo

import androidx.room.withTransaction
import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.data.local.ExpenseEntity
import com.apenkoder.niasfood.data.local.NiasDatabase
import com.apenkoder.niasfood.data.local.OrderEntity
import com.apenkoder.niasfood.data.local.OrderLineEntity
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.data.local.ShiftEntity
import com.apenkoder.niasfood.data.local.SyncOutboxEntity
import com.apenkoder.niasfood.data.security.ApprovalGrant
import com.apenkoder.niasfood.data.security.SessionManager
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.ExpenseChannel
import com.apenkoder.niasfood.domain.MobileMoneyCode
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.ShiftStatus
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.domain.Verification
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** A cart line as the UI knows it. Prices are NOT sent: the service resolves them from the menu. */
class CartLine(
  val itemId: String,
  val variant: Variant,
  val qty: Int,
  val addOnIds: List<String>,
)

class PaymentInput(
  val method: PaymentMethod,
  val tenderedCents: Long?,
  val reference: String?,
)

class CheckoutDiscount(
  val cents: Long,
  val reason: String?,
  val approval: ApprovalGrant?,
)

class CheckoutDetails(
  val type: OrderType,
  val note: String,
)

class CheckoutRequest(
  val outletId: String,
  val lines: List<CartLine>,
  val details: CheckoutDetails,
  val discount: CheckoutDiscount?,
  val heldOrderId: String?,
  /** null = hold the order unpaid */
  val payment: PaymentInput?,
)

class OrderReceipt(
  val order: OrderEntity,
  val lines: List<OrderLineEntity>,
  val payment: PaymentEntity?,
  val changeCents: Long,
  val outletName: String,
)

class ShiftOverview(
  val shift: ShiftEntity,
  val cashSalesCents: Long,
  val cashExpensesCents: Long,
  val expectedCashCents: Long,
  val mobileMoney: List<PaymentEntity>,
)

class SalesService(
  private val db: NiasDatabase,
  private val clock: AppClock,
  private val audit: Audit,
  private val sessions: SessionManager,
) {
  private fun requirePermission(
    s: Session,
    p: Permission,
  ) {
    if (!Permissions.has(s.role, p)) throw AppException(AppError.NOT_ALLOWED)
  }

  private fun requireOutlet(
    s: Session,
    outletId: String,
  ) {
    if (!Permissions.canActInOutlet(s, outletId)) throw AppException(AppError.NOT_ALLOWED)
  }

  private suspend fun outletOrFail(id: String): OutletEntity = db.outletDao().byId(id) ?: throw AppException(AppError.NOT_FOUND)

  private suspend fun queue(
    type: String,
    id: String,
    op: String,
  ) {
    db.syncOutboxDao().insert(SyncOutboxEntity(entityType = type, entityId = id, op = op, createdAt = clock.nowMillis()))
  }

  // ---------- observation ----------

  fun observeOpenShift(outletId: String): Flow<ShiftEntity?> = db.shiftDao().observeOpenFor(outletId)

  fun observeHeld(outletId: String): Flow<List<OrderEntity>> = db.orderDao().observeHeld(outletId)

  fun observeDay(
    outletId: String,
    date: String,
  ): Flow<List<OrderEntity>> = db.orderDao().observeDay(outletId, date)

  fun observeExpenses(outletId: String?): Flow<List<ExpenseEntity>> = db.expenseDao().observeRecent(outletId)

  suspend fun currentShift(outletId: String): ShiftEntity? = db.shiftDao().openFor(outletId)

  suspend fun linesFor(orderId: String): List<OrderLineEntity> = db.orderDao().linesFor(orderId)

  suspend fun paymentsFor(orderId: String): List<PaymentEntity> = db.paymentDao().forOrder(orderId)

  suspend fun order(orderId: String): OrderEntity? = db.orderDao().byId(orderId)

  // ---------- orders ----------

  /** Pays an order, or holds it when [CheckoutRequest.payment] is null. Runs in one transaction. */
  suspend fun submit(req: CheckoutRequest): OrderReceipt =
    db.withTransaction {
      val s = sessions.requireSession()
      requirePermission(s, Permission.TAKE_PAYMENT)
      requireOutlet(s, req.outletId)
      if (req.lines.isEmpty() || req.lines.any { it.qty <= 0 || it.qty > MAX_QTY }) {
        throw AppException(AppError.EMPTY_CART)
      }
      val outlet = outletOrFail(req.outletId)
      val now = clock.nowMillis()

      val held = req.heldOrderId?.let { db.orderDao().byId(it) }
      if (req.heldOrderId != null &&
        (held == null || held.status != OrderStatus.HELD || held.outletId != req.outletId)
      ) {
        throw AppException(AppError.NOT_FOUND)
      }
      val orderId = held?.id ?: UUID.randomUUID().toString()

      // Prices always come from the menu, never from the UI.
      val menu = db.menuDao()
      val addOnIds = req.lines.flatMap { it.addOnIds }.distinct()
      val addOnMap = if (addOnIds.isEmpty()) emptyMap() else menu.addOnsByIds(addOnIds).associateBy { it.id }
      val lineEntities = ArrayList<OrderLineEntity>()
      for (l in req.lines) {
        val item = menu.itemById(l.itemId) ?: throw AppException(AppError.NOT_FOUND)
        if (!item.active || item.soldOut) throw AppException(AppError.SOLD_OUT, item.name)
        val unit =
          when (l.variant) {
            Variant.FULL -> item.priceCents
            Variant.HALF -> item.halfPriceCents ?: throw AppException(AppError.NOT_FOUND)
          }
        if (l.addOnIds.distinct().size != l.addOnIds.size) throw AppException(AppError.AMOUNT_INVALID)
        val chosen =
          l.addOnIds.map { id ->
            addOnMap[id]?.takeIf { it.active } ?: throw AppException(AppError.NOT_FOUND)
          }
        val addCents = chosen.sumOf { it.priceCents }
        lineEntities.add(
          OrderLineEntity(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            itemId = item.id,
            name = item.name,
            variant = l.variant,
            qty = l.qty,
            unitCents = unit,
            addOnIds = l.addOnIds.joinToString(","),
            addOnsText = chosen.joinToString(", ") { it.name },
            addOnsCents = addCents,
            lineTotalCents = (unit + addCents) * l.qty,
          ),
        )
      }
      val subtotal = lineEntities.sumOf { it.lineTotalCents }

      var discount = 0L
      var discountReason: String? = null
      var approverId: String? = null
      req.discount?.let { requestedDiscount ->
        val reason = requestedDiscount.reason?.trim().orEmpty()
        if (reason.isEmpty()) throw AppException(AppError.REASON_REQUIRED)
        if (requestedDiscount.cents <= 0L || requestedDiscount.cents >= subtotal) {
          throw AppException(AppError.DISCOUNT_INVALID)
        }
        if (Permissions.has(s.role, Permission.APPLY_DISCOUNT)) {
          approverId = s.staffId
        } else {
          val approvedById =
            sessions.consumeApproval(requestedDiscount.approval, req.outletId, Permission.APPLY_DISCOUNT)
              ?: throw AppException(AppError.APPROVAL_REQUIRED)
          val approver = db.staffDao().byId(approvedById) ?: throw AppException(AppError.APPROVAL_REQUIRED)
          if (!approver.active ||
            !Permissions.canApprove(approver.role, approver.outletId, req.outletId, Permission.APPLY_DISCOUNT)
          ) {
            throw AppException(AppError.APPROVAL_REQUIRED)
          }
          approverId = approver.id
        }
        discount = requestedDiscount.cents
        discountReason = reason
      }
      val total = subtotal - discount
      if (total <= 0L) throw AppException(AppError.AMOUNT_INVALID)

      val pay = req.payment
      var shiftId: String? = null
      var reference: String? = null
      if (pay != null) {
        val shift = db.shiftDao().openFor(req.outletId) ?: throw AppException(AppError.SHIFT_CLOSED)
        shiftId = shift.id
        when (pay.method) {
          PaymentMethod.CASH -> {
            val tendered = pay.tenderedCents ?: throw AppException(AppError.CASH_SHORT)
            if (tendered < total) throw AppException(AppError.CASH_SHORT)
          }
          PaymentMethod.MOBILE_MONEY -> {
            val code = MobileMoneyCode.normalize(pay.reference.orEmpty())
            if (!MobileMoneyCode.isValid(code)) throw AppException(AppError.BAD_MM_CODE)
            if (db.paymentDao().referenceCount(req.outletId, code) > 0) {
              throw AppException(AppError.DUPLICATE_MM_CODE)
            }
            reference = code
          }
        }
      }

      val date = NairobiTime.businessDate(now, outlet.openMinutes)
      val token =
        if (held != null && held.businessDate == date) {
          held.token
        } else {
          db.orderDao().maxToken(req.outletId, date) + 1
        }

      val order =
        OrderEntity(
          id = orderId,
          outletId = req.outletId,
          token = token,
          businessDate = date,
          type = req.details.type,
          status = if (pay != null) OrderStatus.PAID else OrderStatus.HELD,
          createdBy = held?.createdBy ?: s.staffId,
          createdByName = held?.createdByName ?: s.name,
          createdAt = held?.createdAt ?: now,
          note = req.details.note.trim(),
          discountCents = discount,
          discountReason = discountReason,
          discountApprovedBy = approverId,
          totalCents = total,
          shiftId = shiftId,
          paidAt = if (pay != null) now else null,
          voidReason = null,
          voidedBy = null,
          voidedAt = null,
        )
      if (held != null) {
        db.orderDao().deleteLines(held.id)
        db.orderDao().updateOrder(order)
      } else {
        db.orderDao().insertOrder(order)
      }
      db.orderDao().insertLines(lineEntities)

      var payment: PaymentEntity? = null
      if (pay != null) {
        payment =
          PaymentEntity(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            outletId = req.outletId,
            shiftId = shiftId,
            method = pay.method,
            amountCents = total,
            tenderedCents = if (pay.method == PaymentMethod.CASH) pay.tenderedCents else null,
            reference = reference,
            verification = if (pay.method == PaymentMethod.MOBILE_MONEY) Verification.UNVERIFIED else Verification.NOT_REQUIRED,
            verifiedBy = null,
            flagReason = null,
            createdAt = now,
          )
        db.paymentDao().insert(payment)
      }
      if (discount > 0L) audit.log(s, "discount_applied", "order", orderId, "$discount by $approverId: $discountReason", req.outletId)
      audit.log(s, if (pay != null) "order_paid" else "order_held", "order", orderId, null, req.outletId)
      queue("order", orderId, "upsert")

      val change = if (pay != null && pay.method == PaymentMethod.CASH) (pay.tenderedCents ?: total) - total else 0L
      OrderReceipt(order, lineEntities, payment, change, outlet.name)
    }

  /** Voids an order. Servers need a one-use manager/owner PIN approval. */
  suspend fun voidOrder(
    orderId: String,
    reason: String,
    approval: ApprovalGrant?,
  ) = db.withTransaction {
    val s = sessions.requireSession()
    val cleanReason = reason.trim()
    if (cleanReason.isEmpty()) throw AppException(AppError.REASON_REQUIRED)
    val order = db.orderDao().byId(orderId) ?: throw AppException(AppError.NOT_FOUND)
    requireOutlet(s, order.outletId)
    val approver: String =
      if (Permissions.has(s.role, Permission.VOID_ORDER)) {
        s.staffId
      } else {
        val approvedBy =
          sessions.consumeApproval(approval, order.outletId, Permission.VOID_ORDER)
            ?: throw AppException(AppError.APPROVAL_REQUIRED)
        val a = db.staffDao().byId(approvedBy) ?: throw AppException(AppError.APPROVAL_REQUIRED)
        if (!a.active || !Permissions.canApprove(a.role, a.outletId, order.outletId, Permission.VOID_ORDER)) {
          throw AppException(AppError.APPROVAL_REQUIRED)
        }
        a.id
      }
    if (order.status == OrderStatus.VOIDED) throw AppException(AppError.ALREADY_VOIDED)
    if (order.status == OrderStatus.PAID) {
      val shift = order.shiftId?.let { db.shiftDao().byId(it) }
      if (shift != null && shift.status == ShiftStatus.CLOSED) throw AppException(AppError.ORDER_CLOSED_SHIFT)
    }
    val now = clock.nowMillis()
    db.orderDao().updateOrder(order.copy(status = OrderStatus.VOIDED, voidReason = cleanReason, voidedBy = approver, voidedAt = now))
    audit.log(s, "order_voided", "order", orderId, "$cleanReason (approved by $approver)", order.outletId)
    queue("order", orderId, "upsert")
  }

  // ---------- expenses ----------

  suspend fun recordExpense(
    outletId: String,
    category: ExpenseCategory,
    amountCents: Long,
    channel: ExpenseChannel,
    description: String,
  ): ExpenseEntity =
    db.withTransaction {
      val s = sessions.requireSession()
      requirePermission(s, Permission.RECORD_EXPENSE)
      requireOutlet(s, outletId)
      if (amountCents <= 0L || amountCents > MAX_EXPENSE_CENTS) throw AppException(AppError.AMOUNT_INVALID)
      val text = description.trim()
      if (text.length < 2) throw AppException(AppError.DESCRIPTION_REQUIRED)
      val outlet = outletOrFail(outletId)
      val now = clock.nowMillis()
      val expense =
        ExpenseEntity(
          id = UUID.randomUUID().toString(),
          outletId = outletId,
          shiftId = db.shiftDao().openFor(outletId)?.id,
          businessDate = NairobiTime.businessDate(now, outlet.openMinutes),
          category = category,
          amountCents = amountCents,
          channel = channel,
          description = text,
          createdBy = s.staffId,
          createdByName = s.name,
          createdAt = now,
        )
      db.expenseDao().insert(expense)
      audit.log(s, "expense_recorded", "expense", expense.id, "${category.name} $amountCents", outletId)
      queue("expense", expense.id, "upsert")
      expense
    }

  suspend fun voidExpense(
    expenseId: String,
    reason: String,
  ) = db.withTransaction {
    val s = sessions.requireSession()
    requirePermission(s, Permission.VOID_EXPENSE)
    val cleanReason = reason.trim()
    if (cleanReason.isEmpty()) throw AppException(AppError.REASON_REQUIRED)
    val expense = db.expenseDao().byId(expenseId) ?: throw AppException(AppError.NOT_FOUND)
    requireOutlet(s, expense.outletId)
    if (expense.voided) throw AppException(AppError.ALREADY_VOIDED)
    val shift = expense.shiftId?.let { db.shiftDao().byId(it) }
    if (shift != null && shift.status == ShiftStatus.CLOSED) throw AppException(AppError.ORDER_CLOSED_SHIFT)
    db.expenseDao().update(expense.copy(voided = true, voidReason = cleanReason, voidedBy = s.staffId))
    audit.log(s, "expense_voided", "expense", expenseId, cleanReason, expense.outletId)
    queue("expense", expenseId, "upsert")
  }

  // ---------- shifts ----------

  suspend fun openShift(
    outletId: String,
    openingFloatCents: Long,
  ): ShiftEntity =
    db.withTransaction {
      val s = sessions.requireSession()
      requirePermission(s, Permission.RECONCILE_DRAWER)
      requireOutlet(s, outletId)
      if (openingFloatCents < 0L || openingFloatCents > MAX_EXPENSE_CENTS) throw AppException(AppError.AMOUNT_INVALID)
      if (db.shiftDao().openFor(outletId) != null) throw AppException(AppError.SHIFT_ALREADY_OPEN)
      val outlet = outletOrFail(outletId)
      val now = clock.nowMillis()
      val shift =
        ShiftEntity(
          id = UUID.randomUUID().toString(),
          outletId = outletId,
          businessDate = NairobiTime.businessDate(now, outlet.openMinutes),
          number = db.shiftDao().maxNumber(outletId) + 1,
          openedBy = s.staffId,
          openedByName = s.name,
          openedAt = now,
          openingFloatCents = openingFloatCents,
          status = ShiftStatus.OPEN,
        )
      db.shiftDao().insert(shift)
      audit.log(s, "shift_opened", "shift", shift.id, "float=$openingFloatCents", outletId)
      shift
    }

  suspend fun overview(shiftId: String): ShiftOverview {
    val shift = db.shiftDao().byId(shiftId) ?: throw AppException(AppError.NOT_FOUND)
    val cash = db.paymentDao().cashTotalForShift(shiftId)
    val cashOut = db.expenseDao().cashFromTillForShift(shiftId)
    return ShiftOverview(
      shift = shift,
      cashSalesCents = cash,
      cashExpensesCents = cashOut,
      expectedCashCents = shift.openingFloatCents + cash - cashOut,
      mobileMoney = db.paymentDao().mobileMoneyForShift(shiftId),
    )
  }

  /** A manager checks the till statement and marks a mobile-money payment verified or flagged. */
  suspend fun verifyPayment(
    paymentId: String,
    verified: Boolean,
    flagReason: String?,
  ) = db.withTransaction {
    val s = sessions.requireSession()
    requirePermission(s, Permission.RECONCILE_DRAWER)
    val payment = db.paymentDao().byId(paymentId) ?: throw AppException(AppError.NOT_FOUND)
    requireOutlet(s, payment.outletId)
    if (payment.method != PaymentMethod.MOBILE_MONEY) throw AppException(AppError.NOT_FOUND)
    val shift = payment.shiftId?.let { db.shiftDao().byId(it) }
    if (shift != null && shift.status == ShiftStatus.CLOSED) throw AppException(AppError.ORDER_CLOSED_SHIFT)
    val reason = flagReason?.trim().orEmpty()
    if (!verified && reason.isEmpty()) throw AppException(AppError.REASON_REQUIRED)
    db.paymentDao().update(
      payment.copy(
        verification = if (verified) Verification.VERIFIED else Verification.FLAGGED,
        verifiedBy = s.staffId,
        flagReason = if (verified) null else reason,
      ),
    )
    audit.log(
      s,
      if (verified) "payment_verified" else "payment_flagged",
      "payment",
      paymentId,
      if (verified) null else reason,
      payment.outletId,
    )
  }

  suspend fun closeShift(
    shiftId: String,
    countedCashCents: Long,
    varianceReason: String?,
  ): ShiftEntity =
    db.withTransaction {
      val s = sessions.requireSession()
      requirePermission(s, Permission.RECONCILE_DRAWER)
      val shift = db.shiftDao().byId(shiftId) ?: throw AppException(AppError.NOT_FOUND)
      requireOutlet(s, shift.outletId)
      if (shift.status != ShiftStatus.OPEN) throw AppException(AppError.NOT_FOUND)
      if (countedCashCents < 0L || countedCashCents > MAX_EXPENSE_CENTS * 10L) throw AppException(AppError.AMOUNT_INVALID)
      val unverified = db.paymentDao().mobileMoneyForShift(shiftId).count { it.verification == Verification.UNVERIFIED }
      if (unverified > 0) throw AppException(AppError.UNVERIFIED_PAYMENTS)
      val view = overview(shiftId)
      val variance = countedCashCents - view.expectedCashCents
      val reason = varianceReason?.trim().orEmpty()
      if (variance != 0L && reason.isEmpty()) throw AppException(AppError.VARIANCE_REASON_REQUIRED)
      val now = clock.nowMillis()
      val closed =
        shift.copy(
          status = ShiftStatus.CLOSED,
          closedBy = s.staffId,
          closedByName = s.name,
          closedAt = now,
          expectedCashCents = view.expectedCashCents,
          countedCashCents = countedCashCents,
          varianceCents = variance,
          varianceReason = if (variance != 0L) reason else null,
          zNumber = db.shiftDao().maxZ(shift.outletId) + 1,
        )
      db.shiftDao().update(closed)
      audit.log(s, "shift_closed", "shift", shiftId, "variance=$variance", shift.outletId)
      closed
    }

  companion object {
    const val MAX_QTY = 99
    const val MAX_EXPENSE_CENTS = 100_000_000L // KES 1,000,000
  }
}
