package com.example.finanzecasa.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
        SubscriptionEntity::class, LoanEntity::class, CardStatementEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
}

/** v1 -> v2: valore iniziale degli account. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN initialBalanceCents INTEGER NOT NULL DEFAULT 0")
    }
}
