package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AiReportTest {
    @Test
    fun eurFormatsCentsWithComma() {
        assertEquals("0,00 €", AiReport.eur(0))
        assertEquals("12,05 €", AiReport.eur(1_205))
        assertEquals("-80,00 €", AiReport.eur(-8_000))
        assertEquals("1234,56 €", AiReport.eur(123_456))
    }

    @Test
    fun monthLinesCoverTheLastSixMonthsAndFillGapsWithZero() {
        val current = YearMonth.of(2026, 11)
        val lines = AiReport.monthLines(
            current,
            income = mapOf(YearMonth.of(2026, 10) to 200_000L),
            expense = mapOf(YearMonth.of(2026, 10) to 150_000L, YearMonth.of(2026, 11) to 30_000L),
            saved = mapOf(YearMonth.of(2026, 9) to 10_000L)
        )
        assertEquals(6, lines.size)
        assertEquals(YearMonth.of(2026, 6), lines.first().month)
        assertEquals(current, lines.last().month)
        assertEquals(AiMonthLine(YearMonth.of(2026, 10), 200_000, 150_000, 0), lines[4])
        assertEquals(10_000, lines[3].savedCents)
        assertEquals(0, lines[0].incomeCents)
    }

    private fun item(cat: String, cents: Long, y: Int, m: Int) = SpendItem("x", 1, cat, cents, LocalDate.of(y, m, 10))

    @Test
    fun categoryLinesCompareCurrentMonthWithAverageOfClosedMonths() {
        val current = YearMonth.of(2026, 11)
        val items = listOf(
            item("Casa", 30_000, 2026, 8), item("Casa", 60_000, 2026, 9), item("Casa", 90_000, 2026, 10),
            item("Casa", 20_000, 2026, 11),
            item("Svago", 5_000, 2026, 11)
        )
        val lines = AiReport.categoryLines(items, current)
        val casa = lines.first { it.name == "Casa" }
        assertEquals(20_000, casa.currentCents)
        assertEquals(60_000, casa.averageCents)   // (300+600+900)/3 €
        assertEquals("Casa", lines.first().name)  // la più pesante per primo
        assertEquals(0, lines.first { it.name == "Svago" }.averageCents) // nessun mese chiuso con quella categoria
    }

    @Test
    fun averageUsesOnlyMonthsSinceTheFirstMovement() {
        val current = YearMonth.of(2026, 11)
        val items = listOf(item("Casa", 40_000, 2026, 10), item("Casa", 10_000, 2026, 11))
        // un solo mese chiuso con dati (ottobre): la media è quel mese, non 40/3
        assertEquals(40_000, AiReport.categoryLines(items, current).single().averageCents)
    }

    @Test
    fun categoryLinesAreLimitedToTheTop() {
        val current = YearMonth.of(2026, 11)
        val items = (1..15).map { item("Cat$it", it * 1_000L, 2026, 11) }
        assertEquals(10, AiReport.categoryLines(items, current).size)
        assertEquals("Cat15", AiReport.categoryLines(items, current).first().name)
    }

    @Test
    fun promptContainsKeySectionsAndNoMissingValues() {
        val today = LocalDate.of(2026, 11, 11)
        val snapshot = AiSnapshot(
            today = today, netWorthCents = 1_500_000,
            accounts = listOf(AiAccountLine("Conto", "conto", 500_000)),
            months = AiReport.monthLines(YearMonth.from(today), emptyMap(), emptyMap(), emptyMap(), 2),
            categories = listOf(AiCategoryLine("Casa", 20_000, 60_000)),
            forecastEndOfMonthCents = 480_000, fixedMonthlyCents = 90_000,
            rule = AiRuleLine(YearMonth.of(2026, 10), 55, 30, null),
            loans = listOf(AiLoanLine("Auto", 25_000, 12, 3.5)),
            subscriptions = listOf(AiSubscriptionLine("Musica", 12_000, true)),
            goals = listOf(AiGoalLine("Vacanza", 20_000, 100_000, LocalDate.of(2027, 6, 30))),
            budgets = listOf(AiBudgetLine("Spesa", 30_000, 50_000)),
            savedYearCents = 100_000, provisionedYearCents = 50_000,
            liquidCents = 500_000, averageMonthlyExpenseCents = 180_000, emergencyMonths = 3
        )
        val text = AiReport.buildPrompt(snapshot)
        assertTrue(text.startsWith("Dati al 11/11/2026."))
        for (needle in listOf(
            "Patrimonio netto (somma dei saldi): 15000,00 €", "Conto (conto): 5000,00 €", "mese in corso, parziale",
            "Casa: 200,00 € (media 600,00 €)", "necessità 55%", "risparmio n.d.", "Auto: rata 250,00 €, 12 rate rimaste",
            "Musica: 120,00 € (segnato da rivedere)", "Vacanza: 200,00 € su 1000,00 €, entro il 30/06/2027",
            "Spesa: speso 300,00 € su 500,00 €", "circa 3 mesi", "risparmiato 1000,00 €, accantonato per spese irregolari 500,00 €"
        )) {
            assertTrue("manca: $needle\n$text", text.contains(needle))
        }
        assertTrue(text.endsWith("Scrivi il report."))
        assertFalse(text.contains("null"))
    }

    @Test
    fun emptySectionsAreOmitted() {
        val snapshot = AiSnapshot(
            LocalDate.of(2026, 11, 11), 0, emptyList(), emptyList(), emptyList(), null, null, null,
            emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, 0, null, null
        )
        val text = AiReport.buildPrompt(snapshot)
        assertFalse(text.contains("PRESTITI"))
        assertFalse(text.contains("ABBONAMENTI"))
        assertFalse(text.contains("OBIETTIVI"))
        assertFalse(text.contains("BUDGET"))
        assertFalse(text.contains("null"))
    }
}
