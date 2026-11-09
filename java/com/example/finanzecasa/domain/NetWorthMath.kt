package com.example.finanzecasa.domain

import com.example.finanzecasa.data.LoanEntity

/** Patrimonio netto con i debiti dei prestiti (opzionale). */
object NetWorthMath {
    /** Capitale ancora da restituire su tutti i prestiti con rate residue. */
    fun loanDebtCents(loans: List<LoanEntity>): Long = loans.sumOf {
        LoanMath.remainingPrincipalCents(it.installmentCents, it.annualRatePercent, it.remainingInstallments)
    }

    fun netWorthCents(accountsTotalCents: Long, loans: List<LoanEntity>, includeLoans: Boolean): Long =
        if (includeLoans) accountsTotalCents - loanDebtCents(loans) else accountsTotalCents
}
