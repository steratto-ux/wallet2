package com.example.finanzecasa.alerts

import android.content.Context
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.domain.AlertPlanner
import com.example.finanzecasa.domain.BudgetInput
import com.example.finanzecasa.domain.CreditCardCycle
import com.example.finanzecasa.domain.DueInput
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.budgetCards
import com.example.finanzecasa.ui.formatMoney
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.YearMonth

/** Controlla soglie dei budget e scadenze vicine e manda le notifiche non ancora inviate. */
class AlertsChecker(private val context: Context, private val db: AppDatabase) {
    private val settings = AppSettings(context)

    suspend fun run(now: LocalDateTime = LocalDateTime.now()) {
        if (!settings.notificationsEnabled() || !Notifier.canPost(context)) return
        val dao = db.dao()
        val today: LocalDate = now.toLocalDate()

        val budgets = budgetCards(dao, now).first()
            .map { BudgetInput(it.id, it.name, it.amountCents, it.spentCents) }
        val alerts = AlertPlanner.budgetAlerts(budgets, YearMonth.from(today), ::formatMoney).toMutableList()

        val dues = mutableListOf<DueInput>()
        dao.activeSubscriptions().forEach {
            dues += DueInput("sub", it.id, it.name, it.amountCents, LocalDate.ofEpochDay(it.nextDueEpochDay))
        }
        dao.activeLoans().forEach {
            dues += DueInput("loan", it.id, it.name, it.installmentCents, LocalDate.ofEpochDay(it.nextDueEpochDay))
        }
        dao.activeRecurring().forEach {
            dues += DueInput("rec", it.id, it.title, kotlin.math.abs(it.amountCents), LocalDate.ofEpochDay(it.nextDueEpochDay))
        }
        for (card in dao.configuredCards()) {
            CreditCardCycle.debitDatesIn(today.minusDays(1), today.plusDays(1), card.debitDay!!).forEach { d ->
                dues += DueInput("card", card.id, card.name, null, d)
            }
        }
        alerts += AlertPlanner.dueAlerts(dues, today, ::formatMoney)

        val sent = AlertPlanner.prune(settings.sentAlerts(), today).toMutableSet()
        for (a in alerts) {
            if (sent.add(a.key)) Notifier.post(context, a.key, a.title, a.text)
        }
        settings.setSentAlerts(sent)
    }
}
