package com.example.finanzecasa.domain

import com.example.finanzecasa.data.CategorySpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportMathTest {
    private fun cat(id: Long, name: String, cents: Long) = CategorySpend(name, null, cents, id)

    @Test fun percentChange() {
        assertEquals(50, ReportMath.percentChange(150, 100))
        assertEquals(-25, ReportMath.percentChange(75, 100))
        assertNull(ReportMath.percentChange(100, 0))
    }

    @Test fun categoriesPresentInOnlyOnePeriodAreKept() {
        val cur = listOf(cat(1, "Spesa", 300), cat(2, "Ristoranti", 200))
        val prev = listOf(cat(1, "Spesa", 250), cat(3, "Viaggi", 400))
        val changes = ReportMath.categoryChanges(cur, prev).associateBy { it.categoryId }
        assertEquals(50L, changes.getValue(1).deltaCents)
        assertEquals(200L, changes.getValue(2).deltaCents)       // nuova
        assertEquals(-400L, changes.getValue(3).deltaCents)      // sparita
        assertEquals(0L, changes.getValue(3).currentCents)
    }

    @Test fun fastestGrowingSortsByEuroIncreaseAndSkipsDecreases() {
        val cur = listOf(cat(1, "A", 300), cat(2, "B", 500), cat(3, "C", 100))
        val prev = listOf(cat(1, "A", 100), cat(2, "B", 450), cat(3, "C", 400))
        val top = ReportMath.fastestGrowing(ReportMath.categoryChanges(cur, prev), limit = 2)
        assertEquals(listOf("A", "B"), top.map { it.name })
    }

    @Test fun uncategorizedRowsAreMatchedByName() {
        val cur = listOf(CategorySpend("Senza categoria", null, 100, null))
        val prev = listOf(CategorySpend("Senza categoria", null, 40, null))
        val c = ReportMath.categoryChanges(cur, prev).single()
        assertEquals(60L, c.deltaCents)
    }

    @Test fun shareRoundsAndHandlesZeroTotal() {
        assertEquals(33, ReportMath.share(1, 3))
        assertEquals(0, ReportMath.share(5, 0))
    }

    @Test fun monthPeriodBounds() {
        val p = ReportPeriod(ReportMode.MONTH, YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1), p.start)
        assertEquals(LocalDate.of(2026, 3, 1), p.endExclusive)
        assertEquals(YearMonth.of(2026, 1), p.previous().anchor)
        assertEquals(true, p.isCurrent(LocalDate.of(2026, 2, 28)))
        assertEquals(false, p.isCurrent(LocalDate.of(2026, 3, 1)))
    }

    @Test fun yearPeriodBounds() {
        val p = ReportPeriod(ReportMode.YEAR, YearMonth.of(2026, 7))
        assertEquals(LocalDate.of(2026, 1, 1), p.start)
        assertEquals(LocalDate.of(2027, 1, 1), p.endExclusive)
        assertEquals(2025, p.previous().anchor.year)
    }
}
