package com.example.finanzecasa.ui

import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.FinanceDao
import com.example.finanzecasa.domain.IconRules
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime

/** Un budget con quanto è stato speso nel mese in corso, pronto da mostrare. */
data class BudgetCardData(
    val id: Long,
    val name: String,
    val amountCents: Long,
    val spentCents: Long,
    val iconKey: String,
    val categoryCount: Int
)

/** Budget del mese di [now] con la spesa fino a questo minuto (le uscite future non contano). */
fun budgetCards(dao: FinanceDao, now: LocalDateTime): Flow<List<BudgetCardData>> {
    val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay().toMillis()
    val upTo = now.plusMinutes(1).toMillis()
    return combine(
        dao.observeBudgets(),
        dao.observeBudgetLinks(),
        dao.observeCategories(),
        dao.observeBudgetSpending(monthStart, upTo)
    ) { budgets, links, categories, spending ->
        val byId = categories.associateBy { it.id }
        val spentBy = spending.associate { it.budgetId to it.spentCents }
        budgets.map { b ->
            val ids = links.filter { it.budgetId == b.id }.map { it.categoryId }
            BudgetCardData(
                id = b.id,
                name = b.name,
                amountCents = b.amountCents,
                spentCents = spentBy[b.id] ?: 0L,
                iconKey = budgetIcon(b.name, ids.mapNotNull { byId[it] }, byId),
                categoryCount = ids.size
            )
        }
    }
}

/** Icona del budget: quella della prima categoria collegata, altrimenti dedotta dal nome del budget. */
private fun budgetIcon(name: String, linked: List<CategoryEntity>, byId: Map<Long, CategoryEntity>): String {
    IconRules.guess(name)?.let { return it }
    val first = linked.minByOrNull { it.name.lowercase() } ?: return IconRules.DEFAULT
    return resolveCategoryIcon(first, byId)
}
