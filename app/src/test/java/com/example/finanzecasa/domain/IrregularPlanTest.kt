package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IrregularPlanTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun monthsLeftRoundsUpAndIsAtLeastOne() {
        assertEquals(1, IrregularPlan.monthsLeft(today, today))
        assertEquals(1, IrregularPlan.monthsLeft(today, today.minusDays(5)))
        assertEquals(1, IrregularPlan.monthsLeft(today, today.plusDays(20)))
        assertEquals(2, IrregularPlan.monthsLeft(today, today.plusDays(40)))
        assertEquals(12, IrregularPlan.monthsLeft(today, today.plusYears(1).minusDays(1)))
    }

    @Test
    fun monthlyQuotaDividesWhatIsMissing() {
        val s = IrregularPlan.plan(listOf(IrregularInput(1, "Assicurazione", 60_000, today.plusMonths(6))), 0, today)
        assertEquals(6, s.rows[0].monthsLeft)
        assertEquals(10_000L, s.rows[0].monthlyCents)
        assertEquals(60_000L, s.totalCents)
        assertEquals(60_000L, s.missingCents)
        assertEquals(10_000L, s.monthlyCents)
    }

    @Test
    fun poolCoversEarliestExpensesFirst() {
        val items = listOf(
            IrregularInput(2, "Vacanza", 120_000, today.plusYears(1)),
            IrregularInput(1, "Bollo", 30_000, today.plusMonths(2))
        )
        val s = IrregularPlan.plan(items, 40_000, today)
        assertEquals(listOf(1L, 2L), s.rows.map { it.id })
        assertEquals(30_000L, s.rows[0].coveredCents)
        assertEquals(0L, s.rows[0].monthlyCents)
        assertEquals(10_000L, s.rows[1].coveredCents)
        assertEquals(110_000L, s.rows[1].missingCents)
        assertEquals(9_167L, s.rows[1].monthlyCents) // 110.000 / 12 arrotondato per eccesso
        assertEquals(40_000L, s.coveredCents)
        assertEquals(110_000L, s.missingCents)
        assertEquals(9_167L, s.monthlyCents)
    }

    @Test
    fun poolLargerThanTheTotalLeavesASurplus() {
        val s = IrregularPlan.plan(listOf(IrregularInput(1, "Bollo", 10_000, today.plusMonths(3))), 25_000, today)
        assertEquals(10_000L, s.coveredCents)
        assertEquals(25_000L, s.poolCents)
        assertEquals(0L, s.missingCents)
        assertEquals(0L, s.monthlyCents)
    }

    @Test
    fun usableIsTheSmallerOfPoolAndCostAndNeverNegative() {
        assertEquals(5_000L, IrregularPlan.usable(5_000, 8_000))
        assertEquals(8_000L, IrregularPlan.usable(9_000, 8_000))
        assertEquals(0L, IrregularPlan.usable(-5, 100))
    }
}
