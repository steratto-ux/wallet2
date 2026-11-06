package com.example.finanzecasa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.finanzecasa.alerts.AlertsChecker
import com.example.finanzecasa.backup.AutoBackup
import com.example.finanzecasa.data.ALL_MIGRATIONS
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.data.DbSafety
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.importer.ExportRepository
import com.example.finanzecasa.importer.ImportRepository
import com.example.finanzecasa.widget.WalletWidgetProvider
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class FinanceApp : Application() {
    val db: AppDatabase by lazy {
        // Copia di sicurezza prima di una migrazione, poi apertura con tutte le migrazioni (mai cancellazione dei dati).
        DbSafety.copyBeforeMigration(this, DB_NAME, DB_VERSION)
        Room.databaseBuilder(this, AppDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
    val importRepository by lazy { ImportRepository(db) }
    val exportRepository by lazy { ExportRepository(db) }

    override fun onCreate() {
        super.onCreate()
        // Ogni 3 ore: scadenze, avvisi, backup automatico, widget. UPDATE sostituisce il vecchio job giornaliero.
        val request = PeriodicWorkRequestBuilder<DailyFinanceWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("daily-finance", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val DB_NAME = "finanze.db"
        const val DB_VERSION = 4 // deve coincidere con @Database(version = ...)
    }
}

class DailyFinanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanceApp
        DueItemsProcessor(app.db).run(LocalDate.now())
        runCatching { AlertsChecker(app, app.db).run() }
        runCatching { AutoBackup(app, app.db).runIfDue() }
        runCatching { WalletWidgetProvider.refreshAll(app) }
        return Result.success()
    }
}
