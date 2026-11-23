package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class YearProjectionTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun months(from: Int, to: Int, cents: Long, year: Int = 2026): Map<String, Long> =
        (from..to).associate { "%04d-%02d".format(year, it) to cents }

    @Test fun `senza mesi chiusi con movimenti non c'e previsione`() {
        assertNull(YearProjectionCalc.compute(today, emptyMap(), emptyMap(), 0))
        // il solo mese in corso non basta per fare una media
        assertNull(YearProjectionCalc.compute(today, mapOf("2026-11" to 5_000L), mapOf("2026-11" to 3_000L), 0))
    }

    @Test fun `i mesi mancanti si stimano con la media e il mese in corso vale almeno quanto registrato`() {
        val income = months(1, 10, 200_000) + ("2026-11" to 50_000L)
        val expense = months(1, 10, 150_000) + ("2026-11" to 70_000L)
        val p = YearProjectionCalc.compute(today, income, expense, monthlyGoalCents = 40_000)!!
        assertEquals(6, p.basisMonths)
        assertEquals(200_000L, p.avgIncomeCents)
        assertEquals(150_000L, p.avgExpenseCents)
        assertEquals(2, p.monthsLeft)
        assertEquals(2_050_000L, p.soFarIncomeCents)
        assertEquals(1_570_000L, p.soFarExpenseCents)
        // 10 mesi chiusi + novembre stimato come la media + dicembre
        assertEquals(2_400_000L, p.projectedIncomeCents)
        assertEquals(1_800_000L, p.projectedExpenseCents)
        assertEquals(600_000L, p.projectedSavingsCents)
        // obiettivo 40.000 al mese = 480.000 l'anno
        assertEquals(480_000L, p.annualGoalCents)
        assertEquals(120_000L, p.gapCents)
        // (2.400.000 - 480.000 - 1.500.000) / 2 mesi rimasti
        assertEquals(210_000L, p.allowedMonthlyExpenseCents)
    }

    @Test fun `se il mese in corso supera la media si usa quanto gia registrato`() {
        val income = months(1, 10, 200_000)
        val expense = months(1, 10, 150_000) + ("2026-11" to 260_000L)
        val p = YearProjectionCalc.compute(today, income, expense, 0)!!
        assertEquals(260_000L, p.projectedExpenseCents - 1_500_000L - p.avgExpenseCents)
        assertNull(p.gapCents)
        assertNull(p.allowedMonthlyExpenseCents)
    }

    @Test fun `la media usa solo i mesi con movimenti e al massimo sei`() {
        // l'app è usata solo da agosto: tre mesi chiusi (ago, set, ott)
        val income = months(8, 10, 300_000)
        val expense = months(8, 10, 100_000)
        val p = YearProjectionCalc.compute(today, income, expense, 0)!!
        assertEquals(3, p.basisMonths)
        assertEquals(300_000L, p.avgIncomeCents)
        // mesi precedenti oltre i sei non pesano
        val old = months(1, 4, 1_000_000)
        val q = YearProjectionCalc.compute(today, old + months(5, 10, 200_000), months(5, 10, 100_000), 0)!!
        assertEquals(6, q.basisMonths)
        assertEquals(200_000L, q.avgIncomeCents)
    }

    @Test fun `a inizio anno le medie arrivano dall'anno scorso`() {
        val jan = LocalDate.of(2026, 1, 10)
        val income = months(7, 12, 100_000, year = 2025)
        val expense = months(7, 12, 60_000, year = 2025)
        val p = YearProjectionCalc.compute(jan, income, expense, monthlyGoalCents = 30_000)!!
        assertEquals(12, p.monthsLeft)
        assertEquals(0L, p.priorMonthsExpenseCents)
        assertEquals(1_200_000L, p.projectedIncomeCents)
        assertEquals(720_000L, p.projectedExpenseCents)
        assertEquals(480_000L, p.projectedSavingsCents)
        assertEquals(360_000L, p.annualGoalCents)
        assertEquals(120_000L, p.gapCents)
        assertEquals(70_000L, p.allowedMonthlyExpenseCents) // (1.200.000 - 360.000) / 12
    }

    @Test fun `obiettivo non piu raggiungibile da un limite negativo`() {
        val income = months(1, 10, 100_000)
        val expense = months(1, 10, 95_000)
        val p = YearProjectionCalc.compute(today, income, expense, monthlyGoalCents = 50_000)!!
        assertNotNull(p.allowedMonthlyExpenseCents)
        assertTrue(p.allowedMonthlyExpenseCents!! < 0)
        assertTrue(p.gapCents!! < 0)
    }
}
