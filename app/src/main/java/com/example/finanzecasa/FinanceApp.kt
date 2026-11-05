package com.example.finanzecasa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.data.MIGRATION_1_2
import com.example.finanzecasa.data.MIGRATION_2_3
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.importer.ImportRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class FinanceApp : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "finanze.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
    val importRepository by lazy { ImportRepository(db) }

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<DailyFinanceWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("daily-finance", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class DailyFinanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanceApp
        DueItemsProcessor(app.db).run(LocalDate.now())
        return Result.success()
    }
}
