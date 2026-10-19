package com.apenkoder.niasfood.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apenkoder.niasfood.data.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        OutletEntity::class, StaffEntity::class, CategoryEntity::class, MenuItemEntity::class,
        AddOnEntity::class, OrderEntity::class, OrderLineEntity::class, PaymentEntity::class,
        ExpenseEntity::class, ShiftEntity::class, AuditEntity::class, SyncOutboxEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NiasDatabase : RoomDatabase() {
    abstract fun outletDao(): OutletDao
    abstract fun staffDao(): StaffDao
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun auditDao(): AuditDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun settingDao(): SettingDao
}

/** Builds the database encrypted at rest with SQLCipher. The key never leaves the Keystore unwrapped on disk. */
object DatabaseFactory {
    fun create(context: Context): NiasDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyProvider(context.applicationContext).passphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context.applicationContext, NiasDatabase::class.java, "nias_food.db")
            .openHelperFactory(factory)
            .build()
    }
}
