package com.example.finanzecasa.alerts

import android.content.Context
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.domain.Alert
import com.example.finanzecasa.domain.AlertPlanner
import com.example.finanzecasa.domain.UnusualSpend
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
import java.time.ZoneId

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

        // Abbonamenti segnati "da rivedere": avviso nei 3 giorni prima del rinnovo
        val reviewDues = dao.activeSubscriptions().filter { it.toReview }.map {
            DueInput("sub", it.id, it.name, it.amountCents, LocalDate.ofEpochDay(it.nextDueEpochDay))
        }
        alerts += AlertPlanner.reviewAlerts(reviewDues, today, ::formatMoney)

        // Categorie con una spesa del mese molto sopra la media
        if (settings.unusualAlertsEnabled()) {
            val zone = ZoneId.systemDefault()
            val from = YearMonth.from(today).minusMonths(UnusualSpend.BASELINE_MONTHS.toLong())
                .atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val month = YearMonth.from(today)
            alerts += UnusualSpend.detect(dao.spendRowsOnce(from, to), today).map { u ->
                Alert(
                    "u|${u.categoryId}|$month",
                    "Spesa sopra la media",
                    "${u.name}: già ${formatMoney(u.monthToDateCents)} questo mese, la tua media è ${formatMoney(u.averageCents)}."
                )
            }
        }

        val sent = AlertPlanner.prune(settings.sentAlerts(), today).toMutableSet()
        for (a in alerts) {
            if (sent.add(a.key)) Notifier.post(context, a.key, a.title, a.text)
        }
        settings.setSentAlerts(sent)
    }
}
