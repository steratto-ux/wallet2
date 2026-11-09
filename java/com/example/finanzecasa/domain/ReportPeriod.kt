package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ReportMode { MONTH, YEAR }

/** Periodo di un report: un mese oppure un anno intero. Per l'anno conta solo l'anno di [anchor]. */
data class ReportPeriod(val mode: ReportMode, val anchor: YearMonth) {
    val start: LocalDate
        get() = if (mode == ReportMode.MONTH) anchor.atDay(1) else LocalDate.of(anchor.year, 1, 1)

    /** Primo giorno dopo la fine del periodo. */
    val endExclusive: LocalDate
        get() = if (mode == ReportMode.MONTH) anchor.plusMonths(1).atDay(1) else LocalDate.of(anchor.year + 1, 1, 1)

    fun shift(delta: Int): ReportPeriod =
        if (mode == ReportMode.MONTH) copy(anchor = anchor.plusMonths(delta.toLong()))
        else copy(anchor = anchor.plusYears(delta.toLong()))

    fun previous(): ReportPeriod = shift(-1)

    /**
     * Fine (esclusa) del periodo precedente da usare nel confronto. Se questo periodo è ancora in corso si confrontano
     * gli stessi giorni: il 12 del mese, i primi 12 giorni del mese precedente. A periodo chiuso si usa il precedente intero.
     */
    fun previousComparableEnd(today: LocalDate): LocalDate {
        val prev = previous()
        if (!isCurrent(today)) return prev.endExclusive
        val elapsed = ChronoUnit.DAYS.between(start, today) + 1
        val length = ChronoUnit.DAYS.between(prev.start, prev.endExclusive)
        return prev.start.plusDays(minOf(elapsed, length))
    }

    /** Il periodo contiene [today]? (allora i dati arrivano solo fino a oggi) */
    fun isCurrent(today: LocalDate): Boolean = !today.isBefore(start) && today.isBefore(endExclusive)
}
