package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.data.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMathTest {
    @Test
    fun novemberTwentyTwentySixStartsOnSundayAndHasSixWeeks() {
        val grid = CalendarMath.grid(YearMonth.of(2026, 11))
        assertEquals(42, grid.size)
        // lunedì-sabato vuoti, il primo cade di domenica
        assertTrue(grid.take(6).all { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), grid[6])
        assertEquals(LocalDate.of(2026, 11, 30), grid[35])
        assertEquals(6, CalendarMath.weeks(YearMonth.of(2026, 11)))
    }

    @Test
    fun aMonthStartingOnMondayHasNoLeadingBlanks() {
        // febbraio 2027: 1° lunedì, 28 giorni = 4 settimane esatte
        val grid = CalendarMath.grid(YearMonth.of(2027, 2))
        assertEquals(28, grid.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid.first())
        assertNull(grid.firstOrNull { it == null })
    }

    @Test
    fun gridLengthIsAlwaysWholeWeeks() {
        for (m in 1..12) assertEquals(0, CalendarMath.grid(YearMonth.of(2026, m)).size % 7)
    }

    @Test
    fun dayTotalsSplitExpensesAndIncomeAndIgnoreTransfers() {
        val d = LocalDate.of(2026, 11, 3)
        val totals = CalendarMath.dayTotals(
            listOf(
                CalendarTx(d, TxKind.NORMAL, -2_000, false),
                CalendarTx(d, TxKind.NORMAL, -1_500, false),
                CalendarTx(d, TxKind.NORMAL, 10_000, false),
                CalendarTx(d, TxKind.TRANSFER, -5_000, false),
                CalendarTx(LocalDate.of(2026, 11, 4), TxKind.NORMAL, -300, true)
            )
        )
        val day = totals.getValue(d)
        assertEquals(3_500, day.expenseCents)
        assertEquals(10_000, day.incomeCents)
        assertEquals(6_500, day.netCents)
        assertEquals(1, day.transfers)
        assertEquals(4, day.count)
        assertEquals(false, day.hasFuture)
        assertEquals(true, totals.getValue(LocalDate.of(2026, 11, 4)).hasFuture)
    }

    @Test
    fun aDayWithOnlyATransferCountsAsBusyButHasNoNet() {
        val d = LocalDate.of(2026, 11, 5)
        val day = CalendarMath.dayTotals(listOf(CalendarTx(d, TxKind.TRANSFER, -9_000, false))).getValue(d)
        assertEquals(1, day.count)
        assertEquals(0, day.netCents)
    }

    @Test
    fun monthTotalsOnlyCountTheRequestedMonth() {
        val days = CalendarMath.dayTotals(
            listOf(
                CalendarTx(LocalDate.of(2026, 10, 31), TxKind.NORMAL, -9_900, false),
                CalendarTx(LocalDate.of(2026, 11, 1), TxKind.NORMAL, -3_400, false),
                CalendarTx(LocalDate.of(2026, 11, 20), TxKind.NORMAL, 50_000, true),
                CalendarTx(LocalDate.of(2026, 12, 1), TxKind.NORMAL, -100, true)
            )
        )
        val t = CalendarMath.monthTotals(days, YearMonth.of(2026, 11))
        assertEquals(3_400, t.expenseCents)
        assertEquals(50_000, t.incomeCents)
        assertEquals(46_600, t.netCents)
    }

    @Test
    fun scheduledExpandsRepeatsInsideTheWindow() {
        val from = LocalDate.of(2026, 11, 10)
        val to = LocalDate.of(2026, 12, 31)
        val weekly = RecurringEntity(
            title = "Spesa", amountCents = -4_000, cadence = Cadence.WEEKLY, anchorDay = 13,
            nextDueEpochDay = LocalDate.of(2026, 11, 13).toEpochDay(), accountId = 1
        )
        val sub = SubscriptionEntity(
            name = "Musica", amountCents = 1_099, cadence = Cadence.MONTHLY, anchorDay = 14,
            nextDueEpochDay = LocalDate.of(2026, 11, 14).toEpochDay(), accountId = 1
        )
        val loan = LoanEntity(
            name = "Auto", principalCents = 1_000_000, annualRatePercent = 3.0, installmentCents = 25_000, dueDay = 30,
            nextDueEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(), remainingInstallments = 2, accountId = 1
        )
        val out = CalendarMath.scheduled(listOf(sub), listOf(loan), listOf(weekly), from, to)

        // settimanale: 13/11, 20/11, 27/11, 4/12, 11/12, 18/12, 25/12
        val weeklyDates = out.filter { it.title == "Spesa" }.map { it.date }
        assertEquals(LocalDate.of(2026, 11, 13), weeklyDates.first())
        assertEquals(LocalDate.of(2026, 12, 25), weeklyDates.last())
        assertEquals(7, weeklyDates.size)

        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 14)), out.filter { it.title == "Musica" }.map { it.date })
        assertEquals(-1_099, out.first { it.title == "Musica" }.cents)
        // la rata: solo le 2 che restano (30/11 e 30/12)
        assertEquals(listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 30)), out.filter { it.title == "Auto" }.map { it.date })
        // ordinate per data
        assertEquals(out.sortedBy { it.date }, out)
    }

    @Test
    fun scheduledSkipsInactiveItemsAndRecurringTransfers() {
        val d = LocalDate.of(2026, 11, 20).toEpochDay()
        val off = RecurringEntity(title = "Off", amountCents = -100, cadence = Cadence.MONTHLY, anchorDay = 20, nextDueEpochDay = d, accountId = 1, active = false)
        val transfer = RecurringEntity(title = "Risparmio", amountCents = 5_000, cadence = Cadence.MONTHLY, anchorDay = 20, nextDueEpochDay = d, accountId = 1, toAccountId = 2)
        val pausedSub = SubscriptionEntity(name = "Pausa", amountCents = 500, cadence = Cadence.MONTHLY, anchorDay = 20, nextDueEpochDay = d, accountId = 1, active = false)
        val out = CalendarMath.scheduled(listOf(pausedSub), emptyList(), listOf(off, transfer), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 30))
        assertTrue(out.isEmpty())
    }
}
