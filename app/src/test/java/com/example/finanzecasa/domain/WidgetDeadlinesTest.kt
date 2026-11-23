package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetDeadlinesTest {
    private val today = LocalDate.of(2026, 11, 23)
    private fun item(title: String, date: LocalDate) = CalendarScheduled(title, date, -1_000, "Abbonamento")

    @Test fun `si prendono le prime tre scadenze in ordine di data`() {
        val list = listOf(
            item("D", today.plusDays(9)), item("A", today), item("C", today.plusDays(5)), item("B", today.plusDays(1))
        )
        assertEquals(listOf("A", "B", "C"), WidgetDeadlines.pick(list, today).map { it.title })
    }

    @Test fun `restano fuori il passato e oltre trenta giorni`() {
        val list = listOf(
            item("ieri", today.minusDays(1)), item("limite", today.plusDays(30)), item("troppo", today.plusDays(31))
        )
        assertEquals(listOf("limite"), WidgetDeadlines.pick(list, today).map { it.title })
        assertTrue(WidgetDeadlines.pick(emptyList(), today).isEmpty())
    }

    @Test fun `le etichette dei giorni sono leggibili`() {
        assertEquals("Oggi", WidgetDeadlines.whenLabel(today, today))
        assertEquals("Domani", WidgetDeadlines.whenLabel(today.plusDays(1), today))
        assertEquals("Tra 6 giorni", WidgetDeadlines.whenLabel(today.plusDays(6), today))
        assertEquals("30 nov", WidgetDeadlines.whenLabel(LocalDate.of(2026, 11, 30), today))
        assertEquals("5 dic", WidgetDeadlines.whenLabel(LocalDate.of(2026, 12, 5), today))
    }
}
