package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BalanceSeriesTest {
    @Test fun `saldo a fine giornata, con i giorni senza movimenti che mantengono il saldo`() {
        val nov = { d: Int -> LocalDate.of(2026, 11, d) }
        val series = BalanceSeries.build(
            startCents = 1000,
            txs = listOf(nov(2) to -200L, nov(2) to 50L, nov(4) to 100L),
            from = nov(1), to = nov(4)
        )
        assertEquals(listOf(1000L, 850L, 850L, 950L), series.map { it.cents })
        assertEquals(listOf(1, 2, 3, 4), series.map { it.date.dayOfMonth })
    }

    @Test fun `intervallo di un solo giorno`() {
        val d = LocalDate.of(2026, 11, 5)
        val series = BalanceSeries.build(500, listOf(d to -100L), d, d)
        assertEquals(listOf(400L), series.map { it.cents })
    }

    @Test fun `la data di inizio degli intervalli`() {
        assertEquals(LocalDate.of(2026, 2, 28), ChartRange.MONTH.startDate(LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2026, 8, 5), ChartRange.THREE_MONTHS.startDate(LocalDate.of(2026, 11, 5)))
        assertEquals(LocalDate.of(2026, 5, 5), ChartRange.SIX_MONTHS.startDate(LocalDate.of(2026, 11, 5)))
        assertEquals(LocalDate.of(2025, 11, 5), ChartRange.YEAR.startDate(LocalDate.of(2026, 11, 5)))
    }
}
