package com.example.finanzecasa.domain

import com.example.finanzecasa.data.SpendRow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class UnusualCategory(val categoryId: Long, val name: String, val monthToDateCents: Long, val averageCents: Long)

/**
 * Categorie in cui la spesa del mese in corso ha già superato di molto la media mensile dei mesi chiusi.
 * Per evitare falsi allarmi serve una media di almeno 30 €, un superamento di almeno il 50% e di almeno 25 €.
 */
object UnusualSpend {
    const val BASELINE_MONTHS = 3
    const val FACTOR_PERCENT = 150
    const val MIN_AVERAGE_CENTS = 3_000L
    const val MIN_EXTRA_CENTS = 2_500L

    fun detect(rows: List<SpendRow>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<UnusualCategory> {
        val current = YearMonth.from(today)
        val byMonth = rows.groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate()) }
        val closed = (1..BASELINE_MONTHS).map { current.minusMonths(it.toLong()) }
            .filter { byMonth[it].orEmpty().isNotEmpty() }
        if (closed.isEmpty()) return emptyList()

        val averages = closed.flatMap { byMonth[it].orEmpty() }
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { it.cents } / closed.size }
        val names = rows.filter { it.categoryId != null }.associate { it.categoryId!! to it.categoryName }

        return byMonth[current].orEmpty()
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapNotNull { (id, list) ->
                val spent = list.sumOf { it.cents }
                val avg = averages[id] ?: return@mapNotNull null
                if (avg >= MIN_AVERAGE_CENTS && spent * 100 >= avg * FACTOR_PERCENT && spent - avg >= MIN_EXTRA_CENTS) {
                    UnusualCategory(id, names[id] ?: "Categoria", spent, avg)
                } else null
            }
            .sortedByDescending { it.monthToDateCents - it.averageCents }
    }
}
