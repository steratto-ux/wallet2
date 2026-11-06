package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetHistoryTest {
    @Test fun `ultimi mesi in ordine cronologico`() {
        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
            BudgetHistory.lastMonths(YearMonth.of(2026, 11), 3)
        )
        assertEquals(YearMonth.of(2025, 12), BudgetHistory.lastMonths(YearMonth.of(2026, 11), 12).first())
    }

    @Test fun `le spese si sommano per mese e i mesi vuoti valgono zero`() {
        val months = BudgetHistory.lastMonths(YearMonth.of(2026, 11), 3)
        val result = BudgetHistory.monthly(
            listOf(
                LocalDate.of(2026, 9, 30) to 1000L,
                LocalDate.of(2026, 11, 1) to 500L,
                LocalDate.of(2026, 11, 5) to 250L
            ),
            months
        )
        assertEquals(listOf(1000L, 0L, 750L), result.map { it.spentCents })
    }

    @Test fun `media dei soli mesi chiusi con spese`() {
        val current = YearMonth.of(2026, 11)
        val history = listOf(
            MonthSpend(YearMonth.of(2026, 8), 0),
            MonthSpend(YearMonth.of(2026, 9), 30000),
            MonthSpend(YearMonth.of(2026, 10), 10000),
            MonthSpend(current, 99999)
        )
        assertEquals(20000L, BudgetHistory.averageClosed(history, current))
        assertNull(BudgetHistory.averageClosed(listOf(MonthSpend(current, 500)), current))
    }
}
