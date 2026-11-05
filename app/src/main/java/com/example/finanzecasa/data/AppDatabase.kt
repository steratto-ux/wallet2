package com.example.finanzecasa.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
        SubscriptionEntity::class, LoanEntity::class, CardStatementEntity::class,
        BudgetEntity::class, BudgetCategoryEntity::class
    ],
    version = 3,
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

/** v2 -> v3: icona delle categorie e budget mensili. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_categories` (" +
                "`budgetId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`budgetId`, `categoryId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_categories_categoryId` ON `budget_categories` (`categoryId`)")
    }
}
