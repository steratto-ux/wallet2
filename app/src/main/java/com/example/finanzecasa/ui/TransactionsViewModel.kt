package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
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

private data class SearchFilter(val accountId: Long?, val text: String, val from: LocalDate?, val to: LocalDate?)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()

    val accountFilter = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")
    /** Intervallo di date della ricerca (estremi inclusi); null = senza limite. */
    val fromDate = MutableStateFlow<LocalDate?>(null)
    val toDate = MutableStateFlow<LocalDate?>(null)

    val accounts: StateFlow<List<AccountEntity>> =
        dao.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TransactionRow>> =
        combine(accountFilter, query, fromDate, toDate) { account, text, from, to ->
            SearchFilter(account, text.trim(), from, to)
        }.flatMapLatest { f ->
            dao.observeAll(
                f.accountId, f.text,
                f.from?.atStartOfDay()?.toMillis(),
                f.to?.plusDays(1)?.atStartOfDay()?.toMillis(),   // il giorno finale è compreso
                500
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
