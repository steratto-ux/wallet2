package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleTest {
    @Test
    fun monthlyMovesToTheNextMonthKeepingTheAnchorDay() {
        assertEquals(LocalDate.of(2026, 12, 30), RecurringSchedule.following(LocalDate.of(2026, 11, 30), Cadence.MONTHLY, 30))
    }

    @Test
    fun shortMonthsClampButTheAnchorIsKept() {
        val feb = RecurringSchedule.following(LocalDate.of(2027, 1, 30), Cadence.MONTHLY, 30)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 30), RecurringSchedule.following(feb, Cadence.MONTHLY, 30))
    }

    @Test
    fun weeklyMovesSevenDaysKeepingTheWeekday() {
        val next = RecurringSchedule.following(LocalDate.of(2026, 11, 9), Cadence.WEEKLY, 9)
        assertEquals(LocalDate.of(2026, 11, 16), next)
        assertEquals(next.dayOfWeek, LocalDate.of(2026, 11, 9).dayOfWeek)
        assertEquals(LocalDate.of(2027, 1, 4), RecurringSchedule.following(LocalDate.of(2026, 12, 28), Cadence.WEEKLY, 28))
    }

    @Test
    fun yearlyMovesToTheNextYear() {
        assertEquals(LocalDate.of(2027, 3, 15), RecurringSchedule.following(LocalDate.of(2026, 3, 15), Cadence.YEARLY, 15))
    }

    @Test
    fun foreverNeverChanges() {
        assertEquals(RecurringSchedule.FOREVER, RecurringSchedule.remainingAfter(RecurringSchedule.FOREVER))
    }

    @Test
    fun threeInstallmentsEndAfterTheThird() {
        var remaining = 3
        remaining = RecurringSchedule.remainingAfter(remaining)
        assertEquals(2, remaining)
        remaining = RecurringSchedule.remainingAfter(remaining)
        assertEquals(1, remaining)
        remaining = RecurringSchedule.remainingAfter(remaining)
        assertEquals(RecurringSchedule.FINISHED, remaining)
        // una volta conclusa resta conclusa
        assertEquals(RecurringSchedule.FINISHED, RecurringSchedule.remainingAfter(remaining))
    }
}
