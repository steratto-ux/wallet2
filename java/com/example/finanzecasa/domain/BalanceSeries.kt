package com.example.finanzecasa.domain

import java.time.LocalDate

/** Intervalli selezionabili per il grafico del saldo. */
enum class ChartRange(val shortLabel: String, val description: String, val months: Long) {
    MONTH("1 mese", "Ultimo mese", 1),
    THREE_MONTHS("3 mesi", "Ultimi 3 mesi", 3),
    SIX_MONTHS("6 mesi", "Ultimi 6 mesi", 6),
    YEAR("1 anno", "Ultimo anno", 12);

    fun startDate(today: LocalDate): LocalDate = today.minusMonths(months)
}

data class BalancePoint(val date: LocalDate, val cents: Long)

object BalanceSeries {
    /**
     * Saldo a fine giornata per ogni giorno da [from] a [to] inclusi.
     * [startCents] è il saldo prima dell'inizio di [from]; [txs] sono (giorno, importo) dei movimenti dell'intervallo.
     */
    fun build(startCents: Long, txs: List<Pair<LocalDate, Long>>, from: LocalDate, to: LocalDate): List<BalancePoint> {
        val perDay = HashMap<LocalDate, Long>()
        for ((day, cents) in txs) perDay[day] = (perDay[day] ?: 0L) + cents

        val result = ArrayList<BalancePoint>()
        var balance = startCents
        var day = from
        while (!day.isAfter(to)) {
            balance += perDay[day] ?: 0L
            result.add(BalancePoint(day, balance))
            day = day.plusDays(1)
        }
        return result
    }
}
