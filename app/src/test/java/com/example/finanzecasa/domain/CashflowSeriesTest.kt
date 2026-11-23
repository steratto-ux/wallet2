package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CashflowSeriesTest {
    private val nov26 = YearMonth.of(2026, 11)

    @Test fun `la vista iniziale e l'anno in corso da gennaio a dicembre`() {
        val v = CashflowView.initial(nov26)
        assertEquals(CashflowSpan.CALENDAR_YEAR, v.span)
        assertEquals(12, v.months.size)
        assertEquals(YearMonth.of(2026, 1), v.months.first())
        assertEquals(YearMonth.of(2026, 12), v.months.last())
        assertEquals("2026", v.label())
    }

    @Test fun `le finestre mobili finiscono nel mese di riferimento`() {
        val v = CashflowView(CashflowSpan.THREE_MONTHS, nov26)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), nov26), v.months)
        assertEquals("set – nov 2026", v.label())
        val y = CashflowView(CashflowSpan.TWELVE_MONTHS, nov26)
        assertEquals(12, y.months.size)
        assertEquals(YearMonth.of(2025, 12), y.months.first())
        assertEquals("dic 2025 – nov 2026", y.label())
    }

    @Test fun `le frecce spostano della durata della finestra`() {
        assertEquals(YearMonth.of(2026, 8), CashflowView(CashflowSpan.THREE_MONTHS, nov26).shift(-1, nov26).anchor)
        assertEquals(YearMonth.of(2026, 5), CashflowView(CashflowSpan.SIX_MONTHS, nov26).shift(-1, nov26).anchor)
        assertEquals(YearMonth.of(2025, 11), CashflowView(CashflowSpan.TWELVE_MONTHS, nov26).shift(-1, nov26).anchor)
        assertEquals(2025, CashflowView.initial(nov26).shift(-1, nov26).anchor.year)
        assertEquals(2024, CashflowView.initial(nov26).shift(-1, nov26).shift(-1, nov26).anchor.year)
    }

    @Test fun `non si va oltre il mese o l'anno in corso`() {
        val now = CashflowView(CashflowSpan.THREE_MONTHS, nov26)
        assertFalse(now.canGoForward(nov26))
        assertEquals(now, now.shift(1, nov26))
        assertTrue(now.shift(-1, nov26).canGoForward(nov26))
        // tornando avanti da una finestra passata ci si ferma al mese in corso
        val back = CashflowView(CashflowSpan.SIX_MONTHS, YearMonth.of(2026, 8))
        assertEquals(nov26, back.shift(1, nov26).anchor)
        val year = CashflowView.initial(nov26)
        assertFalse(year.canGoForward(nov26))
        assertEquals(year, year.shift(1, nov26))
        assertEquals(2026, year.shift(-1, nov26).shift(1, nov26).anchor.year)
    }

    @Test fun `cambiando durata si mantiene il periodo guardato`() {
        val past = CashflowView.initial(nov26).shift(-1, nov26) // 2025
        val rolling = past.withSpan(CashflowSpan.THREE_MONTHS, nov26)
        assertEquals(YearMonth.of(2025, 12), rolling.anchor) // ultimo trimestre del 2025
        val fromCurrentYear = CashflowView.initial(nov26).withSpan(CashflowSpan.SIX_MONTHS, nov26)
        assertEquals(nov26, fromCurrentYear.anchor) // anno in corso: si finisce al mese corrente
        val backToYear = CashflowView(CashflowSpan.TWELVE_MONTHS, YearMonth.of(2025, 4)).withSpan(CashflowSpan.CALENDAR_YEAR, nov26)
        assertEquals(2025, backToYear.anchor.year)
        assertEquals(backToYear, backToYear.withSpan(CashflowSpan.CALENDAR_YEAR, nov26))
    }

    @Test fun `la serie assegna entrate e uscite ai mesi e lascia vuoti quelli futuri`() {
        val months = CashflowView.initial(nov26).months
        val points = CashflowSeries.build(
            months,
            income = mapOf("2026-01" to 200_000L, "2026-11" to 150_000L),
            expense = mapOf("2026-01" to 120_000L, "2026-02" to 90_000L, "2026-11" to 60_000L, "2026-12" to 999L),
            current = nov26
        )
        assertEquals(12, points.size)
        assertEquals(200_000L, points[0].incomeCents)
        assertEquals(80_000L, points[0].netCents)
        assertEquals(0L, points[2].incomeCents)
        assertEquals(90_000L, points[1].expenseCents)
        assertFalse(points[10].future)
        assertTrue(points[11].future)
        assertEquals(0L, points[11].expenseCents) // dato ignorato: dicembre non è ancora arrivato
    }

    @Test fun `totali e picco contano solo i mesi trascorsi`() {
        val points = CashflowSeries.build(
            CashflowView.initial(nov26).months,
            income = mapOf("2026-01" to 300_000L, "2026-02" to 100_000L),
            expense = mapOf("2026-01" to 120_000L, "2026-02" to 250_000L),
            current = nov26
        )
        val (inc, exp, net) = CashflowSeries.totals(points)
        assertEquals(400_000L, inc)
        assertEquals(370_000L, exp)
        assertEquals(30_000L, net)
        assertEquals(300_000L, CashflowSeries.peak(points))
        assertEquals(0L, CashflowSeries.peak(CashflowSeries.build(listOf(nov26), emptyMap(), emptyMap(), nov26)))
    }

    @Test fun `nomi dei mesi e colonna sotto il dito`() {
        assertEquals("gen", CashflowSeries.shortMonth(YearMonth.of(2026, 1)))
        assertEquals("novembre 2026", CashflowSeries.longMonth(nov26))
        assertEquals(0, CashflowSeries.slotIndex(-5f, 120f, 12))
        assertEquals(0, CashflowSeries.slotIndex(9f, 120f, 12))
        assertEquals(1, CashflowSeries.slotIndex(10f, 120f, 12))
        assertEquals(11, CashflowSeries.slotIndex(500f, 120f, 12))
        assertEquals(0, CashflowSeries.slotIndex(10f, 0f, 12))
    }

    @Test fun `il fondo scala e tondo e contiene sempre il massimo`() {
        assertEquals(0L, CashflowSeries.niceMax(0))
        assertEquals(150_000L, CashflowSeries.niceMax(123_456))
        assertEquals(300_000L, CashflowSeries.niceMax(300_000))
        assertEquals(400_000L, CashflowSeries.niceMax(300_001))
        assertEquals(100L, CashflowSeries.niceMax(5))
        assertEquals(1_000_000L, CashflowSeries.niceMax(850_000))
        for (cents in listOf(1L, 99L, 1_234L, 56_789L, 999_999L, 12_345_678L)) {
            assertTrue(CashflowSeries.niceMax(cents) >= cents)
        }
    }
}
