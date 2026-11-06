package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalMathTest {
    @Test fun fractionIsClamped() {
        assertEquals(0.5f, GoalMath.fraction(50_00, 100_00), 0.0001f)
        assertEquals(1f, GoalMath.fraction(150_00, 100_00), 0f)
        assertEquals(0f, GoalMath.fraction(-10_00, 100_00), 0f)
        assertEquals(0f, GoalMath.fraction(10, 0), 0f)
    }

    @Test fun percentCanExceedHundred() {
        assertEquals(125, GoalMath.percent(125_00, 100_00))
    }

    @Test fun monthlyNeededSplitsRemainingOverMonths() {
        val today = LocalDate.of(2026, 1, 1)
        // 6 mesi (181 giorni ≈ 5,9 mesi -> 6): restano 600 euro -> 100 euro al mese
        assertEquals(10_000L, GoalMath.monthlyNeededCents(0, 60_000, today, LocalDate.of(2026, 7, 1)))
    }

    @Test fun monthlyNeededIsNullWhenDoneOrExpired() {
        val today = LocalDate.of(2026, 1, 1)
        assertNull(GoalMath.monthlyNeededCents(60_000, 60_000, today, LocalDate.of(2026, 7, 1)))
        assertNull(GoalMath.monthlyNeededCents(0, 60_000, today, today.minusDays(1)))
    }

    @Test fun lessThanAMonthCountsAsOneMonth() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals(50_000L, GoalMath.monthlyNeededCents(0, 50_000, today, today.plusDays(10)))
    }
}
