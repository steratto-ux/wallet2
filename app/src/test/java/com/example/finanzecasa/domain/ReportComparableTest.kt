package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportComparableTest {
    private val month = { ym: YearMonth -> ReportPeriod(ReportMode.MONTH, ym) }

    @Test
    fun currentMonthIsComparedWithTheSameDaysOfThePreviousMonth() {
        val p = month(YearMonth.of(2026, 8))
        // 12 agosto: i primi 12 giorni di luglio (fine esclusa = 13 luglio)
        assertEquals(LocalDate.of(2026, 7, 13), p.previousComparableEnd(LocalDate.of(2026, 8, 12)))
    }

    @Test
    fun longerCurrentMonthIsCappedToThePreviousMonthLength() {
        val p = month(YearMonth.of(2026, 3))
        // 31 marzo contro febbraio (28 giorni): tutto febbraio
        assertEquals(LocalDate.of(2026, 3, 1), p.previousComparableEnd(LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun closedMonthIsComparedWithTheWholePreviousMonth() {
        val p = month(YearMonth.of(2026, 8))
        assertEquals(LocalDate.of(2026, 8, 1), p.previousComparableEnd(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun currentYearIsComparedWithTheSameDaysOfThePreviousYear() {
        val p = ReportPeriod(ReportMode.YEAR, YearMonth.of(2026, 1))
        // 1 febbraio = 32° giorno: dal 1 gennaio 2025 per 32 giorni
        assertEquals(LocalDate.of(2025, 2, 2), p.previousComparableEnd(LocalDate.of(2026, 2, 1)))
    }
}
