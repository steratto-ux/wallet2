package com.example.finanzecasa.domain

import com.example.finanzecasa.data.SpendRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UnusualSpendTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun row(cat: Long, name: String, cents: Long, date: LocalDate) = SpendRow(
        "x", cat, null, name, cents, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    private fun history(cat: Long, name: String, cents: Long) = listOf(
        row(cat, name, cents, LocalDate.of(2026, 8, 12)),
        row(cat, name, cents, LocalDate.of(2026, 9, 12)),
        row(cat, name, cents, LocalDate.of(2026, 10, 12))
    )

    @Test
    fun flagsACategoryFarAboveItsMonthlyAverage() {
        val rows = history(1, "Ristoranti", 10_000) + row(1, "Ristoranti", 18_000, LocalDate.of(2026, 11, 8))
        val found = UnusualSpend.detect(rows, today)
        assertEquals(1, found.size)
        assertEquals("Ristoranti", found[0].name)
        assertEquals(18_000L, found[0].monthToDateCents)
        assertEquals(10_000L, found[0].averageCents)
    }

    @Test
    fun ignoresNormalSmallAndNewCategories() {
        val rows = history(1, "Spesa", 40_000) + row(1, "Spesa", 41_000, LocalDate.of(2026, 11, 3)) +
            history(2, "Caffè", 1_000) + row(2, "Caffè", 9_000, LocalDate.of(2026, 11, 3)) +
            row(3, "Nuova", 50_000, LocalDate.of(2026, 11, 3))
        assertTrue(UnusualSpend.detect(rows, today).isEmpty())
    }

    @Test
    fun withoutClosedMonthsThereIsNothingToCompare() {
        val rows = listOf(row(1, "Spesa", 90_000, LocalDate.of(2026, 11, 3)))
        assertTrue(UnusualSpend.detect(rows, today).isEmpty())
    }
}
