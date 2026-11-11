package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.data.TxKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Movimento minimo per il calendario. [future] = la data è successiva ad ora (transazione programmata). */
data class CalendarTx(val date: LocalDate, val kind: TxKind, val amountCents: Long, val future: Boolean)

/** Una scadenza prevista (abbonamento, rata, ricorrente) non ancora registrata. Importo con segno. */
data class CalendarScheduled(val title: String, val date: LocalDate, val cents: Long, val label: String)

/** Totali di un giorno. I trasferimenti non contano come entrata o spesa, ma fanno comparire il giorno tra quelli con movimenti. */
data class DayTotals(
    val expenseCents: Long = 0,
    val incomeCents: Long = 0,
    val transfers: Int = 0,
    val count: Int = 0,
    val hasFuture: Boolean = false
) {
    val netCents: Long get() = incomeCents - expenseCents
}

object CalendarMath {
    /**
     * Celle del mese a settimane intere, da lunedì a domenica: i giorni fuori dal mese sono null.
     * La lunghezza è sempre un multiplo di 7.
     */
    fun grid(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value // lunedì = 0
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List<LocalDate?>(lead) { null } + days
        val tail = (7 - cells.size % 7) % 7
        return cells + List<LocalDate?>(tail) { null }
    }

    fun weeks(month: YearMonth): Int = grid(month).size / 7

    /** Totali per giorno. Le transazioni di valutazione degli investimenti non arrivano qui (la query le esclude). */
    fun dayTotals(txs: List<CalendarTx>): Map<LocalDate, DayTotals> =
        txs.groupBy { it.date }.mapValues { (_, list) ->
            var expense = 0L
            var income = 0L
            var transfers = 0
            for (t in list) {
                if (t.kind == TxKind.NORMAL) {
                    if (t.amountCents < 0) expense += -t.amountCents else income += t.amountCents
                } else if (t.kind == TxKind.TRANSFER) {
                    transfers++
                }
            }
            DayTotals(expense, income, transfers, list.size, list.any { it.future })
        }

    /** Totale del mese: uscite, entrate e saldo (entrate meno uscite). */
    fun monthTotals(days: Map<LocalDate, DayTotals>, month: YearMonth): DayTotals {
        var expense = 0L
        var income = 0L
        for ((d, t) in days) {
            if (YearMonth.from(d) == month) {
                expense += t.expenseCents
                income += t.incomeCents
            }
        }
        return DayTotals(expenseCents = expense, incomeCents = income)
    }

    /**
     * Scadenze previste tra [from] e [to] (estremi inclusi): per ogni abbonamento, rata o ricorrente si espandono
     * le occorrenze successive alla prossima, così anche i mesi a venire mostrano le loro scadenze.
     * I trasferimenti ricorrenti non sono spese e restano fuori.
     */
    fun scheduled(
        subs: List<SubscriptionEntity>,
        loans: List<LoanEntity>,
        recurring: List<RecurringEntity>,
        from: LocalDate,
        to: LocalDate
    ): List<CalendarScheduled> {
        fun occurrences(start: LocalDate, cadence: Cadence, anchor: Int, limit: Int): List<LocalDate> {
            val out = ArrayList<LocalDate>()
            var d = start
            var n = 0
            while (!d.isAfter(to) && n < limit) {
                if (!d.isBefore(from)) out += d
                d = RecurringSchedule.following(d, cadence, anchor)
                n++
            }
            return out
        }

        val result = ArrayList<CalendarScheduled>()
        for (s in subs.filter { it.active }) {
            occurrences(LocalDate.ofEpochDay(s.nextDueEpochDay), s.cadence, s.anchorDay, 400)
                .forEach { result += CalendarScheduled(s.name, it, -s.amountCents, "Abbonamento") }
        }
        for (l in loans.filter { it.remainingInstallments > 0 }) {
            occurrences(LocalDate.ofEpochDay(l.nextDueEpochDay), Cadence.MONTHLY, l.dueDay, l.remainingInstallments)
                .forEach { result += CalendarScheduled(l.name, it, -l.installmentCents, "Rata") }
        }
        for (r in recurring.filter { it.active && it.toAccountId == null }) {
            occurrences(LocalDate.ofEpochDay(r.nextDueEpochDay), r.cadence, r.anchorDay, if (r.remainingOccurrences > 0) r.remainingOccurrences else 400)
                .forEach { result += CalendarScheduled(r.title, it, r.amountCents, "Ricorrente") }
        }
        return result.sortedBy { it.date }
    }
}
