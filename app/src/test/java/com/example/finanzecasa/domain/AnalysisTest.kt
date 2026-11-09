package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AnalysisTest {
    private fun spend(title: String, cents: Long, y: Int, m: Int, d: Int, cat: String = "Altro", catId: Long? = 1) =
        SpendItem(title, catId, cat, cents, LocalDate.of(y, m, d))

    // --- Chiave del titolo
    @Test fun titleKeyIgnoresCaseDigitsAndPunctuation() {
        assertEquals("netflix", TitleKey.of("Netflix 11/2026"))
        assertEquals("rata mutuo", TitleKey.of("  RATA - Mutuo  "))
        assertEquals("", TitleKey.of("123"))
    }

    // --- Spese fisse
    private val rent = listOf(
        spend("Affitto", 70_000, 2026, 8, 5), spend("Affitto", 70_000, 2026, 9, 5),
        spend("Affitto", 70_000, 2026, 10, 6), spend("Affitto", 70_000, 2026, 11, 5)
    )

    @Test fun monthlyStablePaymentIsDetectedAsFixed() {
        val fixed = FixedSpendDetector.detect(rent, emptySet())
        assertEquals(1, fixed.size)
        assertEquals(70_000L, fixed.single().typicalCents)
        assertEquals(5, fixed.single().typicalDay)
    }

    @Test fun twoMonthsAreNotEnough() {
        assertTrue(FixedSpendDetector.detect(rent.take(2), emptySet()).isEmpty())
    }

    @Test fun unstableAmountsAreNotFixed() {
        val bills = listOf(
            spend("Spesa", 5_000, 2026, 8, 3), spend("Spesa", 30_000, 2026, 9, 3), spend("Spesa", 9_000, 2026, 10, 3)
        )
        assertTrue(FixedSpendDetector.detect(bills, emptySet()).isEmpty())
    }

    @Test fun manyPurchasesPerMonthAreNotFixed() {
        val coffee = (1..3).flatMap { m -> (1..10).map { d -> spend("Caffè", 120, 2026, 7 + m, d) } }
        assertTrue(FixedSpendDetector.detect(coffee, emptySet()).isEmpty())
    }

    @Test fun configuredItemsAreFixedEvenWithOneOccurrence() {
        val items = listOf(spend("Netflix", 1_299, 2026, 11, 3))
        val fixed = FixedSpendDetector.detect(items, setOf("netflix"))
        assertTrue(fixed.single().configured)
        assertEquals(setOf("netflix"), FixedSpendDetector.fixedKeys(fixed, setOf("netflix")))
    }

    @Test fun splitSeparatesFixedFromVariable() {
        val month = listOf(
            spend("Affitto", 70_000, 2026, 11, 5, "Casa"),
            spend("Esselunga", 8_000, 2026, 11, 7, "Spesa"),
            spend("Conad", 4_000, 2026, 11, 9, "Spesa"),
            spend("Cinema", 2_000, 2026, 11, 10, "Svago")
        )
        val s = SpendSplit.split(month, setOf("affitto"))
        assertEquals(70_000L, s.fixedCents)
        assertEquals(14_000L, s.variableCents)
        assertEquals("Spesa", s.variableLines.first().label)
        assertEquals(12_000L, s.variableLines.first().cents)
        assertEquals(83, s.fixedPercent)
    }

    // --- Previsione
    @Test fun dailyVariableTrustsHistoryEarlyInTheMonth() {
        // giorno 2: peso 0,2 -> 0,2*5000 + 0,8*1000 = 1800
        assertEquals(1_800L, ForecastMath.dailyVariable(10_000, 2, 1_000))
        // dal giorno 10 conta solo il mese in corso
        assertEquals(2_000L, ForecastMath.dailyVariable(20_000, 10, 1_000))
        assertEquals(2_500L, ForecastMath.dailyVariable(5_000, 2, null))
    }

    @Test fun forecastAddsUpcomingAndSubtractsProjectedSpending() {
        val today = LocalDate.of(2026, 11, 10) // 30 giorni, restano 20
        val upcoming = listOf(
            Upcoming("Affitto", today.plusDays(5), -70_000),
            Upcoming("Stipendio", today.plusDays(17), 200_000)
        )
        val f = ForecastMath.forecast(100_000, upcoming, variableSoFarCents = 20_000, today = today, baselineDailyCents = null)
        assertEquals(2_000L, f.dailyVariableCents)
        assertEquals(20, f.remainingDays)
        assertEquals(40_000L, f.variableProjectionCents)
        assertEquals(130_000L, f.upcomingCents)
        assertEquals(100_000L + 130_000L - 40_000L, f.projectedEndCents)
        assertEquals(today.plusDays(5), f.upcoming.first().date)
    }

    @Test fun configuredUpcomingKeepsOnlyThisMonthAndActiveItems() {
        val today = LocalDate.of(2026, 11, 10)
        fun epoch(d: LocalDate) = d.toEpochDay()
        val subs = listOf(
            SubscriptionEntity(name = "Netflix", amountCents = 1_299, cadence = Cadence.MONTHLY, anchorDay = 20,
                nextDueEpochDay = epoch(LocalDate.of(2026, 11, 20)), accountId = 1),
            SubscriptionEntity(name = "Palestra", amountCents = 3_000, cadence = Cadence.MONTHLY, anchorDay = 2,
                nextDueEpochDay = epoch(LocalDate.of(2026, 12, 2)), accountId = 1),
            SubscriptionEntity(name = "Vecchio", amountCents = 500, cadence = Cadence.MONTHLY, anchorDay = 25,
                nextDueEpochDay = epoch(LocalDate.of(2026, 11, 25)), accountId = 1, active = false)
        )
        val loans = listOf(
            LoanEntity(name = "Auto", principalCents = 0, annualRatePercent = 0.0, installmentCents = 25_000, dueDay = 28,
                nextDueEpochDay = epoch(LocalDate.of(2026, 11, 28)), remainingInstallments = 5, accountId = 1),
            LoanEntity(name = "Finito", principalCents = 0, annualRatePercent = 0.0, installmentCents = 9_000, dueDay = 28,
                nextDueEpochDay = epoch(LocalDate.of(2026, 11, 28)), remainingInstallments = 0, accountId = 1)
        )
        val recs = listOf(
            RecurringEntity(title = "Stipendio", amountCents = 200_000, cadence = Cadence.MONTHLY, anchorDay = 27,
                nextDueEpochDay = epoch(LocalDate.of(2026, 11, 27)), accountId = 1)
        )
        val up = ForecastMath.configuredUpcoming(subs, loans, recs, today)
        assertEquals(setOf("Netflix", "Auto", "Stipendio"), up.map { it.label }.toSet())
        assertEquals(200_000L - 1_299L - 25_000L, up.sumOf { it.cents })
    }

    @Test fun weeklyRecurringCountsEveryOccurrenceLeftInTheMonth() {
        val today = LocalDate.of(2026, 11, 10)
        val recs = listOf(
            RecurringEntity(title = "Paghetta", amountCents = -1_000, cadence = Cadence.WEEKLY, anchorDay = 12,
                nextDueEpochDay = LocalDate.of(2026, 11, 12).toEpochDay(), accountId = 1)
        )
        val up = ForecastMath.configuredUpcoming(emptyList(), emptyList(), recs, today)
        // 12, 19 e 26 novembre
        assertEquals(listOf(12, 19, 26), up.map { it.date.dayOfMonth })
        assertEquals(-3_000L, up.sumOf { it.cents })
    }

    @Test fun patternUpcomingSkipsConfiguredSeenAndPassedItems() {
        val today = LocalDate.of(2026, 11, 10)
        val fixed = listOf(
            FixedItem("affitto", "Affitto", 70_000, 15, 4, configured = false),
            FixedItem("palestra", "Palestra", 3_000, 15, 4, configured = true),
            FixedItem("condominio", "Condominio", 9_000, 15, 4, configured = false),
            FixedItem("bolletta", "Bolletta", 5_000, 3, 4, configured = false)
        )
        val up = ForecastMath.patternUpcoming(fixed, seenThisMonth = setOf("condominio"), today = today)
        assertEquals(listOf("Affitto"), up.map { it.label })
        assertEquals(LocalDate.of(2026, 11, 15), up.single().date)
        assertEquals(-70_000L, up.single().cents)
    }

    @Test fun baselineDailyUsesOnlyVariableSpendOfClosedMonths() {
        val items = listOf(
            spend("Affitto", 60_000, 2026, 10, 5), spend("Spesa", 31_000, 2026, 10, 9),
            spend("Spesa", 9_999, 2026, 11, 2)
        )
        // ottobre ha 31 giorni: 31.000 / 31 = 1.000
        assertEquals(1_000L, ForecastMath.baselineDaily(items, listOf(YearMonth.of(2026, 10)), setOf("affitto")))
        assertNull(ForecastMath.baselineDaily(items, emptyList(), emptySet()))
        assertNull(ForecastMath.baselineDaily(items, listOf(YearMonth.of(2026, 9)), emptySet()))
    }

    // --- Risparmio e simulatore
    private val twoMonths = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10))

    private fun sampleBaseline(): Baseline {
        val items = listOf(
            spend("Affitto", 70_000, 2026, 9, 5, "Casa"), spend("Affitto", 70_000, 2026, 10, 5, "Casa"),
            spend("Esselunga", 40_000, 2026, 9, 8, "Spesa"), spend("Esselunga", 60_000, 2026, 10, 8, "Spesa"),
            spend("Cena", 10_000, 2026, 9, 12, "Ristoranti"), spend("Cena", 30_000, 2026, 10, 12, "Ristoranti")
        )
        val income = mapOf(YearMonth.of(2026, 9) to 200_000L, YearMonth.of(2026, 10) to 200_000L)
        return SavingsPlanner.baseline(items, income, twoMonths, setOf("affitto"))
    }

    @Test fun baselineAveragesOverMonths() {
        val b = sampleBaseline()
        assertEquals(200_000L, b.incomeCents)
        assertEquals(70_000L, b.fixedCents)
        assertEquals(listOf("Spesa", "Ristoranti"), b.variable.map { it.name })
        assertEquals(50_000L, b.variable[0].monthlyCents)
        assertEquals(20_000L, b.variable[1].monthlyCents)
        assertEquals(200_000L - 70_000L - 70_000L, b.savingsCents)
    }

    @Test fun suggestedCutsAreProportionalAndReachTheGap() {
        val v = listOf(CategoryAvg(1, "Spesa", 50_000), CategoryAvg(2, "Ristoranti", 20_000))
        val plan = SavingsPlanner.suggestCuts(v, gapCents = 14_000)
        assertEquals(0L, plan.shortfallCents)
        assertEquals(14_000L, plan.cuts.sumOf { it.cutCents })
        assertEquals(10_000L, plan.cuts.first { it.name == "Spesa" }.cutCents)
        assertEquals(4_000L, plan.cuts.first { it.name == "Ristoranti" }.cutCents)
        assertEquals(20, plan.cuts.first { it.name == "Spesa" }.percent)
    }

    @Test fun cutsAreCappedAndShortfallIsReported() {
        val v = listOf(CategoryAvg(1, "Spesa", 10_000), CategoryAvg(2, "Svago", 10_000))
        val plan = SavingsPlanner.suggestCuts(v, gapCents = 30_000) // al massimo il 50% di 20.000 = 10.000
        assertEquals(10_000L, plan.achievedCents)
        assertEquals(20_000L, plan.shortfallCents)
    }

    @Test fun roundingLeavesNoLostCents() {
        val v = listOf(CategoryAvg(1, "A", 3_333), CategoryAvg(2, "B", 3_333), CategoryAvg(3, "C", 3_334))
        val plan = SavingsPlanner.suggestCuts(v, gapCents = 1_001)
        assertEquals(1_001L, plan.cuts.sumOf { it.cutCents })
        assertEquals(0L, plan.shortfallCents)
    }

    @Test fun noGapMeansNoCuts() {
        val plan = SavingsPlanner.suggestCuts(listOf(CategoryAvg(1, "A", 1_000)), gapCents = 0)
        assertTrue(plan.cuts.isEmpty())
        assertEquals(0L, plan.shortfallCents)
    }

    @Test fun savingsWithCutsAddsThePercentages() {
        val b = sampleBaseline() // risparmio 60.000; Spesa 50.000, Ristoranti 20.000
        assertEquals(60_000L, SavingsPlanner.savingsWithCuts(b, listOf(0, 0)))
        assertEquals(60_000L + 10_000L + 6_000L, SavingsPlanner.savingsWithCuts(b, listOf(20, 30)))
    }

    // --- Piccole spese ripetute
    @Test fun repeatedSmallSpendsAreFoundAndProjected() {
        val items = (1..12).map { spend("Caffè bar", 150, 2026, 10 + (it - 1) / 6, 1 + it) } +
            listOf(spend("Cena", 6_000, 2026, 10, 3), spend("Cena", 6_500, 2026, 10, 10), spend("Cena", 7_000, 2026, 10, 17), spend("Cena", 6_000, 2026, 10, 24))
        val found = RepeatedSpends.find(items, emptySet(), spanMonths = 2.0)
        assertEquals(listOf("Caffè bar"), found.map { it.title }) // la cena supera la soglia di 30 euro
        val c = found.single()
        assertEquals(12, c.count)
        assertEquals(1_800L, c.totalCents)
        assertEquals(150L, c.averageCents)
        assertEquals(900L, c.perMonthCents)
        assertEquals(10_800L, c.perYearCents)
    }

    @Test fun fixedAndRareSpendsAreExcluded() {
        val items = (1..5).map { spend("Netflix", 1_299, 2026, 10, it) } + listOf(spend("Libro", 1_500, 2026, 10, 3))
        assertTrue(RepeatedSpends.find(items, setOf("netflix"), 1.0).isEmpty())
        assertFalse(RepeatedSpends.find(items, emptySet(), 1.0).any { it.title == "Libro" })
    }
}
