package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Scelta e descrizione delle scadenze mostrate nel widget. */
object WidgetDeadlines {
    const val MAX_ROWS = 3
    const val HORIZON_DAYS = 30L

    /** Le prossime [max] scadenze da oggi ai prossimi 30 giorni, in ordine di data. */
    fun pick(scheduled: List<CalendarScheduled>, today: LocalDate, max: Int = MAX_ROWS): List<CalendarScheduled> =
        scheduled
            .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(HORIZON_DAYS)) }
            .sortedBy { it.date }
            .take(max)

    /** "Oggi", "Domani", "Tra 3 giorni" (fino a una settimana), poi la data ("27 nov"). */
    fun whenLabel(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days <= 0L -> "Oggi"
            days == 1L -> "Domani"
            days <= 6L -> "Tra $days giorni"
            else -> "${date.dayOfMonth} ${CashflowSeries.shortMonth(YearMonth.from(date))}"
        }
    }
}
