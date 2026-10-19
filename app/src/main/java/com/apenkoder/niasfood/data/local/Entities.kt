package com.apenkoder.niasfood.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.ExpenseChannel
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.ShiftStatus
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.domain.Verification

// Data classification (see docs/DATA_CLASSIFICATION.md):
//   restricted: staff.pinHash, payment.reference, shift cash figures
//   confidential: staff names, order/payment/expense records
//   internal: menu, outlets, audit log

@Entity(tableName = "outlet")
data class OutletEntity(
  @PrimaryKey val id: String,
  val name: String,
  val area: String,
  val openMinutes: Int,
  val closeMinutes: Int,
  val active: Boolean = true,
)

@Entity(tableName = "staff", indices = [Index("outletId")])
data class StaffEntity(
  @PrimaryKey val id: String,
  val outletId: String?,
  val name: String,
  val role: Role,
  val pinHash: String,
  val failedAttempts: Int = 0,
  val lockoutUntil: Long? = null,
  val active: Boolean = true,
)

@Entity(tableName = "category")
data class CategoryEntity(
  @PrimaryKey val id: String,
  val name: String,
  val sortOrder: Int,
)

@Entity(tableName = "menu_item", indices = [Index("categoryId")])
data class MenuItemEntity(
  @PrimaryKey val id: String,
  val categoryId: String,
  val name: String,
  val description: String,
  val priceCents: Long,
  val halfPriceCents: Long?,
  val soldOut: Boolean = false,
  val active: Boolean = true,
)

@Entity(tableName = "add_on")
data class AddOnEntity(
  @PrimaryKey val id: String,
  val name: String,
  val priceCents: Long,
  val active: Boolean = true,
)

@Entity(
  tableName = "customer_order",
  indices = [
    Index(value = ["outletId", "businessDate", "token"], unique = true),
    Index("shiftId"),
  ],
)
data class OrderEntity(
  @PrimaryKey val id: String,
  val outletId: String,
  val token: Int,
  val businessDate: String,
  val type: OrderType,
  val status: OrderStatus,
  val createdBy: String,
  val createdByName: String,
  val createdAt: Long,
  val note: String,
  val discountCents: Long,
  val discountReason: String?,
  val discountApprovedBy: String?,
  val totalCents: Long,
  val shiftId: String?,
  val paidAt: Long?,
  val voidReason: String?,
  val voidedBy: String?,
  val voidedAt: Long?,
)

@Entity(tableName = "order_line", indices = [Index("orderId")])
data class OrderLineEntity(
  @PrimaryKey val id: String,
  val orderId: String,
  val itemId: String,
  val name: String,
  val variant: Variant,
  val qty: Int,
  val unitCents: Long,
  val addOnIds: String,
  val addOnsText: String,
  val addOnsCents: Long,
  val lineTotalCents: Long,
)

@Entity(
  tableName = "payment",
  indices = [
    Index("orderId"),
    Index("shiftId"),
    Index(value = ["outletId", "reference"], unique = true),
  ],
)
data class PaymentEntity(
  @PrimaryKey val id: String,
  val orderId: String,
  val outletId: String,
  val shiftId: String?,
  val method: PaymentMethod,
  val amountCents: Long,
  val tenderedCents: Long?,
  val reference: String?,
  val verification: Verification,
  val verifiedBy: String?,
  val flagReason: String?,
  val createdAt: Long,
)

@Entity(
  tableName = "expense",
  indices = [Index(value = ["outletId", "businessDate"]), Index("shiftId")],
)
data class ExpenseEntity(
  @PrimaryKey val id: String,
  val outletId: String,
  val shiftId: String?,
  val businessDate: String,
  val category: ExpenseCategory,
  val amountCents: Long,
  val channel: ExpenseChannel,
  val description: String,
  val createdBy: String,
  val createdByName: String,
  val createdAt: Long,
  val voided: Boolean = false,
  val voidReason: String? = null,
  val voidedBy: String? = null,
)

@Entity(tableName = "shift", indices = [Index("outletId")])
data class ShiftEntity(
  @PrimaryKey val id: String,
  val outletId: String,
  val businessDate: String,
  val number: Int,
  val openedBy: String,
  val openedByName: String,
  val openedAt: Long,
  val openingFloatCents: Long,
  val status: ShiftStatus,
  val closedBy: String? = null,
  val closedByName: String? = null,
  val closedAt: Long? = null,
  val expectedCashCents: Long? = null,
  val countedCashCents: Long? = null,
  val varianceCents: Long? = null,
  val varianceReason: String? = null,
  val zNumber: Int? = null,
)

/** Append-only. Never updated or deleted by the app. */
@Entity(tableName = "audit_event", indices = [Index("outletId")])
data class AuditEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val outletId: String?,
  val actorId: String,
  val actorName: String,
  val action: String,
  val entityType: String,
  val entityId: String,
  val reason: String?,
  val at: Long,
)

/** Queue for a future backend. v1 writes rows here; nothing consumes them yet. */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val entityType: String,
  val entityId: String,
  val op: String,
  val createdAt: Long,
)

@Entity(tableName = "app_setting")
data class SettingEntity(
  @PrimaryKey val settingKey: String,
  val settingValue: String,
)
