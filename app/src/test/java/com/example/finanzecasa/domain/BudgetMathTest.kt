package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BudgetMathTest {
    private val now = LocalDateTime.of(2026, 11, 5, 9, 0)

    @Test fun `esempio del riquadro Spesa alimentare`() {
        // 101,39 € spesi su 400 € il 5 novembre: 25%, 26 giorni rimasti, 11,49 € al giorno
        val s = BudgetMath.status(amountCents = 40_000, spentCents = 10_139, now = now)
        assertEquals(25, s.percent)
        assertEquals(26, s.daysLeft)
        assertEquals(29_861L, s.remainingCents)
        assertEquals(1_149L, s.perDayCents)
        assertEquals(BudgetState.AHEAD, s.state) // 25% speso a poco più del 14% del mese
        assertEquals((4 + 9 / 24.0) / 30, s.todayFraction.toDouble(), 1e-6)
    }

    @Test fun `spesa in linea con il mese`() {
        val s = BudgetMath.status(40_000, 2_000, now)
        assertEquals(BudgetState.ON_TRACK, s.state)
        assertEquals(5, s.percent)
    }

    @Test fun `budget superato`() {
        val s = BudgetMath.status(40_000, 45_000, now)
        assertEquals(BudgetState.OVER, s.state)
        assertEquals(113, s.percent)
        assertEquals(1f, s.fillFraction, 0f)
        assertEquals(-5_000L, s.remainingCents)
        assertEquals(0L, s.perDayCents)
    }

    @Test fun `ultimo giorno del mese`() {
        val s = BudgetMath.status(30_000, 10_000, LocalDateTime.of(2026, 11, 30, 23, 59))
        assertEquals(1, s.daysLeft)
        assertEquals(20_000L, s.perDayCents)
        assertTrue(s.todayFraction > 0.99f)
    }
}
