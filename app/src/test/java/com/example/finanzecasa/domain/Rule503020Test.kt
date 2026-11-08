package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class Rule503020Test {
    private fun item(cat: String, cents: Long) = SpendItem(cat, 1L, cat, cents, LocalDate.of(2026, 10, 5))

    @Test
    fun autoClassificationRecognisesCommonNeeds() {
        assertEquals(Bucket.NEED, BucketRules.auto("Affitto"))
        assertEquals(Bucket.NEED, BucketRules.auto("Bollette luce e gas"))
        assertEquals(Bucket.NEED, BucketRules.auto("Spesa alimentare"))
        assertEquals(Bucket.NEED, BucketRules.auto("Trasporti"))
        assertEquals(Bucket.NEED, BucketRules.auto("Salute"))
        assertEquals(Bucket.NEED, BucketRules.auto("Auto"))
    }

    @Test
    fun autoClassificationDefaultsToWant() {
        assertEquals(Bucket.WANT, BucketRules.auto("Ristoranti"))
        assertEquals(Bucket.WANT, BucketRules.auto("Hobby"))
        assertEquals(Bucket.WANT, BucketRules.auto("Streaming"))
        // "automobilismo" non è la parola "auto"
        assertEquals(Bucket.WANT, BucketRules.auto("Automobilismo"))
    }

    @Test
    fun manualSettingWinsOverTheName() {
        assertEquals(Bucket.WANT, BucketRules.effective(BucketRules.WANT, "Affitto"))
        assertEquals(Bucket.NEED, BucketRules.effective(BucketRules.NEED, "Hobby"))
        assertEquals(Bucket.NEED, BucketRules.effective(BucketRules.AUTO, "Affitto"))
    }

    @Test
    fun monthIsSplitByBucketAndPercentagesAreOnIncome() {
        val items = listOf(item("Affitto", 80_000), item("Spesa", 40_000), item("Ristoranti", 30_000))
        val m = Rule503020.compute(
            items, { BucketRules.auto(it.categoryName) }, incomeCents = 200_000, savingsCents = 30_000
        )
        assertEquals(120_000, m.needCents)
        assertEquals(30_000, m.wantCents)
        assertEquals(60, m.percentOfIncome(m.needCents))
        assertEquals(15, m.percentOfIncome(m.wantCents))
        assertEquals(15, m.percentOfIncome(m.savingsCents))
        assertEquals(20_000, m.leftCents)
        assertEquals("Affitto", m.needLines.first().label)
    }

    @Test
    fun noIncomeGivesNoPercentages() {
        val m = Rule503020.compute(listOf(item("Spesa", 1_000)), { Bucket.NEED }, 0, 0)
        assertNull(m.percentOfIncome(m.needCents))
        assertEquals(RuleStatus.NO_INCOME, Rule503020.savingsStatus(m))
    }

    @Test
    fun statusesFollowTheTargets() {
        val m = Rule503020.compute(
            listOf(item("Spesa", 110_000), item("Hobby", 20_000)), { BucketRules.auto(it.categoryName) },
            incomeCents = 200_000, savingsCents = 30_000
        )
        // Necessità: 55% > 50%; desideri: 10% <= 30%; risparmio: 15% < 20%
        assertEquals(RuleStatus.OVER, Rule503020.spendStatus(m, m.needCents, Rule503020.NEED_PERCENT))
        assertEquals(RuleStatus.OK, Rule503020.spendStatus(m, m.wantCents, Rule503020.WANT_PERCENT))
        assertEquals(RuleStatus.SHORT, Rule503020.savingsStatus(m))
        assertEquals(40_000, Rule503020.target(200_000, 20))
    }

    @Test
    fun spendingMoreThanIncomeLeavesNegativeRemainder() {
        val m = Rule503020.compute(listOf(item("Spesa", 150_000)), { Bucket.NEED }, 100_000, 0)
        assertEquals(-50_000, m.leftCents)
    }

    @Test
    fun subcategorySettingWinsThenParentThenNames() {
        // La sotto-categoria scelta a mano vince su tutto
        assertEquals(Bucket.WANT, BucketRules.resolve(BucketRules.WANT, "Bollette", BucketRules.NEED, "Casa"))
        assertEquals(Bucket.NEED, BucketRules.resolve(BucketRules.NEED, "Cinema", BucketRules.WANT, "Svago"))
        // Senza scelta sulla sotto-categoria vale quella della principale
        assertEquals(Bucket.WANT, BucketRules.resolve(BucketRules.AUTO, "Bollette", BucketRules.WANT, "Casa"))
        assertEquals(Bucket.NEED, BucketRules.resolve(BucketRules.AUTO, "Cinema", BucketRules.NEED, "Svago"))
        // Tutto automatico: basta un nome da necessità
        assertEquals(Bucket.NEED, BucketRules.resolve(BucketRules.AUTO, "Farmacia", BucketRules.AUTO, "Altro"))
        assertEquals(Bucket.NEED, BucketRules.resolve(BucketRules.AUTO, "Cena", BucketRules.AUTO, "Spesa"))
        assertEquals(Bucket.WANT, BucketRules.resolve(BucketRules.AUTO, "Cena", BucketRules.AUTO, "Svago"))
        assertEquals(Bucket.NEED, BucketRules.resolve(null, null, BucketRules.AUTO, "Affitto"))
    }
}
