package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class YearSummaryTest {
    private fun data(income: Long, expense: Long) = YearSummaryData(
        year = 2026, inProgress = false,
        incomeCents = income, expenseCents = expense, previousIncomeCents = 0, previousExpenseCents = 0,
        savedCents = 0, provisionedCents = 0, months = emptyList(),
        expenseCategories = emptyList(), incomeCategories = emptyList()
    )

    @Test fun `risparmio e tasso di risparmio`() {
        val d = data(income = 3_000_000, expense = 2_400_000)
        assertEquals(600_000L, d.savingsCents)
        assertEquals(20, d.savingsRatePercent)
        assertEquals(-25, data(1_000_000, 1_250_000).savingsRatePercent)
        assertNull(data(0, 5_000).savingsRatePercent)
    }

    @Test fun `le categorie piccole confluiscono in Altre con il loro anno prima`() {
        val lines = listOf(
            CategoryLine("Casa", 500, 400), CategoryLine("Cibo", 300, 350), CategoryLine("Auto", 200, 100),
            CategoryLine("Sport", 50, 40), CategoryLine("Libri", 30, 0), CategoryLine("Zero", 0, 90)
        )
        val top = YearSummary.topWithOthers(lines, 3)
        assertEquals(listOf("Casa", "Cibo", "Auto", "Altre"), top.map { it.name })
        assertEquals(CategoryLine("Altre", 80, 40), top.last())
        // sotto il limite non si raggruppa e gli zeri spariscono
        assertEquals(listOf("Casa", "Cibo", "Auto", "Sport", "Libri"), YearSummary.topWithOthers(lines, 10).map { it.name })
    }

    @Test fun `etichetta della variazione`() {
        assertEquals("+12%", YearSummary.changeLabel(112, 100))
        assertEquals("-50%", YearSummary.changeLabel(50, 100))
        assertEquals("0%", YearSummary.changeLabel(100, 100))
        assertEquals("—", YearSummary.changeLabel(100, 0))
    }

    @Test fun `mese migliore e peggiore`() {
        val pts = listOf(
            CashflowPoint(YearMonth.of(2026, 1), 200, 100, false),
            CashflowPoint(YearMonth.of(2026, 2), 100, 300, false),
            CashflowPoint(YearMonth.of(2026, 3), 0, 0, false),
            CashflowPoint(YearMonth.of(2026, 4), 0, 0, true)
        )
        val (best, worst) = YearSummary.bestAndWorstMonth(pts)!!
        assertEquals(YearMonth.of(2026, 1), best.month)
        assertEquals(YearMonth.of(2026, 2), worst.month)
        assertNull(YearSummary.bestAndWorstMonth(pts.takeLast(2)))
    }
}
