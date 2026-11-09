package com.example.finanzecasa.domain

import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.FinanceDao
import com.example.finanzecasa.data.SpendRow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Collega i dati del database alla regola 50/30/20 (usato dalle analisi e dal widget). */
object RuleLoader {
    fun toItems(rows: List<SpendRow>): List<SpendItem> {
        val zone = ZoneId.systemDefault()
        return rows.map {
            SpendItem(
                it.title, it.categoryId, it.categoryName, it.cents,
                Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate(), it.subCategoryId
            )
        }
    }

    /** Necessità o desiderio per ogni spesa, secondo le scelte fatte sulle categorie e sulle sotto-categorie. */
    fun bucketOf(categories: List<CategoryEntity>): (SpendItem) -> Bucket {
        val byId = categories.associateBy { it.id }
        return { item ->
            val parent = item.categoryId?.let { byId[it] }
            if (parent == null) {
                BucketRules.auto(item.categoryName)
            } else {
                val sub = item.subCategoryId?.let { byId[it] }
                BucketRules.resolve(sub?.budgetBucket, sub?.name, parent.budgetBucket, parent.name)
            }
        }
    }

    /** Il mese [month] letto con la regola; per il mese in corso si arriva solo fino ad ora. */
    suspend fun month(dao: FinanceDao, month: YearMonth, now: LocalDateTime): RuleMonth {
        val from = month.atDay(1).atStartOfDay().toMillis()
        val to = minOf(month.plusMonths(1).atDay(1).atStartOfDay().toMillis(), now.plusMinutes(1).toMillis())
        val items = toItems(dao.observeSpendRows(from, to).first())
        val income = dao.observeIncomeByMonth(from, to).first().sumOf { it.totalCents }
        val savings = dao.observeSavingsByMonth(from, to).first().sumOf { it.totalCents }
        val categories = dao.observeCategories().first()
        return Rule503020.compute(items, bucketOf(categories), income, savings)
    }
}
