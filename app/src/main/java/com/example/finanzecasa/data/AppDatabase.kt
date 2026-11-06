package com.example.finanzecasa.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
        SubscriptionEntity::class, LoanEntity::class, CardStatementEntity::class,
        BudgetEntity::class, BudgetCategoryEntity::class,
        RecurringEntity::class, GoalEntity::class
    ],
    version = 4,
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

/**
 * v3 -> v4: personalizzazione account, tag e scontrino sulle transazioni, ricorrenti e obiettivi.
 * Solo aggiunte: nessun dato esistente viene toccato.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN colorKey TEXT")
        db.execSQL("ALTER TABLE accounts ADD COLUMN iconKey TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE transactions ADD COLUMN receiptPath TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`cadence` TEXT NOT NULL, " +
                "`anchorDay` INTEGER NOT NULL, " +
                "`nextDueEpochDay` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`active` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`targetCents` INTEGER NOT NULL, " +
                "`accountId` INTEGER, " +
                "`categoryId` INTEGER, " +
                "`manualCents` INTEGER NOT NULL, " +
                "`deadlineEpochDay` INTEGER, " +
                "`createdEpochDay` INTEGER NOT NULL)"
        )
    }
}

/** Tutte le migrazioni, in un posto solo: ogni nuova versione del database si aggiunge qui. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
