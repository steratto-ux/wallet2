package com.example.finanzecasa.ui

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.MonthTotal
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SpendRow
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.domain.Baseline
import com.example.finanzecasa.domain.BucketRules
import com.example.finanzecasa.domain.Rule503020
import com.example.finanzecasa.domain.RuleMonth
import com.example.finanzecasa.domain.RuleStatus
import com.example.finanzecasa.domain.FixedItem
import com.example.finanzecasa.domain.FixedSpendDetector
import com.example.finanzecasa.domain.FixedVariableSplit
import com.example.finanzecasa.domain.ForecastMath
import com.example.finanzecasa.domain.ForecastResult
import com.example.finanzecasa.domain.RepeatedSpend
import com.example.finanzecasa.domain.RepeatedSpends
import com.example.finanzecasa.domain.SavingsPlanner
import com.example.finanzecasa.domain.SpendItem
import com.example.finanzecasa.domain.SpendSplit
import com.example.finanzecasa.domain.TitleKey
import com.example.finanzecasa.domain.Upcoming
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Tutti i risultati delle analisi, calcolati insieme da [AnalysisViewModel]. */
data class AnalysisData(
    val today: LocalDate,
    val forecast: ForecastResult,
    val items: List<SpendItem>,
    val fixed: List<FixedItem>,
    val fixedKeys: Set<String>,
    val baseline: Baseline,
    val repeated: List<RepeatedSpend>,
    val months: List<YearMonth>,
    val rule: Map<YearMonth, RuleMonth>
)

data class AnalysisState(
    val data: AnalysisData? = null,
    val month: YearMonth = YearMonth.now(),
    val split: FixedVariableSplit? = null,
    val ruleMonth: YearMonth = YearMonth.now()
)

private data class Config(
    val subs: List<SubscriptionEntity>,
    val loans: List<LoanEntity>,
    val recurring: List<RecurringEntity>,
    val categories: List<CategoryEntity>
)

private data class History(
    val rows: List<SpendRow>,
    val income: List<MonthTotal>,
    val futureNet: Long,
    val savings: List<MonthTotal>
)

private const val HISTORY_MONTHS = 6      // mesi di storico letti dal database
private const val BASELINE_MONTHS = 3     // mesi chiusi usati come media
private const val REPEAT_MONTHS = 3L      // finestra per le piccole spese ripetute

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()
    private val settings = AppSettings(application)

    private val month = MutableStateFlow(YearMonth.now())
    private val ruleMonth = MutableStateFlow(YearMonth.now())
    private val _target = MutableStateFlow(settings.savingsTargetCents())
    val target: StateFlow<Long> = _target

    fun setTarget(cents: Long) {
        val v = cents.coerceAtLeast(0)
        settings.setSavingsTargetCents(v)
        _target.value = v
    }

    fun selectMonth(m: YearMonth) { month.value = m }
    fun selectRuleMonth(m: YearMonth) { ruleMonth.value = m }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data: Flow<AnalysisData> = minuteClock().flatMapLatest { now ->
        val nowMs = now.plusMinutes(1).toMillis()
        val current = YearMonth.from(now)
        val from = current.minusMonths((HISTORY_MONTHS - 1).toLong()).atDay(1).atStartOfDay().toMillis()
        val monthEnd = current.plusMonths(1).atDay(1).atStartOfDay().toMillis()
        val config = combine(dao.observeSubscriptions(), dao.observeLoans(), dao.observeRecurring(), dao.observeCategories()) { s, l, r, c ->
            Config(s, l, r, c)
        }
        val history = combine(
            dao.observeSpendRows(from, nowMs),
            dao.observeIncomeByMonth(from, nowMs),
            dao.observeNetBetween(nowMs, monthEnd),
            dao.observeSavingsByMonth(from, nowMs)
        ) { rows, income, futureNet, savings -> History(rows, income, futureNet, savings) }
        combine(dao.observeBalances(nowMs), config, history) { balances, cfg, hist ->
            val subs = cfg.subs
            val loans = cfg.loans
            val recurring = cfg.recurring
            val rows = hist.rows
            val incomeRows = hist.income
            val futureNet = hist.futureNet
            val today = now.toLocalDate()
            val zone = ZoneId.systemDefault()
            val items = rows.map {
                SpendItem(it.title, it.categoryId, it.categoryName, it.cents, Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate())
            }
            val configuredKeys = (subs.map { TitleKey.of(it.name) } + loans.map { TitleKey.of(it.name) } +
                recurring.map { TitleKey.of(it.title) }).filter { it.isNotEmpty() }.toSet()
            val fixed = FixedSpendDetector.detect(items, configuredKeys)
            val fixedKeys = FixedSpendDetector.fixedKeys(fixed, configuredKeys)

            // Mesi chiusi con dati: i (fino a) 3 precedenti a quello in corso, a partire dal primo mese con movimenti
            val firstMonth = items.minOfOrNull { YearMonth.from(it.date) }
            val closed = (1..BASELINE_MONTHS).map { current.minusMonths(it.toLong()) }
                .filter { firstMonth != null && !it.isBefore(firstMonth) }
                .sorted()

            val seenThisMonth = items.filter { YearMonth.from(it.date) == current }.map { TitleKey.of(it.title) }.toSet()
            val upcoming = ForecastMath.configuredUpcoming(subs, loans, recurring, today) +
                ForecastMath.patternUpcoming(fixed, seenThisMonth, today) +
                (if (futureNet != 0L) listOf(Upcoming("Movimenti già registrati con data futura", today, futureNet)) else emptyList())
            val variableSoFar = items.filter { YearMonth.from(it.date) == current && TitleKey.of(it.title) !in fixedKeys }
                .sumOf { it.cents }
            val forecast = ForecastMath.forecast(
                currentCents = balances.sumOf { it.balanceCents },
                upcoming = upcoming,
                variableSoFarCents = variableSoFar,
                today = today,
                baselineDailyCents = ForecastMath.baselineDaily(items, closed, fixedKeys)
            )

            val incomeByMonth = incomeRows.associate { YearMonth.parse(it.month) to it.totalCents }
            val baseline = SavingsPlanner.baseline(items, incomeByMonth, closed.ifEmpty { listOf(current) }, fixedKeys)

            val windowStart = today.minusMonths(REPEAT_MONTHS)
            val recent = items.filter { !it.date.isBefore(windowStart) }
            val spanDays = recent.minOfOrNull { it.date }?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 30L
            val repeated = RepeatedSpends.find(recent, fixedKeys, spanDays / 30.4)

            val savingsByMonth = hist.savings.associate { YearMonth.parse(it.month) to it.totalCents }
            val months = (items.map { YearMonth.from(it.date) } + incomeByMonth.keys + savingsByMonth.keys + current)
                .distinct().sorted()

            // Regola 50/30/20: ogni spesa va tra le necessità o i desideri secondo la sua categoria principale
            val categoryById = cfg.categories.associateBy { it.id }
            val bucketOf = { item: SpendItem ->
                val cat = item.categoryId?.let { categoryById[it] }
                if (cat != null) BucketRules.effective(cat.budgetBucket, cat.name) else BucketRules.auto(item.categoryName)
            }
            val rule = months.associateWith { ym ->
                Rule503020.compute(
                    items.filter { YearMonth.from(it.date) == ym }, bucketOf,
                    incomeByMonth[ym] ?: 0L, savingsByMonth[ym] ?: 0L
                )
            }
            AnalysisData(today, forecast, items, fixed, fixedKeys, baseline, repeated, months, rule)
        }
    }

    val state: StateFlow<AnalysisState> = combine(data, month, ruleMonth) { d, m, rm ->
        val shown = if (m in d.months) m else d.months.last()
        AnalysisState(
            data = d, month = shown,
            split = SpendSplit.split(d.items.filter { YearMonth.from(it.date) == shown }, d.fixedKeys),
            ruleMonth = if (rm in d.months) rm else d.months.last()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisState())
}

private val monthNameFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)

private fun signed(cents: Long): String = (if (cents > 0) "+" else "") + formatMoney(cents)

@Composable
private fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
        Text(
            value, color = valueColor,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier
    )
}

@Composable
fun AnalysisScreen(onBack: () -> Unit, vm: AnalysisViewModel = viewModel()) {
    val st by vm.state.collectAsState()
    val target by vm.target.collectAsState()
    val d = st.data

    ScreenScaffold("Analisi", onBack = onBack) { inner ->
        FormColumn(inner) {
            if (d == null) {
                Text("Calcolo in corso…", style = MaterialTheme.typography.bodyMedium)
            } else {
                ForecastCard(d)
                RuleCard(d, st.ruleMonth, onSelect = vm::selectRuleMonth)
                SplitCard(d, st, onSelect = vm::selectMonth)
                SimulatorCard(d.baseline, target, onTarget = vm::setTarget)
                RepeatedCard(d.repeated)
            }
        }
    }
}

// ---------------------------------------------------------------- 1. Previsione di fine mese

@Composable
private fun ForecastCard(d: AnalysisData) {
    val f = d.forecast
    val endDate = d.today.withDayOfMonth(d.today.lengthOfMonth())
    AppCard(Modifier.fillMaxWidth()) {
        Text("Previsione di fine mese", style = MaterialTheme.typography.titleMedium)
        Text(
            formatMoney(f.projectedEndCents),
            style = MaterialTheme.typography.headlineMedium,
            color = if (f.projectedEndCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Hint("Saldo totale stimato al ${formatDayMonth(endDate)}")
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

        LabelValue("Saldo attuale (tutti i conti)", formatMoney(f.currentCents))
        if (f.upcoming.isEmpty()) {
            Hint("Nessuna scadenza prevista fino a fine mese.", Modifier.padding(vertical = 4.dp))
        } else {
            Text("Ancora in arrivo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            f.upcoming.forEach { u ->
                LabelValue(
                    "${u.label} · ${formatDayMonth(u.date)}", signed(u.cents),
                    if (u.cents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.income
                )
            }
        }
        if (f.remainingDays > 0) {
            LabelValue(
                "Spese di tutti i giorni: ${formatMoney(f.dailyVariableCents)} al giorno × ${f.remainingDays} giorni",
                signed(-f.variableProjectionCents), MaterialTheme.colorScheme.error
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        LabelValue("Saldo previsto", formatMoney(f.projectedEndCents), bold = true)
        Hint(
            "Le spese di tutti i giorni sono la media di quanto speso finora (all'inizio del mese pesa di più la media " +
                "dei mesi scorsi). Stipendi e altre entrate sono contati solo se inseriti tra i ricorrenti o con data futura.",
            Modifier.padding(top = 6.dp)
        )
    }
}

// ---------------------------------------------------------------- 2. Spese fisse e variabili

@Composable
private fun SplitCard(d: AnalysisData, st: AnalysisState, onSelect: (YearMonth) -> Unit) {
    val split = st.split
    val idx = d.months.indexOf(st.month)
    AppCard(Modifier.fillMaxWidth()) {
        Text("Spese fisse e variabili", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(d.months[idx - 1]) }, enabled = idx > 0) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mese precedente")
            }
            Text(
                st.month.format(monthNameFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            IconButton(onClick = { onSelect(d.months[idx + 1]) }, enabled = idx in 0 until d.months.size - 1) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Mese successivo")
            }
        }
        if (split == null || split.totalCents <= 0) {
            Text("Nessuna spesa in questo mese.", style = MaterialTheme.typography.bodyMedium)
            return@AppCard
        }
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            if (split.fixedCents > 0) {
                Row(Modifier.weight(split.fixedCents.toFloat()).height(14.dp).background(MaterialTheme.colorScheme.primary)) {}
            }
            if (split.variableCents > 0) {
                Row(Modifier.weight(split.variableCents.toFloat()).height(14.dp).background(MaterialTheme.colorScheme.tertiary)) {}
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Fisse · ${split.fixedPercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(formatMoney(split.fixedCents), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Variabili · ${100 - split.fixedPercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(formatMoney(split.variableCents), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (split.fixedLines.isNotEmpty()) {
            Text("Fisse", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            split.fixedLines.forEach { l ->
                LabelValue(if (l.count > 1) "${l.label} (×${l.count})" else l.label, formatMoney(l.cents))
            }
        }
        if (split.variableLines.isNotEmpty()) {
            Text("Variabili, per categoria", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            split.variableLines.forEach { l -> LabelValue(l.label, formatMoney(l.cents)) }
        }
        Hint(
            "Sono fisse abbonamenti, rate e ricorrenti, più le spese che tornano ogni mese con importo simile " +
                "(riconosciute dopo almeno 3 mesi). Le fisse sono le più difficili da ridurre; le variabili sono quelle su cui puoi intervenire.",
            Modifier.padding(top = 10.dp)
        )
    }
}

// ---------------------------------------------------------------- 3. Simulatore "e se…"

@Composable
private fun SimulatorCard(b: Baseline, target: Long, onTarget: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (target > 0) centsToInput(target) else "") }
    val shown = b.variable.take(8)
    val percents = remember(b) { mutableStateListOf<Int>().apply { repeat(shown.size) { add(0) } } }

    AppCard(Modifier.fillMaxWidth()) {
        Text("E se risparmiassi di più?", style = MaterialTheme.typography.titleMedium)
        Hint(
            "Media degli ultimi ${b.months} ${if (b.months == 1) "mese" else "mesi"} " +
                "(${if (b.months == 1) "quello in corso" else "chiusi"}).",
            Modifier.padding(bottom = 6.dp)
        )
        LabelValue("Entrate", formatMoney(b.incomeCents), MaterialTheme.colorScheme.income)
        LabelValue("Spese fisse", signed(-b.fixedCents))
        LabelValue("Spese variabili", signed(-b.variableCents))
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        LabelValue(
            "Ora risparmi al mese", formatMoney(b.savingsCents),
            if (b.savingsCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified, bold = true
        )
        if (b.incomeCents <= 0) {
            Hint("Non risultano entrate registrate nel periodo: il risparmio mostrato è solo negativo.", Modifier.padding(top = 4.dp))
        }

        // Obiettivo e tagli suggeriti
        MoneyInput("Obiettivo di risparmio al mese (€)", text) {
            text = it
            onTarget(if (it.isBlank()) 0L else (parseAmountToCents(it) ?: target))
        }
        val goal = parseAmountToCents(text) ?: 0L
        if (goal > 0) {
            val gap = goal - b.savingsCents
            if (gap <= 0) {
                Text(
                    "Con le abitudini attuali raggiungi già l'obiettivo (${formatMoney(b.savingsCents - goal)} in più al mese).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.income,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                val plan = SavingsPlanner.suggestCuts(b.variable, gap)
                Text(
                    "Per arrivarci devi recuperare ${formatMoney(gap)} al mese. Un modo possibile:",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                plan.cuts.forEach { c ->
                    LabelValue("${c.name} (−${c.percent}% su ${formatMoney(c.monthlyCents)})", "−" + formatMoney(c.cutCents))
                }
                if (plan.shortfallCents > 0) {
                    Text(
                        "Tagliando al massimo metà di ogni categoria mancano ancora ${formatMoney(plan.shortfallCents)} al mese: " +
                            "servirebbe agire anche sulle spese fisse o sulle entrate.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Prova libera con i cursori
        if (shown.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Prova tu", style = MaterialTheme.typography.titleSmall)
            Hint("Sposta i cursori per vedere quanto risparmieresti riducendo ogni categoria.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 10, 20, 30).forEach { p ->
                    TextButton(onClick = { for (i in percents.indices) percents[i] = p }) {
                        Text(if (p == 0) "Azzera" else "Tutte −$p%")
                    }
                }
            }
            shown.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatMoney(c.monthlyCents)} → ${formatMoney(c.monthlyCents - c.monthlyCents * percents[i] / 100)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = percents[i].toFloat(),
                    onValueChange = { percents[i] = (it / 5f).roundToInt() * 5 },
                    valueRange = 0f..50f, steps = 9
                )
                Text("−${percents[i]}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val newSavings = SavingsPlanner.savingsWithCuts(b, percents.toList())
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            LabelValue(
                "Risparmio al mese", formatMoney(newSavings),
                if (newSavings < 0) MaterialTheme.colorScheme.error else Color.Unspecified, bold = true
            )
            LabelValue("In più rispetto a ora", signed(newSavings - b.savingsCents), MaterialTheme.colorScheme.income)
            LabelValue("Risparmio in un anno", formatMoney(newSavings * 12), bold = true)
            if (goal > 0) {
                Hint(
                    if (newSavings >= goal) "Obiettivo di ${formatMoney(goal)} al mese raggiunto."
                    else "Mancano ${formatMoney(goal - newSavings)} al mese all'obiettivo di ${formatMoney(goal)}.",
                    Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 4. Piccole spese che si ripetono

@Composable
private fun RepeatedCard(list: List<RepeatedSpend>) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Piccole spese che si ripetono", style = MaterialTheme.typography.titleMedium)
        Hint("Spese con lo stesso titolo, di importo contenuto (fino a 30 € in media), comparse almeno 4 volte negli ultimi 3 mesi.")
        if (list.isEmpty()) {
            Text("Nessuna piccola spesa ripetuta trovata.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            return@AppCard
        }
        val shown = list.take(8)
        shown.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${r.count} volte · media ${formatMoney(r.averageCents)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("≈ ${formatMoney(r.perMonthCents)} /mese", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "≈ ${formatMoney(r.perYearCents)} /anno",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabelValue("Tutte insieme, in un anno", formatMoney(list.sumOf { it.perYearCents }), MaterialTheme.colorScheme.error, bold = true)
    }
}


// ---------------------------------------------------------------- Regola 50/30/20

private val needColor = Color(0xFF3F7BD1)
private val wantColor = Color(0xFFE0724A)
private val saveColor = Color(0xFF1E9E7C)
private val leftColor = Color(0xFF9DB0A8)

private fun pctText(p: Int?): String = if (p == null) "—" else "$p%"

@Composable
private fun RuleCard(d: AnalysisData, selected: YearMonth, onSelect: (YearMonth) -> Unit) {
    val m = d.rule[selected] ?: return
    val idx = d.months.indexOf(selected)
    val outflow = m.needCents + m.wantCents + m.savingsCents

    AppCard(Modifier.fillMaxWidth()) {
        Text("Regola 50/30/20", style = MaterialTheme.typography.titleMedium)
        Hint("50% necessità, 30% desideri, 20% risparmio: percentuali sul reddito (le entrate) del mese.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(d.months[idx - 1]) }, enabled = idx > 0) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mese precedente")
            }
            Text(
                selected.format(monthNameFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            IconButton(onClick = { onSelect(d.months[idx + 1]) }, enabled = idx in 0 until d.months.size - 1) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Mese successivo")
            }
        }

        if (m.incomeCents <= 0 && outflow <= 0) {
            Text("Nessun movimento in questo mese.", style = MaterialTheme.typography.bodyMedium)
        } else {
            // Ciambella: la torta è il reddito; se si è speso di più, è il totale di spese e risparmio
            val slices = buildList {
                if (m.needCents > 0) add(Slice("Necessità", m.needCents, needColor))
                if (m.wantCents > 0) add(Slice("Desideri", m.wantCents, wantColor))
                if (m.savingsCents > 0) add(Slice("Risparmio", m.savingsCents, saveColor))
                if (m.leftCents > 0) add(Slice("Non assegnato", m.leftCents, leftColor))
            }
            val total = slices.sumOf { it.cents }
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Donut(slices, total, Modifier.size(170.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (m.incomeCents > 0) "Reddito" else "Uscite",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(formatMoney(if (m.incomeCents > 0) m.incomeCents else outflow), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (m.incomeCents <= 0) {
                Hint("Nel mese non risultano entrate: le percentuali non si possono calcolare.", Modifier.padding(bottom = 6.dp))
            }
            if (m.leftCents < 0) {
                Text(
                    "Spese e risparmi superano le entrate di ${formatMoney(-m.leftCents)}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            val needTarget = Rule503020.target(m.incomeCents, Rule503020.NEED_PERCENT)
            val wantTarget = Rule503020.target(m.incomeCents, Rule503020.WANT_PERCENT)
            val saveTarget = Rule503020.target(m.incomeCents, Rule503020.SAVINGS_PERCENT)
            val over = MaterialTheme.colorScheme.error
            val good = MaterialTheme.colorScheme.income

            val needStatus = Rule503020.spendStatus(m, m.needCents, Rule503020.NEED_PERCENT)
            RuleRow(
                needColor, "Necessità", Rule503020.NEED_PERCENT, m.needCents, m.percentOfIncome(m.needCents),
                fraction(m.needCents, needTarget), if (needStatus == RuleStatus.OVER) over else needColor,
                when (needStatus) {
                    RuleStatus.OVER -> "Oltre di ${formatMoney(m.needCents - needTarget)}"
                    RuleStatus.OK -> "Entro l'obiettivo"
                    else -> ""
                },
                if (needStatus == RuleStatus.OVER) over else good
            )
            val wantStatus = Rule503020.spendStatus(m, m.wantCents, Rule503020.WANT_PERCENT)
            RuleRow(
                wantColor, "Desideri", Rule503020.WANT_PERCENT, m.wantCents, m.percentOfIncome(m.wantCents),
                fraction(m.wantCents, wantTarget), if (wantStatus == RuleStatus.OVER) over else wantColor,
                when (wantStatus) {
                    RuleStatus.OVER -> "Oltre di ${formatMoney(m.wantCents - wantTarget)}"
                    RuleStatus.OK -> "Entro l'obiettivo"
                    else -> ""
                },
                if (wantStatus == RuleStatus.OVER) over else good
            )
            val saveStatus = Rule503020.savingsStatus(m)
            RuleRow(
                saveColor, "Risparmio", Rule503020.SAVINGS_PERCENT, m.savingsCents, m.percentOfIncome(m.savingsCents),
                fraction(m.savingsCents, saveTarget), saveColor,
                when (saveStatus) {
                    RuleStatus.SHORT -> "Mancano ${formatMoney(saveTarget - m.savingsCents)}"
                    RuleStatus.OK -> "Obiettivo raggiunto"
                    else -> ""
                },
                if (saveStatus == RuleStatus.SHORT) MaterialTheme.colorScheme.tertiary else good
            )

            // Dettaglio: dove vanno le necessità e i desideri
            if (m.needLines.isNotEmpty()) {
                Text("Necessità", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                m.needLines.take(5).forEach { l -> LabelValue(l.label, formatMoney(l.cents)) }
            }
            if (m.wantLines.isNotEmpty()) {
                Text("Desideri", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                m.wantLines.take(5).forEach { l -> LabelValue(l.label, formatMoney(l.cents)) }
            }
        }

        // Ultimi mesi: tocca una colonna per vederla sopra
        if (d.months.size > 1) {
            Text("Ultimi mesi", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            RuleHistory(d.months, d.rule, selected, onSelect)
            Hint("Le linee tratteggiate sono i confini del 50% e dell'80%: se le colonne le rispettano sei in regola.")
        }
        Hint(
            "Le categorie sono classificate in automatico dal nome; per cambiare tocca la categoria in Categorie. " +
                "Il risparmio è la somma dei trasferimenti con la casella \"Risparmio\".",
            Modifier.padding(top = 8.dp)
        )
    }
}

private fun fraction(value: Long, target: Long): Float =
    if (target <= 0) (if (value > 0) 1f else 0f) else (value.toFloat() / target).coerceIn(0f, 1f)

@Composable
private fun RuleRow(
    color: Color, name: String, targetPercent: Int, cents: Long, percent: Int?,
    fillFraction: Float, barColor: Color, status: String, statusColor: Color
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(pctText(percent), style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(cents), style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(Modifier.fillMaxWidth(fillFraction).height(8.dp).background(barColor))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Text(
                "Obiettivo $targetPercent%", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
            )
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
    }
}

/** Una colonna per mese: necessità, desideri e risparmio impilati, con i confini del 50% e dell'80%. */
@Composable
private fun RuleHistory(
    months: List<YearMonth>, rule: Map<YearMonth, RuleMonth>,
    selected: YearMonth, onSelect: (YearMonth) -> Unit
) {
    val shown = months.takeLast(6)
    val lineColor = MaterialTheme.colorScheme.outline
    val selectedBg = MaterialTheme.colorScheme.surfaceContainer
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(110.dp).pointerInput(shown) {
                detectTapGestures { pos ->
                    val slot = size.width.toFloat() / shown.size
                    shown.getOrNull((pos.x / slot).toInt())?.let(onSelect)
                }
            }
        ) {
            val slot = size.width / shown.size
            val barW = slot * 0.5f
            val h = size.height
            shown.forEachIndexed { i, ym ->
                val left = i * slot + (slot - barW) / 2
                if (ym == selected) {
                    drawRect(selectedBg, Offset(i * slot, 0f), Size(slot, h))
                }
                val m = rule[ym] ?: return@forEachIndexed
                val total = maxOf(m.incomeCents, m.needCents + m.wantCents + m.savingsCents).toFloat()
                if (total <= 0f) return@forEachIndexed
                var bottom = h
                for ((cents, color) in listOf(m.needCents to needColor, m.wantCents to wantColor, m.savingsCents to saveColor)) {
                    val segment = h * cents / total
                    if (segment > 0f) {
                        drawRect(color, Offset(left, bottom - segment), Size(barW, segment))
                        bottom -= segment
                    }
                }
            }
            for (fractionOfHeight in listOf(0.5f, 0.8f)) {
                val y = h * (1f - fractionOfHeight)
                drawLine(
                    lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            shown.forEach { ym ->
                Text(
                    ym.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALIAN),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ym == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
