package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CreditCardCycleTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun `esempio Amex - il 24 febbraio si addebita 12 gennaio - 11 febbraio`() {
        val (start, end) = CreditCardCycle.lastClosedPeriod(d("2026-02-24"), startDay = 12)
        assertEquals(d("2026-01-12"), start)
        assertEquals(d("2026-02-11"), end)
    }

    @Test fun `addebito prima dell'inizio periodo - 5 febbraio con inizio 12`() {
        val (start, end) = CreditCardCycle.lastClosedPeriod(d("2026-02-05"), startDay = 12)
        assertEquals(d("2025-12-12"), start)
        assertEquals(d("2026-01-11"), end)
    }

    @Test fun `periodo che inizia il giorno 1 coincide col mese solare`() {
        val (start, end) = CreditCardCycle.lastClosedPeriod(d("2026-02-24"), startDay = 1)
        assertEquals(d("2026-01-01"), start)
        assertEquals(d("2026-01-31"), end)
    }

    @Test fun `inizio periodo 31 si adatta ai mesi corti`() {
        val (start, end) = CreditCardCycle.lastClosedPeriod(d("2026-03-10"), startDay = 31)
        assertEquals(d("2026-01-31"), start)
        assertEquals(d("2026-02-27"), end)
    }

    @Test fun `date di addebito nell'intervallo`() {
        assertEquals(
            listOf(d("2026-02-24")),
            CreditCardCycle.debitDatesIn(d("2026-01-30"), d("2026-03-01"), debitDay = 24)
        )
        assertEquals(
            listOf(d("2026-01-24"), d("2026-02-24"), d("2026-03-24")),
            CreditCardCycle.debitDatesIn(d("2026-01-23"), d("2026-03-24"), debitDay = 24)
        )
    }

    @Test fun `giorno di addebito 31 a febbraio diventa 28`() {
        assertEquals(
            listOf(d("2026-02-28")),
            CreditCardCycle.debitDatesIn(d("2026-02-01"), d("2026-02-28"), debitDay = 31)
        )
    }
}
