package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth

/** Spesa di un budget in un mese. */
data class MonthSpend(val month: YearMonth, val spentCents: Long)

object BudgetHistory {
    /**
     * Raggruppa le uscite (giorno, importo positivo) per mese. Restituisce una voce per ogni mese di [months],
     * nello stesso ordine, anche quando non ci sono spese (0).
     */
    fun monthly(spends: List<Pair<LocalDate, Long>>, months: List<YearMonth>): List<MonthSpend> {
        val perMonth = HashMap<YearMonth, Long>()
        for ((day, cents) in spends) {
            val m = YearMonth.from(day)
            perMonth[m] = (perMonth[m] ?: 0L) + cents
        }
        return months.map { MonthSpend(it, perMonth[it] ?: 0L) }
    }

    /** I [count] mesi che finiscono con [current] (compreso), dal più vecchio al più recente. */
    fun lastMonths(current: YearMonth, count: Int): List<YearMonth> =
        (count - 1 downTo 0).map { current.minusMonths(it.toLong()) }

    /** Media dei mesi già chiusi che hanno almeno una spesa; null se non ce ne sono. */
    fun averageClosed(history: List<MonthSpend>, current: YearMonth): Long? {
        val closed = history.filter { it.month.isBefore(current) && it.spentCents > 0 }
        return if (closed.isEmpty()) null else closed.sumOf { it.spentCents } / closed.size
    }
}
