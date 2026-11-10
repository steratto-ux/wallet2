package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Una spesa irregolare prevista: assicurazione, bollo, vacanza... */
data class IrregularInput(val id: Long, val name: String, val estimatedCents: Long, val due: LocalDate)

/**
 * Una riga del piano: [coveredCents] è la parte già coperta dall'accantonamento, [missingCents] quella da mettere
 * da parte entro la scadenza, [monthlyCents] la quota mensile (la mancanza divisa per i mesi che restano).
 */
data class IrregularRow(
    val id: Long,
    val name: String,
    val estimatedCents: Long,
    val due: LocalDate,
    val monthsLeft: Int,
    val coveredCents: Long,
    val missingCents: Long,
    val monthlyCents: Long
)

data class IrregularSummary(
    val rows: List<IrregularRow>,
    val totalCents: Long,      // somma degli importi previsti
    val poolCents: Long,       // accantonamento disponibile
    val coveredCents: Long,    // parte dell'accantonamento assegnata alle spese previste
    val missingCents: Long,    // quanto manca ancora
    val monthlyCents: Long     // quota mensile totale da mettere da parte
)

/** Calcolo del frazionamento mensile delle spese irregolari. Logica pura, senza Android. */
object IrregularPlan {
    /** Mesi interi che restano fino alla scadenza (almeno 1, anche se la data è oggi o già passata). */
    fun monthsLeft(today: LocalDate, due: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(today, due)
        if (days <= 0) return 1
        return max(1, ceil(days / 30.4375).toInt())
    }

    /**
     * L'accantonamento disponibile copre prima le spese che scadono prima, ciascuna fino al suo importo;
     * per quello che manca si divide per i mesi restanti.
     */
    fun plan(items: List<IrregularInput>, poolCents: Long, today: LocalDate): IrregularSummary {
        var pool = max(0L, poolCents)
        val rows = items.filter { it.estimatedCents > 0 }
            .sortedWith(compareBy({ it.due }, { it.id }))
            .map { i ->
                val covered = min(pool, i.estimatedCents)
                pool -= covered
                val missing = i.estimatedCents - covered
                val months = monthsLeft(today, i.due)
                val monthly = if (missing <= 0) 0L else (missing + months - 1) / months
                IrregularRow(i.id, i.name, i.estimatedCents, i.due, months, covered, missing, monthly)
            }
        return IrregularSummary(
            rows = rows,
            totalCents = rows.sumOf { it.estimatedCents },
            poolCents = max(0L, poolCents),
            coveredCents = rows.sumOf { it.coveredCents },
            missingCents = rows.sumOf { it.missingCents },
            monthlyCents = rows.sumOf { it.monthlyCents }
        )
    }

    /** Quanto dell'accantonamento si può usare per pagare una spesa di [costCents]: al massimo il disponibile. */
    fun usable(poolCents: Long, costCents: Long): Long = max(0L, min(poolCents, costCents))
}
