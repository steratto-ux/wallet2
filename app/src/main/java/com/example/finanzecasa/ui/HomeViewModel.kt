package com.example.finanzecasa.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class HomeState(
    val loaded: Boolean = false,
    val accounts: List<AccountBalance> = emptyList(),
    val netWorthCents: Long = 0,
    val monthExpensesCents: Long = 0,
    val monthIncomeCents: Long = 0,
    val recent: List<TransactionRow> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp
    private val dao = app.db.dao()

    /** Emette l'istante corrente (al minuto): le transazioni future entrano nei conti quando arriva il loro momento. */
    private val clock = flow {
        while (true) { emit(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)); delay(15_000) }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = clock.flatMapLatest { now ->
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay().toMillis()
        val upTo = now.plusMinutes(1).toMillis() // include il minuto corrente, esclude il futuro
        combine(
            dao.observeBalances(upTo),
            dao.observeExpenses(monthStart, upTo),
            dao.observeIncome(monthStart, upTo),
            dao.observeRecent(upTo, 15)
        ) { balances, expenses, income, recent ->
            HomeState(
                loaded = true,
                accounts = balances,
                netWorthCents = balances.sumOf { it.balanceCents },
                monthExpensesCents = expenses,
                monthIncomeCents = income,
                recent = recent
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val importStatus = MutableStateFlow("")

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            importStatus.value = "Import in corso…"
            importStatus.value = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    app.importRepository.import(text).summary()
                }.getOrElse { "Errore: ${it.message}" }
            }
        }
    }
}
