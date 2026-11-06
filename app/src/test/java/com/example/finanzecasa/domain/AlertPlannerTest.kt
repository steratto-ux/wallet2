package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AlertPlannerTest {
    private val fmt: (Long) -> String = { "€${it / 100}" }
    private val month = YearMonth.of(2026, 11)

    @Test fun belowThresholdNoAlert() {
        val alerts = AlertPlanner.budgetAlerts(listOf(BudgetInput(1, "Spesa", 50_000, 39_999)), month, fmt)
        assertTrue(alerts.isEmpty())
    }

    @Test fun atEightyPercentWarns() {
        val alerts = AlertPlanner.budgetAlerts(listOf(BudgetInput(1, "Spesa", 50_000, 40_000)), month, fmt)
        assertEquals(1, alerts.size)
        assertEquals("b|1|2026-11|80", alerts.single().key)
    }

    @Test fun overBudgetUsesTheHundredKeyOnly() {
        val alerts = AlertPlanner.budgetAlerts(listOf(BudgetInput(1, "Spesa", 50_000, 61_000)), month, fmt)
        assertEquals(listOf("b|1|2026-11|100"), alerts.map { it.key })
    }

    @Test fun zeroBudgetIsIgnored() {
        assertTrue(AlertPlanner.budgetAlerts(listOf(BudgetInput(1, "x", 0, 100)), month, fmt).isEmpty())
    }

    @Test fun onlyTodayAndTomorrowAreReminded() {
        val today = LocalDate.of(2026, 11, 6)
        val dues = listOf(
            DueInput("sub", 1, "Netflix", 1299, today.plusDays(1)),
            DueInput("loan", 2, "Mutuo", 80_000, today),
            DueInput("rec", 3, "Affitto", 70_000, today.plusDays(2)),
            DueInput("card", 4, "Amex", null, today.plusDays(1))
        )
        val alerts = AlertPlanner.dueAlerts(dues, today, fmt)
        assertEquals(listOf("d|sub|1|${today.plusDays(1).toEpochDay()}", "d|loan|2|${today.toEpochDay()}", "d|card|4|${today.plusDays(1).toEpochDay()}"), alerts.map { it.key })
        assertTrue(alerts[0].text.contains("domani"))
        assertTrue(alerts[1].text.contains("oggi"))
    }

    @Test fun pruneDropsOldMonthsAndOldDueKeys() {
        val today = LocalDate.of(2026, 11, 6)
        val keep = setOf("b|1|2026-11|80", "d|sub|1|${today.toEpochDay()}")
        val drop = setOf("b|1|2026-10|100", "d|sub|1|${today.minusDays(30).toEpochDay()}", "strano")
        assertEquals(keep, AlertPlanner.prune(keep + drop, today))
    }
}
