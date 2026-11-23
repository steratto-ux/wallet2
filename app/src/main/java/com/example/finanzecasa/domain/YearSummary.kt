package com.example.finanzecasa.domain

import kotlin.math.roundToInt

/** Una categoria con il totale dell'anno e quello dell'anno prima (stessi giorni se l'anno è in corso). */
data class CategoryLine(val name: String, val cents: Long, val previousCents: Long)

/** Tutto ciò che serve al riepilogo annuale in PDF. Importi in centesimi, uscite positive. */
data class YearSummaryData(
    val year: Int,
    /** True per l'anno in corso: il confronto con l'anno prima è sugli stessi giorni. */
    val inProgress: Boolean,
    val incomeCents: Long,
    val expenseCents: Long,
    val previousIncomeCents: Long,
    val previousExpenseCents: Long,
    /** Trasferimenti segnati come risparmio e come accantonamento nell'anno. */
    val savedCents: Long,
    val provisionedCents: Long,
    val months: List<CashflowPoint>,
    val expenseCategories: List<CategoryLine>,
    val incomeCategories: List<CategoryLine>
) {
    val savingsCents: Long get() = incomeCents - expenseCents
    val previousSavingsCents: Long get() = previousIncomeCents - previousExpenseCents

    /** Quota delle entrate che resta dopo le spese, in percentuale (null senza entrate). */
    val savingsRatePercent: Int?
        get() = if (incomeCents > 0) (savingsCents * 100.0 / incomeCents).roundToInt() else null
}

object YearSummary {
    /** Le [max] categorie più grandi; le altre confluiscono in "Altre" (con il loro anno prima). Zeri esclusi. */
    fun topWithOthers(lines: List<CategoryLine>, max: Int): List<CategoryLine> {
        val sorted = lines.filter { it.cents > 0 }.sortedByDescending { it.cents }
        if (sorted.size <= max) return sorted
        val top = sorted.take(max)
        val rest = sorted.drop(max)
        return top + CategoryLine("Altre", rest.sumOf { it.cents }, rest.sumOf { it.previousCents })
    }

    /** "+12%", "-5%", "0%" oppure "—" se l'anno prima era a zero. */
    fun changeLabel(current: Long, previous: Long): String {
        val pct = ReportMath.percentChange(current, previous) ?: return "—"
        return (if (pct > 0) "+" else "") + pct + "%"
    }

    /** Mese con il saldo migliore e mese con il saldo peggiore tra quelli con movimenti; null se non ce ne sono. */
    fun bestAndWorstMonth(points: List<CashflowPoint>): Pair<CashflowPoint, CashflowPoint>? {
        val used = points.filter { !it.future && (it.incomeCents > 0 || it.expenseCents > 0) }
        if (used.isEmpty()) return null
        return used.maxByOrNull { it.netCents }!! to used.minByOrNull { it.netCents }!!
    }
}
