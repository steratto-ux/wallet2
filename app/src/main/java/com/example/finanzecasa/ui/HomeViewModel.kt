package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.CategorySpend
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.domain.BalancePoint
import com.example.finanzecasa.domain.BalanceSeries
import com.example.finanzecasa.domain.ChartRange
import com.example.finanzecasa.domain.NetWorthMath
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

data class HomeState(
    val loaded: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    val accounts: List<AccountBalance> = emptyList(),
    val netWorthCents: Long = 0,
    val loanDebtCents: Long = 0, // debiti sottratti dal patrimonio netto (0 se l'opzione è spenta)
    val monthExpensesCents: Long = 0,
    val monthIncomeCents: Long = 0,
    val yearSavedCents: Long = 0,        // trasferimenti "Risparmio" dall'inizio dell'anno in corso
    val yearProvisionedCents: Long = 0,  // trasferimenti "Accantonamento" dall'inizio dell'anno in corso
    val recent: List<TransactionRow> = emptyList(),
    val range: ChartRange = ChartRange.MONTH,
    val chart: List<BalancePoint> = emptyList(),
    val budgets: List<BudgetCardData> = emptyList(),
    val spendMonth: YearMonth = YearMonth.now(),
    val categorySpend: List<CategorySpend> = emptyList()
)

private data class HomeInputs(val now: LocalDateTime, val range: ChartRange, val spendMonth: YearMonth)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()
    private val includeLoans = AppSettings(application).includeLoansFlow()

    /** Intervallo scelto per il grafico del saldo. */
    private val range = MutableStateFlow(ChartRange.MONTH)

    /** Mese mostrato nel grafico a ciambella delle spese. */
    private val spendMonth = MutableStateFlow(YearMonth.now())

    fun selectRange(r: ChartRange) { range.value = r }

    /** Sposta il mese della ciambella (-1 = mese precedente). Non si va oltre il mese in corso. */
    fun shiftSpendMonth(delta: Int) {
        val next = spendMonth.value.plusMonths(delta.toLong())
        if (!next.isAfter(YearMonth.now())) spendMonth.value = next
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> =
        combine(minuteClock(), range, spendMonth) { now, r, m -> HomeInputs(now, r, m) }
            .flatMapLatest { (now, r, m) ->
                val today = now.toLocalDate()
                val monthStart = today.withDayOfMonth(1).atStartOfDay().toMillis()
                val upTo = now.plusMinutes(1).toMillis() // include il minuto corrente, esclude il futuro
                val chartStart = r.startDate(today)
                val chartFrom = chartStart.atStartOfDay().toMillis()

                // Ciambella: per il mese in corso si arriva fino ad ora, per i mesi passati fino alla fine del mese
                val spendFrom = m.atDay(1).atStartOfDay().toMillis()
                val spendTo = if (m == YearMonth.from(now)) upTo else m.plusMonths(1).atDay(1).atStartOfDay().toMillis()

                val core = combine(
                    dao.observeBalances(upTo),
                    dao.observeExpenses(monthStart, upTo),
                    dao.observeIncome(monthStart, upTo),
                    dao.observeRecent(upTo, 10),
                    combine(dao.observeLoans(), includeLoans) { loans, include ->
                        if (include) NetWorthMath.loanDebtCents(loans) else 0L
                    }
                ) { balances, expenses, income, recent, debt ->
                    HomeState(
                        loaded = true,
                        now = now,
                        accounts = balances,
                        netWorthCents = balances.sumOf { it.balanceCents } - debt,
                        loanDebtCents = debt,
                        monthExpensesCents = expenses,
                        monthIncomeCents = income,
                        recent = recent,
                        range = r,
                        spendMonth = m
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
                // Risparmio e accantonamento ripartono da zero ogni 1° gennaio: si contano solo i trasferimenti dell'anno
                val yearFrom = today.withDayOfYear(1).atStartOfDay().toMillis()
                val saved = combine(
                    dao.observeSavedBetween(yearFrom, upTo), dao.observeProvisionedBetween(yearFrom, upTo)
                ) { a, b -> a to b }
                combine(
                    core, chart, budgetCards(dao, now), dao.observeCategorySpend(spendFrom, spendTo), saved
                ) { c, ch, bu, sp, sv ->
                    c.copy(
                        chart = ch, budgets = bu, categorySpend = sp,
                        yearSavedCents = sv.first, yearProvisionedCents = sv.second
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
