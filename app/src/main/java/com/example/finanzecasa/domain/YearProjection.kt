package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Previsione di fine anno. Il metodo è volutamente semplice e spiegabile: i mesi che mancano si stimano con la media
 * degli ultimi mesi chiusi (fino a 6, solo quelli con movimenti); il mese in corso vale almeno quanto già registrato.
 * Gli importi sono in centesimi.
 */
data class YearProjection(
    val year: Int,
    /** Mesi da oggi a dicembre, compreso quello in corso. */
    val monthsLeft: Int,
    /** Quanti mesi chiusi sono serviti per le medie. */
    val basisMonths: Int,
    val avgIncomeCents: Long,
    val avgExpenseCents: Long,
    /** Da gennaio a oggi (mese in corso compreso). */
    val soFarIncomeCents: Long,
    val soFarExpenseCents: Long,
    /** Spese dei soli mesi già chiusi dell'anno (da gennaio al mese scorso). */
    val priorMonthsExpenseCents: Long,
    val projectedIncomeCents: Long,
    val projectedExpenseCents: Long,
    /** Obiettivo di risparmio dell'anno (12 volte quello mensile); 0 = non impostato. */
    val annualGoalCents: Long
) {
    val projectedSavingsCents: Long get() = projectedIncomeCents - projectedExpenseCents
    val soFarSavingsCents: Long get() = soFarIncomeCents - soFarExpenseCents

    /** Quanto si supera (positivo) o si manca (negativo) l'obiettivo a fine anno; null se non c'è un obiettivo. */
    val gapCents: Long? get() = if (annualGoalCents > 0) projectedSavingsCents - annualGoalCents else null

    /**
     * Spesa media mensile che si può permettere ogni mese rimasto (compreso quello in corso) per arrivare all'obiettivo.
     * Può essere negativa se l'obiettivo non è più raggiungibile; null se non c'è un obiettivo.
     */
    val allowedMonthlyExpenseCents: Long?
        get() = if (annualGoalCents > 0 && monthsLeft > 0)
            (projectedIncomeCents - annualGoalCents - priorMonthsExpenseCents) / monthsLeft
        else null
}

object YearProjectionCalc {
    const val BASIS_MONTHS = 6

    /**
     * [income] e [expense] sono i totali per mese con chiave "2026-11" (uscite positive).
     * [monthlyGoalCents] è l'obiettivo di risparmio mensile impostato nell'Analisi (0 = nessuno).
     * Restituisce null se non ci sono mesi chiusi con movimenti su cui basare le medie.
     */
    fun compute(today: LocalDate, income: Map<String, Long>, expense: Map<String, Long>, monthlyGoalCents: Long): YearProjection? {
        val current = YearMonth.from(today)
        fun inc(m: YearMonth) = income[m.toString()] ?: 0L
        fun exp(m: YearMonth) = expense[m.toString()] ?: 0L

        val basis = (1..BASIS_MONTHS).map { current.minusMonths(it.toLong()) }.filter { inc(it) > 0 || exp(it) > 0 }
        if (basis.isEmpty()) return null
        val avgIncome = basis.sumOf { inc(it) } / basis.size
        val avgExpense = basis.sumOf { exp(it) } / basis.size

        val closed = (1 until current.monthValue).map { YearMonth.of(current.year, it) }
        val closedIncome = closed.sumOf { inc(it) }
        val closedExpense = closed.sumOf { exp(it) }
        val futureMonths = 12 - current.monthValue

        val expectedIncomeNow = maxOf(inc(current), avgIncome)
        val expectedExpenseNow = maxOf(exp(current), avgExpense)

        return YearProjection(
            year = current.year,
            monthsLeft = futureMonths + 1,
            basisMonths = basis.size,
            avgIncomeCents = avgIncome,
            avgExpenseCents = avgExpense,
            soFarIncomeCents = closedIncome + inc(current),
            soFarExpenseCents = closedExpense + exp(current),
            priorMonthsExpenseCents = closedExpense,
            projectedIncomeCents = closedIncome + expectedIncomeNow + futureMonths * avgIncome,
            projectedExpenseCents = closedExpense + expectedExpenseNow + futureMonths * avgExpense,
            annualGoalCents = if (monthlyGoalCents > 0) monthlyGoalCents * 12 else 0L
        )
    }
}
