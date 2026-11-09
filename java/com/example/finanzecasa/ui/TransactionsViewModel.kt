package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Tipo di transazione per la ricerca. */
enum class TxTypeFilter(val code: Int) { ALL(0), INCOME(1), EXPENSE(2), TRANSFER(3) }

private data class SearchFilter(
    val accountId: Long?, val text: String, val from: LocalDate?, val to: LocalDate?,
    val type: TxTypeFilter, val categoryId: Long?
)

/** Una scadenza futura di un abbonamento, di una rata o di una ricorrente (solo la prossima, non le successive). */
data class ScheduledRow(
    val key: String,
    val title: String,
    val cents: Long,                 // con segno
    val date: LocalDate,
    val accountId: Long,
    val accountName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val parentCategoryName: String?,
    val parentCategoryIcon: String?,
    val kindLabel: String,           // Abbonamento, Rata, Ricorrente
    val formRoute: String,
    val sourceId: Long
)

private data class ScheduleSources(
    val subs: List<SubscriptionEntity>, val loans: List<LoanEntity>, val recurring: List<RecurringEntity>,
    val accounts: List<AccountEntity>, val categories: List<CategoryEntity>
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()

    val accountFilter = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")
    /** Intervallo di date della ricerca (estremi inclusi); null = senza limite. */
    val fromDate = MutableStateFlow<LocalDate?>(null)
    val toDate = MutableStateFlow<LocalDate?>(null)
    val typeFilter = MutableStateFlow(TxTypeFilter.ALL)
    /** Categoria (principale o sotto-categoria) da cercare; una principale comprende le sue sotto-categorie. */
    val categoryFilter = MutableStateFlow<Long?>(null)
    val showScheduled = MutableStateFlow(true)

    val accounts: StateFlow<List<AccountEntity>> =
        dao.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = combine(accountFilter, query, fromDate, toDate, typeFilter) { account, text, from, to, type ->
        SearchFilter(account, text.trim(), from, to, type, null)
    }.combine(categoryFilter) { f, cat -> f.copy(categoryId = cat) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TransactionRow>> = filter.flatMapLatest { f ->
        dao.observeAll(
            f.accountId, f.text,
            f.from?.atStartOfDay()?.toMillis(),
            f.to?.plusDays(1)?.atStartOfDay()?.toMillis(),   // il giorno finale è compreso
            f.type.code, f.categoryId,
            500
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Totale dell'elenco visualizzato (con gli stessi filtri). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val total: StateFlow<Long> = filter.flatMapLatest { f ->
        dao.observeTotal(
            f.accountId, f.text,
            f.from?.atStartOfDay()?.toMillis(),
            f.to?.plusDays(1)?.atStartOfDay()?.toMillis(),
            f.type.code, f.categoryId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val sources = combine(
        dao.observeSubscriptions(), dao.observeLoans(), dao.observeRecurring(), dao.observeAccounts(), dao.observeCategories()
    ) { s, l, r, a, c -> ScheduleSources(s, l, r, a, c) }

    /** Prossime scadenze programmate che rispettano i filtri della ricerca (una sola per ogni ricorrente, abbonamento o prestito). */
    val scheduled: StateFlow<List<ScheduledRow>> = combine(filter, showScheduled, sources) { f, show, src ->
        if (show) scheduledRows(f, src) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun scheduledRows(f: SearchFilter, src: ScheduleSources): List<ScheduledRow> {
        if (f.type == TxTypeFilter.TRANSFER) return emptyList()
        val accountNames = src.accounts.associate { it.id to it.name }
        val categoryById = src.categories.associateBy { it.id }

        fun make(
            kind: String, route: String, id: Long, title: String, cents: Long,
            nextDueEpochDay: Long, accountId: Long, categoryId: Long?
        ): ScheduledRow {
            val cat = categoryId?.let { categoryById[it] }
            val parent = cat?.parentId?.let { categoryById[it] }
            return ScheduledRow(
                key = "$route|$id", title = title, cents = cents, date = LocalDate.ofEpochDay(nextDueEpochDay),
                accountId = accountId, accountName = accountNames[accountId] ?: "?",
                categoryId = categoryId, categoryName = cat?.name, categoryIcon = cat?.icon,
                parentCategoryName = parent?.name, parentCategoryIcon = parent?.icon,
                kindLabel = kind, formRoute = route, sourceId = id
            )
        }

        val all = src.subs.filter { it.active }.map {
            make("Abbonamento", "subscriptionForm", it.id, it.name, -it.amountCents, it.nextDueEpochDay, it.accountId, it.categoryId)
        } + src.loans.filter { it.remainingInstallments > 0 }.map {
            make("Rata", "loanForm", it.id, it.name, -it.installmentCents, it.nextDueEpochDay, it.accountId, it.categoryId)
        } + src.recurring.filter { it.active }.map {
            make("Ricorrente", "recurringForm", it.id, it.title, it.amountCents, it.nextDueEpochDay, it.accountId, it.categoryId)
        }

        return all.filter { r ->
            val typeOk = when (f.type) {
                TxTypeFilter.ALL -> true
                TxTypeFilter.INCOME -> r.cents > 0
                TxTypeFilter.EXPENSE -> r.cents < 0
                TxTypeFilter.TRANSFER -> false
            }
            val accountOk = f.accountId == null || r.accountId == f.accountId
            val categoryOk = f.categoryId == null || r.categoryId == f.categoryId ||
                (r.categoryId != null && categoryById[r.categoryId]?.parentId == f.categoryId)
            val textOk = f.text.isEmpty() || listOfNotNull(r.title, r.categoryName, r.parentCategoryName, r.kindLabel)
                .any { it.contains(f.text, ignoreCase = true) }
            val dateOk = (f.from == null || !r.date.isBefore(f.from)) && (f.to == null || !r.date.isAfter(f.to))
            typeOk && accountOk && categoryOk && textOk && dateOk
        }.sortedBy { it.date }
    }
}
