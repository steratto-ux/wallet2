package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth

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

    /** Il periodo contiene [today]? (allora i dati arrivano solo fino a oggi) */
    fun isCurrent(today: LocalDate): Boolean = !today.isBefore(start) && today.isBefore(endExclusive)
}
