package com.apenkoder.niasfood.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutletDao {
    @Query("SELECT * FROM outlet WHERE active = 1 ORDER BY name")
    suspend fun active(): List<OutletEntity>

    @Query("SELECT * FROM outlet WHERE id = :id")
    suspend fun byId(id: String): OutletEntity?

    @Query("SELECT COUNT(*) FROM outlet")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outlet: OutletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(outlets: List<OutletEntity>)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE active = 1")
    suspend fun active(): List<StaffEntity>

    @Query("SELECT * FROM staff")
    fun observeAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun byId(id: String): StaffEntity?

    @Query("SELECT * FROM staff WHERE active = 1 AND (role = 'OWNER' OR (role = 'MANAGER' AND outletId = :outletId))")
    suspend fun approversFor(outletId: String): List<StaffEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(staff: StaffEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(staff: List<StaffEntity>)
}

@Dao
interface MenuDao {
    @Query("SELECT * FROM category ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM menu_item ORDER BY name")
    fun observeItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM add_on WHERE active = 1 ORDER BY name")
    fun observeAddOns(): Flow<List<AddOnEntity>>

    @Query("SELECT * FROM menu_item WHERE id = :id")
    suspend fun itemById(id: String): MenuItemEntity?

    @Query("SELECT * FROM add_on WHERE id IN (:ids)")
    suspend fun addOnsByIds(ids: List<String>): List<AddOnEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category")
    suspend fun maxCategorySort(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: MenuItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<MenuItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddOns(addOns: List<AddOnEntity>)
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Insert
    suspend fun insertLines(lines: List<OrderLineEntity>)

    /** Only used for HELD (unpaid draft) orders that are being re-saved or resumed. */
    @Query("DELETE FROM order_line WHERE orderId = :orderId")
    suspend fun deleteLines(orderId: String)

    @Query("SELECT COALESCE(MAX(token), 0) FROM customer_order WHERE outletId = :outletId AND businessDate = :date")
    suspend fun maxToken(outletId: String, date: String): Int

    @Query("SELECT * FROM customer_order WHERE id = :id")
    suspend fun byId(id: String): OrderEntity?

    @Query("SELECT * FROM order_line WHERE orderId = :orderId")
    suspend fun linesFor(orderId: String): List<OrderLineEntity>

    @Query("SELECT * FROM customer_order WHERE outletId = :outletId AND status = 'HELD' ORDER BY createdAt DESC")
    fun observeHeld(outletId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM customer_order WHERE outletId = :outletId AND businessDate = :date AND status != 'HELD' ORDER BY createdAt DESC")
    fun observeDay(outletId: String, date: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM customer_order WHERE businessDate BETWEEN :from AND :to AND status != 'HELD' AND (:outletId IS NULL OR outletId = :outletId)")
    suspend fun inRange(from: String, to: String, outletId: String?): List<OrderEntity>

    @Query("SELECT l.* FROM order_line l JOIN customer_order o ON o.id = l.orderId WHERE o.businessDate BETWEEN :from AND :to AND o.status = 'PAID' AND (:outletId IS NULL OR o.outletId = :outletId)")
    suspend fun linesInRange(from: String, to: String, outletId: String?): List<OrderLineEntity>

    @Query("SELECT * FROM customer_order WHERE shiftId = :shiftId")
    suspend fun forShift(shiftId: String): List<OrderEntity>

    @Query("SELECT l.* FROM order_line l JOIN customer_order o ON o.id = l.orderId WHERE o.shiftId = :shiftId AND o.status = 'PAID'")
    suspend fun linesForShift(shiftId: String): List<OrderLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinesReplace(lines: List<OrderLineEntity>)
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT * FROM payment WHERE id = :id")
    suspend fun byId(id: String): PaymentEntity?

    @Query("SELECT * FROM payment WHERE orderId = :orderId")
    suspend fun forOrder(orderId: String): List<PaymentEntity>

    @Query("SELECT COUNT(*) FROM payment WHERE outletId = :outletId AND reference = :reference")
    suspend fun referenceCount(outletId: String, reference: String): Int

    @Query("SELECT p.* FROM payment p JOIN customer_order o ON o.id = p.orderId WHERE o.businessDate BETWEEN :from AND :to AND o.status = 'PAID' AND (:outletId IS NULL OR o.outletId = :outletId)")
    suspend fun inRange(from: String, to: String, outletId: String?): List<PaymentEntity>

    @Query("SELECT p.* FROM payment p JOIN customer_order o ON o.id = p.orderId WHERE p.shiftId = :shiftId AND o.status = 'PAID'")
    suspend fun forShift(shiftId: String): List<PaymentEntity>

    @Query("SELECT p.* FROM payment p JOIN customer_order o ON o.id = p.orderId WHERE p.shiftId = :shiftId AND p.method = 'MOBILE_MONEY' AND o.status = 'PAID' ORDER BY p.createdAt")
    suspend fun mobileMoneyForShift(shiftId: String): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(p.amountCents), 0) FROM payment p JOIN customer_order o ON o.id = p.orderId WHERE p.shiftId = :shiftId AND p.method = 'CASH' AND o.status = 'PAID'")
    suspend fun cashTotalForShift(shiftId: String): Long
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun byId(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE (:outletId IS NULL OR outletId = :outletId) ORDER BY createdAt DESC LIMIT 60")
    fun observeRecent(outletId: String?): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE businessDate BETWEEN :from AND :to AND voided = 0 AND (:outletId IS NULL OR outletId = :outletId)")
    suspend fun inRange(from: String, to: String, outletId: String?): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE shiftId = :shiftId AND voided = 0")
    suspend fun forShift(shiftId: String): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expense WHERE shiftId = :shiftId AND channel = 'CASH_FROM_TILL' AND voided = 0")
    suspend fun cashFromTillForShift(shiftId: String): Long
}

@Dao
interface ShiftDao {
    @Insert
    suspend fun insert(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shifts: List<ShiftEntity>)

    @Update
    suspend fun update(shift: ShiftEntity)

    @Query("SELECT * FROM shift WHERE id = :id")
    suspend fun byId(id: String): ShiftEntity?

    @Query("SELECT * FROM shift WHERE outletId = :outletId AND status = 'OPEN' LIMIT 1")
    suspend fun openFor(outletId: String): ShiftEntity?

    @Query("SELECT * FROM shift WHERE outletId = :outletId AND status = 'OPEN' LIMIT 1")
    fun observeOpenFor(outletId: String): Flow<ShiftEntity?>

    @Query("SELECT COALESCE(MAX(zNumber), 0) FROM shift WHERE outletId = :outletId")
    suspend fun maxZ(outletId: String): Int

    @Query("SELECT COALESCE(MAX(number), 0) FROM shift WHERE outletId = :outletId")
    suspend fun maxNumber(outletId: String): Int

    @Query("SELECT * FROM shift WHERE status = 'CLOSED' AND (:outletId IS NULL OR outletId = :outletId) ORDER BY closedAt DESC LIMIT 60")
    suspend fun closed(outletId: String?): List<ShiftEntity>
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(event: AuditEntity)

    @Query("SELECT * FROM audit_event ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AuditEntity>
}

@Dao
interface SyncOutboxDao {
    @Insert
    suspend fun insert(row: SyncOutboxEntity)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int
}

@Dao
interface SettingDao {
    @Query("SELECT settingValue FROM app_setting WHERE settingKey = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
