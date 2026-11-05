package com.example.finanzecasa.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.domain.BalancePoint
import com.example.finanzecasa.domain.BalanceSeries
import com.example.finanzecasa.domain.ChartRange
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

data class HomeState(
    val loaded: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    val accounts: List<AccountBalance> = emptyList(),
    val netWorthCents: Long = 0,
    val monthExpensesCents: Long = 0,
    val monthIncomeCents: Long = 0,
    val recent: List<TransactionRow> = emptyList(),
    val range: ChartRange = ChartRange.MONTH,
    val chart: List<BalancePoint> = emptyList(),
    val budgets: List<BudgetCardData> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanzeApp
    private val dao = app.db.dao()

    /** Intervallo scelto per il grafico del saldo. */
    private val range = MutableStateFlow(ChartRange.MONTH)

    fun selectRange(r: ChartRange) { range.value = r }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(minuteClock(), range) { now, r -> now to r }
        .flatMapLatest { (now, r) ->
            val today = now.toLocalDate()
            val monthStart = today.withDayOfMonth(1).atStartOfDay().toMillis()
            val upTo = now.plusMinutes(1).toMillis() // include il minuto corrente, esclude il futuro
            val chartStart = r.startDate(today)
            val chartFrom = chartStart.atStartOfDay().toMillis()

            val core = combine(
                dao.observeBalances(upTo),
                dao.observeExpenses(monthStart, upTo),
                dao.observeIncome(monthStart, upTo),
                dao.observeRecent(upTo, 15)
            ) { balances, expenses, income, recent ->
                HomeState(
                    loaded = true,
                    now = now,
                    accounts = balances,
                    netWorthCents = balances.sumOf { it.balanceCents },
                    monthExpensesCents = expenses,
                    monthIncomeCents = income,
                    recent = recent,
                    range = r
                )
            }
            // Saldo totale all'inizio dell'intervallo + movimenti giorno per giorno
            val chart = combine(
                dao.observeInitialTotal(),
                dao.observeSumBefore(chartFrom),
                dao.observeTxPoints(chartFrom, upTo)
            ) { initial, before, points ->
                val zone = ZoneId.systemDefault()
                BalanceSeries.build(
                    startCents = initial + before,
                    txs = points.map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() to it.amountCents },
                    from = chartStart,
                    to = today
                )
            }
            combine(core, chart, budgetCards(dao, now)) { c, ch, bu -> c.copy(chart = ch, budgets = bu) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

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
