package com.example.finanzecasa.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.domain.YearProjection
import com.example.finanzecasa.domain.YearProjectionCalc
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

class YearProjectionViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()
    private val settings = AppSettings(application)

    /** Previsione di fine anno; null finché non ci sono mesi chiusi con movimenti. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val projection: StateFlow<YearProjection?> = minuteClock().flatMapLatest { now ->
        val current = YearMonth.from(now)
        // Serve da gennaio dell'anno in corso e, per le medie, i sei mesi chiusi prima di quello in corso
        val start = minOf(YearMonth.of(current.year, 1), current.minusMonths(YearProjectionCalc.BASIS_MONTHS.toLong()))
        val from = start.atDay(1).atStartOfDay().toMillis()
        val to = now.plusMinutes(1).toMillis()
        combine(
            dao.observeIncomeByMonth(from, to),
            dao.observeExpenseByMonth(from, to),
            settings.changes("savings_target") { settings.savingsTargetCents() }
        ) { income, expense, goal ->
            YearProjectionCalc.compute(
                today = now.toLocalDate(),
                income = income.associate { it.month to it.totalCents },
                expense = expense.associate { it.month to it.totalCents },
                monthlyGoalCents = goal
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun ProjectionRow(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, modifier = Modifier.weight(1f),
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
        Text(
            value, color = valueColor,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
    }
}

private fun signedMoney(cents: Long): String = (if (cents > 0) "+" else "") + formatMoney(cents)

/**
 * Dove arrivi a dicembre: entrate, spese e risparmio previsti per l'anno e, se hai un obiettivo di risparmio mensile,
 * quanto puoi spendere ogni mese per raggiungerlo.
 */
@Composable
fun YearProjectionCard(modifier: Modifier = Modifier, vm: YearProjectionViewModel = viewModel()) {
    val p by vm.projection.collectAsState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bad = MaterialTheme.colorScheme.error
    val good = MaterialTheme.colorScheme.income

    AppCard(modifier.fillMaxWidth()) {
        Text("Previsione di fine anno", style = MaterialTheme.typography.titleMedium)
        val d = p
        if (d == null) {
            Text(
                "Servono almeno un mese chiuso con entrate o spese registrate: poi qui vedrai dove arrivi a dicembre.",
                style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp)
            )
            return@AppCard
        }
        Text(
            "Stima per il ${d.year}: i mesi che mancano valgono come la media degli ultimi ${d.basisMonths} mesi chiusi. " +
                "È un'indicazione, non una promessa: spese annuali o entrate straordinarie la spostano.",
            style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )

        ProjectionRow("Entrate finora", formatMoney(d.soFarIncomeCents), good)
        ProjectionRow("Spese finora", formatMoney(d.soFarExpenseCents), bad)
        ProjectionRow("Risparmio finora", signedMoney(d.soFarSavingsCents), if (d.soFarSavingsCents < 0) bad else Color.Unspecified)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ProjectionRow("Entrate previste a dicembre", formatMoney(d.projectedIncomeCents), good)
        ProjectionRow("Spese previste a dicembre", formatMoney(d.projectedExpenseCents), bad)
        ProjectionRow(
            "Risparmio previsto nell'anno", signedMoney(d.projectedSavingsCents),
            if (d.projectedSavingsCents < 0) bad else good, bold = true
        )

        val gap = d.gapCents
        val allowed = d.allowedMonthlyExpenseCents
        if (gap == null || allowed == null) {
            Text(
                "Per sapere quanto puoi spendere ogni mese, imposta un obiettivo di risparmio mensile nel simulatore qui sotto.",
                style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 10.dp)
            )
            return@AppCard
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ProjectionRow("Obiettivo dell'anno (12 × mensile)", formatMoney(d.annualGoalCents))
        Text(
            if (gap >= 0) "Al ritmo attuale supererai l'obiettivo di ${formatMoney(gap)}."
            else "Al ritmo attuale ti mancheranno ${formatMoney(-gap)} per raggiungerlo.",
            style = MaterialTheme.typography.bodyMedium, color = if (gap >= 0) good else bad,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (allowed >= 0) {
                "Per centrarlo puoi spendere in media ${formatMoney(allowed)} al mese nei prossimi ${d.monthsLeft} " +
                    (if (d.monthsLeft == 1) "mese" else "mesi") + " (la tua media recente è ${formatMoney(d.avgExpenseCents)})."
            } else {
                "Con le spese già fatte e le entrate previste l'obiettivo di quest'anno non è più raggiungibile: " +
                    "conviene rivederlo o ridurre le spese il più possibile."
            },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)
        )
    }
}
