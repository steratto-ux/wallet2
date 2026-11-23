package com.example.finanzecasa.report

import com.example.finanzecasa.data.FinanceDao
import com.example.finanzecasa.domain.CashflowSeries
import com.example.finanzecasa.domain.CategoryLine
import com.example.finanzecasa.domain.ReportMath
import com.example.finanzecasa.domain.ReportMode
import com.example.finanzecasa.domain.ReportPeriod
import com.example.finanzecasa.domain.YearSummaryData
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Legge dal database i numeri di un anno, con gli stessi criteri della schermata Report: per l'anno in corso
 * si arriva fino a ora e il confronto con l'anno prima è sugli stessi giorni.
 */
object YearSummaryLoader {
    suspend fun load(dao: FinanceDao, year: Int, now: LocalDateTime): YearSummaryData {
        val today = now.toLocalDate()
        val period = ReportPeriod(ReportMode.YEAR, YearMonth.of(year, 1))
        val from = period.start.atStartOfDay().toMillis()
        val to = minOf(period.endExclusive.atStartOfDay().toMillis(), now.plusMinutes(1).toMillis())
        val prevFrom = period.previous().start.atStartOfDay().toMillis()
        val prevTo = period.previousComparableEnd(today).atStartOfDay().toMillis()

        val monthly = CashflowSeries.build(
            months = (1..12).map { YearMonth.of(year, it) },
            income = dao.observeIncomeByMonth(from, to).first().associate { it.month to it.totalCents },
            expense = dao.observeExpenseByMonth(from, to).first().associate { it.month to it.totalCents },
            current = YearMonth.from(now)
        )
        fun lines(cur: List<com.example.finanzecasa.data.CategorySpend>, prev: List<com.example.finanzecasa.data.CategorySpend>) =
            ReportMath.categoryChanges(cur, prev).map { CategoryLine(it.name, it.currentCents, it.previousCents) }

        return YearSummaryData(
            year = year,
            inProgress = period.isCurrent(today),
            incomeCents = dao.observeIncome(from, to).first(),
            expenseCents = dao.observeExpenses(from, to).first(),
            previousIncomeCents = dao.observeIncome(prevFrom, prevTo).first(),
            previousExpenseCents = dao.observeExpenses(prevFrom, prevTo).first(),
            savedCents = dao.observeSavedBetween(from, to).first(),
            provisionedCents = dao.observeProvisionedBetween(from, to).first(),
            months = monthly,
            expenseCategories = lines(dao.observeCategorySpend(from, to).first(), dao.observeCategorySpend(prevFrom, prevTo).first()),
            incomeCategories = lines(dao.observeCategoryIncome(from, to).first(), dao.observeCategoryIncome(prevFrom, prevTo).first())
        )
    }
}
