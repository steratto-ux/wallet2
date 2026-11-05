package com.example.finanzecasa.domain

import kotlin.math.pow
import kotlin.math.roundToLong

/** Piano di ammortamento "alla francese" (rata costante, tasso nominale annuo, rate mensili). */
object LoanMath {
    private fun monthlyRate(annualPercent: Double) = annualPercent / 100.0 / 12.0

    /** Rata costante che estingue [principalCents] in [n] rate. */
    fun installmentCents(principalCents: Long, annualRatePercent: Double, n: Int): Long {
        require(n > 0) { "Servono almeno una rata" }
        val r = monthlyRate(annualRatePercent)
        val pmt = if (r == 0.0) principalCents.toDouble() / n
        else principalCents * r / (1 - (1 + r).pow(-n))
        return pmt.roundToLong()
    }

    /** Capitale ancora da restituire dopo aver pagato tutte le rate tranne le [remaining] ultime. */
    fun remainingPrincipalCents(installmentCents: Long, annualRatePercent: Double, remaining: Int): Long {
        if (remaining <= 0) return 0
        val r = monthlyRate(annualRatePercent)
        val pv = if (r == 0.0) installmentCents.toDouble() * remaining
        else installmentCents * (1 - (1 + r).pow(-remaining)) / r
        return pv.roundToLong()
    }
}
