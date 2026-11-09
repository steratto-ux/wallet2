package com.example.finanzecasa.domain

import com.example.finanzecasa.data.CategorySpend
import kotlin.math.roundToInt

/** Una categoria confrontata tra il periodo attuale e quello precedente. */
data class CategoryChange(
    val name: String,
    val categoryId: Long?,
    val icon: String?,
    val currentCents: Long,
    val previousCents: Long
) {
    val deltaCents: Long get() = currentCents - previousCents
}

object ReportMath {
    /** Variazione percentuale; null se il periodo precedente era a zero (non confrontabile). */
    fun percentChange(current: Long, previous: Long): Int? =
        if (previous == 0L) null else ((current - previous) * 100.0 / previous).roundToInt()

    private fun key(c: CategorySpend) = c.categoryId?.toString() ?: "nome:${c.name}"

    /** Unisce le categorie dei due periodi (una categoria può esistere in uno solo). */
    fun categoryChanges(current: List<CategorySpend>, previous: List<CategorySpend>): List<CategoryChange> {
        val cur = current.associateBy { key(it) }
        val prev = previous.associateBy { key(it) }
        return (cur.keys + prev.keys).map { k ->
            val c = cur[k]
            val p = prev[k]
            val ref = c ?: p!!
            CategoryChange(ref.name, ref.categoryId, ref.icon, c?.totalCents ?: 0L, p?.totalCents ?: 0L)
        }.sortedByDescending { it.currentCents }
    }

    /** Le categorie di spesa cresciute di più (in euro) rispetto al periodo precedente. */
    fun fastestGrowing(changes: List<CategoryChange>, limit: Int = 3): List<CategoryChange> =
        changes.filter { it.deltaCents > 0 }.sortedByDescending { it.deltaCents }.take(limit)

    /** Quota percentuale di [part] su [total]. */
    fun share(part: Long, total: Long): Int = if (total <= 0) 0 else (part * 100.0 / total).roundToInt()
}
