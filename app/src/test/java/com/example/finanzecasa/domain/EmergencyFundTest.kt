package com.example.finanzecasa.domain

import com.example.finanzecasa.data.MonthTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class EmergencyFundTest {
    private val current = YearMonth.of(2026, 11)
    private val months = listOf(
        MonthTotal("2026-08", 200_000), MonthTotal("2026-09", 300_000),
        MonthTotal("2026-10", 250_000), MonthTotal("2026-11", 100_000)
    )

    @Test
    fun averageUsesClosedMonthsOnly() {
        assertEquals(250_000L, EmergencyFund.averageMonthly(months, current))
    }

    @Test
    fun averageCanBeLimitedToTheMostRecentMonths() {
        assertEquals(275_000L, EmergencyFund.averageMonthly(months, current, maxMonths = 2))
    }

    @Test
    fun noClosedMonthMeansNoAverage() {
        assertNull(EmergencyFund.averageMonthly(emptyList(), current))
        assertNull(EmergencyFund.averageMonthly(listOf(MonthTotal("2026-11", 100_000)), current))
    }

    @Test
    fun targetCoverageAndPercent() {
        assertEquals(1_500_000L, EmergencyFund.target(250_000, 6))
        assertEquals(2.0, EmergencyFund.monthsCovered(500_000, 250_000), 0.001)
        assertEquals(0.0, EmergencyFund.monthsCovered(500_000, 0), 0.001)
        assertEquals(50, EmergencyFund.percent(750_000, 1_500_000))
        assertEquals(100, EmergencyFund.percent(3_000_000, 1_500_000))
        assertEquals(0, EmergencyFund.percent(100, 0))
    }
}
