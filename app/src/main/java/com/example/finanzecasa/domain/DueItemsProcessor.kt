package com.example.finanzecasa.domain

import androidx.room.withTransaction
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.CardStatementEntity
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Genera le transazioni automatiche scadute: addebiti carta, abbonamenti, rate dei prestiti.
 * È idempotente e recupera i giorni persi (telefono spento, app non aperta).
 */
class DueItemsProcessor(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun run(today: LocalDate) = db.withTransaction {
        processCards(today)
        processSubscriptions(today)
        processLoans(today)
        processRecurring(today)
    }

    private suspend fun processCards(today: LocalDate) {
        for (card in dao.configuredCards()) {
            val linkedId = card.linkedAccountId!!
            val linked = dao.accountById(linkedId) ?: continue
            val since = card.autoDebitSinceEpochDay!!
            val last = dao.lastDebitEpochDay(card.id)
            val after = LocalDate.ofEpochDay(maxOf(last ?: (since - 1), since - 1))

            for (debitDate in CreditCardCycle.debitDatesIn(after, today, card.debitDay!!)) {
                val (start, end) = CreditCardCycle.lastClosedPeriod(debitDate, card.cycleStartDay!!)
                val from = start.atStartOfDay().toMillis()
                val to = end.plusDays(1).atStartOfDay().toMillis() - 1
                val spent = -dao.sumNormal(card.id, from, to) // spese = positivo

                val inserted = dao.insertStatement(
                    CardStatementEntity(
                        cardAccountId = card.id,
                        periodStartEpochDay = start.toEpochDay(),
                        periodEndEpochDay = end.toEpochDay(),
                        debitEpochDay = debitDate.toEpochDay(),
                        amountCents = maxOf(spent, 0)
                    )
                )
                if (inserted == -1L || spent <= 0) continue

                val group = UUID.randomUUID().toString()
                val millis = debitDate.atStartOfDay().toMillis()
                val note = "Saldo trasferito\n${linked.name} → ${card.name}"
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = linked.id, title = "Saldo carta", note = note,
                        amountCents = -spent, dateMillis = millis,
                        kind = TxKind.TRANSFER, transferGroupId = group
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = card.id, title = "Saldo carta", note = note,
                        amountCents = spent, dateMillis = millis,
                        kind = TxKind.TRANSFER, transferGroupId = group
                    )
                )
            }
        }
    }

    private suspend fun processSubscriptions(today: LocalDate) {
        for (s in dao.dueSubscriptions(today.toEpochDay())) {
            var next = LocalDate.ofEpochDay(s.nextDueEpochDay)
            while (!next.isAfter(today)) {
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = s.accountId, categoryId = s.categoryId, title = s.name,
                        amountCents = -s.amountCents, dateMillis = next.atStartOfDay().toMillis()
                    )
                )
                next = RecurringSchedule.following(next, s.cadence, s.anchorDay)
            }
            dao.updateSubscription(s.copy(nextDueEpochDay = next.toEpochDay()))
        }
    }

    private suspend fun processLoans(today: LocalDate) {
        for (l in dao.dueLoans(today.toEpochDay())) {
            var next = LocalDate.ofEpochDay(l.nextDueEpochDay)
            var remaining = l.remainingInstallments
            while (remaining > 0 && !next.isAfter(today)) {
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = l.accountId, categoryId = l.categoryId, title = l.name,
                        amountCents = -l.installmentCents, dateMillis = next.atStartOfDay().toMillis()
                    )
                )
                remaining--
                next = DateUtil.clamp(YearMonth.from(next).plusMonths(1), l.dueDay)
            }
            dao.updateLoan(l.copy(nextDueEpochDay = next.toEpochDay(), remainingInstallments = remaining))
        }
    }

    /** Transazioni ricorrenti (stipendio, affitto, bollette...): importo con segno, entrata o uscita. */
    private suspend fun processRecurring(today: LocalDate) {
        for (r in dao.dueRecurring(today.toEpochDay())) {
            var next = LocalDate.ofEpochDay(r.nextDueEpochDay)
            var remaining = r.remainingOccurrences
            // Con una durata, dopo l'ultima scadenza la ricorrente si ferma da sola
            while (!next.isAfter(today) && remaining != RecurringSchedule.FINISHED) {
                insertRecurringOccurrence(dao, r, next.atStartOfDay().toMillis())
                remaining = RecurringSchedule.remainingAfter(remaining)
                next = RecurringSchedule.following(next, r.cadence, r.anchorDay)
            }
            dao.updateRecurring(
                r.copy(
                    nextDueEpochDay = next.toEpochDay(), remainingOccurrences = remaining,
                    active = r.active && remaining != RecurringSchedule.FINISHED
                )
            )
        }
    }
}
