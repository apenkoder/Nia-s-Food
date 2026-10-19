package com.apenkoder.niasfood.data.repo

import androidx.room.withTransaction
import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.data.local.AddOnEntity
import com.apenkoder.niasfood.data.local.CategoryEntity
import com.apenkoder.niasfood.data.local.MenuItemEntity
import com.apenkoder.niasfood.data.local.NiasDatabase
import com.apenkoder.niasfood.data.security.SessionManager
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MenuService(
    private val db: NiasDatabase,
    private val clock: AppClock,
    private val audit: Audit,
    private val sessions: SessionManager
) {
    fun categories(): Flow<List<CategoryEntity>> = db.menuDao().observeCategories()
    fun items(): Flow<List<MenuItemEntity>> = db.menuDao().observeItems()
    fun addOns(): Flow<List<AddOnEntity>> = db.menuDao().observeAddOns()

    suspend fun setSoldOut(itemId: String, soldOut: Boolean) {
        val s = sessions.requireSession()
        if (!Permissions.has(s.role, Permission.TOGGLE_SOLD_OUT)) throw AppException(AppError.NOT_ALLOWED)
        db.withTransaction {
            val item = db.menuDao().itemById(itemId) ?: throw AppException(AppError.NOT_FOUND)
            db.menuDao().upsertItem(item.copy(soldOut = soldOut))
            audit.log(s, if (soldOut) "item_sold_out" else "item_back_in_stock", "menu_item", itemId, item.name)
        }
    }

    /** Create ([id] null) or edit an item. Price changes are audited with old and new values. */
    suspend fun saveItem(
        id: String?,
        categoryId: String,
        name: String,
        description: String,
        priceCents: Long,
        halfPriceCents: Long?,
        active: Boolean
    ) {
        val s = sessions.requireSession()
        if (!Permissions.has(s.role, Permission.MANAGE_PRICES)) throw AppException(AppError.NOT_ALLOWED)
        if (name.isBlank()) throw AppException(AppError.NAME_REQUIRED)
        if (priceCents <= 0L || priceCents > MAX_PRICE_CENTS) throw AppException(AppError.AMOUNT_INVALID)
        if (halfPriceCents != null && (halfPriceCents <= 0L || halfPriceCents > priceCents)) {
            throw AppException(AppError.AMOUNT_INVALID)
        }
        db.withTransaction {
            val existing = if (id != null) db.menuDao().itemById(id) else null
            val itemId = existing?.id ?: UUID.randomUUID().toString()
            val item = (existing ?: MenuItemEntity(itemId, categoryId, name, description, priceCents, halfPriceCents))
                .copy(
                    categoryId = categoryId, name = name.trim(), description = description.trim(),
                    priceCents = priceCents, halfPriceCents = halfPriceCents, active = active
                )
            db.menuDao().upsertItem(item)
            val change = if (existing == null) "created price=$priceCents" else
                "price ${existing.priceCents} -> $priceCents, half ${existing.halfPriceCents} -> $halfPriceCents"
            audit.log(s, if (existing == null) "item_created" else "item_edited", "menu_item", itemId, change)
        }
    }

    suspend fun addCategory(name: String) {
        val s = sessions.requireSession()
        if (!Permissions.has(s.role, Permission.MANAGE_PRICES)) throw AppException(AppError.NOT_ALLOWED)
        if (name.isBlank()) throw AppException(AppError.NAME_REQUIRED)
        db.withTransaction {
            val order = db.menuDao().maxCategorySort() + 1
            val category = CategoryEntity(UUID.randomUUID().toString(), name.trim(), order)
            db.menuDao().upsertCategory(category)
            audit.log(s, "category_created", "category", category.id, name.trim())
        }
    }

    companion object {
        const val MAX_PRICE_CENTS = 100_000_00L // KES 100,000
    }
}
