package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderLogicTest {
    private val now = LocalDateTime.of(2026, 11, 10, 21, 30)
    private val todayDay = LocalDate.of(2026, 11, 10).toEpochDay()

    @Test
    fun remindsOnlyWhenNothingWasEnteredToday() {
        assertTrue(ReminderLogic.shouldRemind(true, 21, now, todayDay - 1, todayDay - 1))
        assertFalse(ReminderLogic.shouldRemind(true, 21, now, todayDay, todayDay - 1))
    }

    @Test
    fun doesNotRemindTwiceBeforeTheHourOrWhenOff() {
        assertFalse(ReminderLogic.shouldRemind(true, 21, now, todayDay - 1, todayDay))
        assertFalse(ReminderLogic.shouldRemind(true, 22, now, todayDay - 1, todayDay - 1))
        assertFalse(ReminderLogic.shouldRemind(false, 21, now, todayDay - 1, todayDay - 1))
    }

    @Test
    fun waitsUntilTheNextOccurrenceOfTheHour() {
        assertEquals(30L, ReminderLogic.minutesUntilNext(LocalDateTime.of(2026, 11, 10, 20, 30), 21))
        assertEquals(1_440L, ReminderLogic.minutesUntilNext(LocalDateTime.of(2026, 11, 10, 21, 0), 21))
        assertEquals(1_380L, ReminderLogic.minutesUntilNext(LocalDateTime.of(2026, 11, 10, 22, 0), 21))
    }
}
