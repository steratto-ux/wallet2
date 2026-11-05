package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LoanMathTest {
    @Test fun `rata di 10000 euro al 6 per cento in 12 mesi`() {
        assertEquals(86066L, LoanMath.installmentCents(1_000_000, 6.0, 12))
    }

    @Test fun `tasso zero divide il capitale per le rate`() {
        assertEquals(10_000L, LoanMath.installmentCents(120_000, 0.0, 12))
        assertEquals(360_490L, LoanMath.remainingPrincipalCents(36_049, 0.0, 10))
    }

    @Test fun `il debito residuo a inizio piano coincide col capitale (tolleranza arrotondamento)`() {
        val rata = LoanMath.installmentCents(1_000_000, 6.0, 12)
        assertTrue(abs(LoanMath.remainingPrincipalCents(rata, 6.0, 12) - 1_000_000) <= 12)
        assertEquals(507_478L, LoanMath.remainingPrincipalCents(rata, 6.0, 6))
        assertEquals(0L, LoanMath.remainingPrincipalCents(rata, 6.0, 0))
    }
}
