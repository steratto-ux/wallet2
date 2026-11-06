package com.example.finanzecasa.domain

import com.example.finanzecasa.data.LoanEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SmallHelpersTest {
    // --- Tag
    @Test fun tagsAreNormalized() {
        assertEquals("Vacanza,regalo", Tags.normalize(" Vacanza, #regalo ,vacanza;  "))
        assertEquals("", Tags.normalize("  , ,"))
    }

    @Test fun tagsAreSplitAndMerged() {
        assertEquals(listOf("a", "b"), Tags.split("a,b"))
        assertEquals(emptyList<String>(), Tags.split(""))
        assertEquals(listOf("auto", "casa"), Tags.distinct(listOf("casa,auto", "Auto", "")))
        assertEquals("casa,auto", Tags.add("casa", "auto"))
        assertEquals("casa", Tags.add("casa", "CASA"))
    }

    // --- Ordine account
    @Test fun moveUpAndDown() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(listOf(2L, 1L, 3L), AccountOrder.move(ids, 2, -1))
        assertEquals(listOf(1L, 3L, 2L), AccountOrder.move(ids, 2, 1))
    }

    @Test fun moveStopsAtTheEnds() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(ids, AccountOrder.move(ids, 1, -1))
        assertEquals(ids, AccountOrder.move(ids, 3, 1))
        assertEquals(ids, AccountOrder.move(ids, 99, 1))
    }

    // --- Debiti dei prestiti
    private fun loan(rate: Double, installment: Long, remaining: Int) = LoanEntity(
        name = "Prestito", principalCents = 0, annualRatePercent = rate, installmentCents = installment,
        dueDay = 1, nextDueEpochDay = 0, remainingInstallments = remaining, accountId = 1
    )

    @Test fun zeroRateDebtIsInstallmentsTimesRemaining() {
        assertEquals(1_200_00L, NetWorthMath.loanDebtCents(listOf(loan(0.0, 100_00, 12))))
    }

    @Test fun finishedLoansHaveNoDebt() {
        assertEquals(0L, NetWorthMath.loanDebtCents(listOf(loan(5.0, 100_00, 0))))
    }

    @Test fun netWorthRespectsTheSwitch() {
        val loans = listOf(loan(0.0, 100_00, 10))
        assertEquals(5_000_00L, NetWorthMath.netWorthCents(5_000_00, loans, includeLoans = false))
        assertEquals(4_000_00L, NetWorthMath.netWorthCents(5_000_00, loans, includeLoans = true))
    }
}
