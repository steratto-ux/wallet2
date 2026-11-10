package com.example.finanzecasa.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.finanzecasa.domain.ReminderLogic
import com.example.finanzecasa.ui.AppSettings
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Programma il promemoria serale: un lavoro che scatta all'ora scelta e poi si riprogramma per il giorno dopo. */
object ReminderScheduler {
    private const val WORK = "evening-reminder"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val settings = AppSettings(appContext)
        val manager = WorkManager.getInstance(appContext)
        if (!settings.reminderEnabled()) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val minutes = ReminderLogic.minutesUntilNext(LocalDateTime.now(), settings.reminderHour())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = AppSettings(context)
        val now = LocalDateTime.now()
        val remind = settings.notificationsEnabled() && Notifier.canPost(context) && ReminderLogic.shouldRemind(
            settings.reminderEnabled(), settings.reminderHour(), now,
            settings.lastEntryEpochDay(), settings.lastReminderEpochDay()
        )
        if (remind) {
            Notifier.post(
                context, "reminder",
                "Hai registrato le spese di oggi?",
                "Oggi non hai inserito nessuna transazione. Bastano pochi secondi per tenere i conti in ordine."
            )
            settings.setLastReminderEpochDay(now.toLocalDate().toEpochDay())
        }
        ReminderScheduler.schedule(context)
        return Result.success()
    }
}
