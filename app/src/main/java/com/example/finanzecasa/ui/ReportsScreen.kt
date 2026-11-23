package com.example.finanzecasa.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.CategorySpend
import com.example.finanzecasa.domain.CashflowPoint
import com.example.finanzecasa.domain.CashflowSeries
import com.example.finanzecasa.domain.CashflowSpan
import com.example.finanzecasa.domain.CashflowView
import com.example.finanzecasa.domain.CategoryChange
import com.example.finanzecasa.domain.IconRules
import com.example.finanzecasa.domain.ReportMath
import com.example.finanzecasa.domain.ReportMode
import com.example.finanzecasa.domain.ReportPeriod
import com.example.finanzecasa.domain.toMillis
import com.example.finanzecasa.report.YearPdf
import com.example.finanzecasa.report.YearSummaryLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class ReportState(
    val period: ReportPeriod = ReportPeriod(ReportMode.MONTH, YearMonth.now()),
    val expensesCents: Long = 0,
    val previousExpensesCents: Long = 0,
    val incomeCents: Long = 0,
    val previousIncomeCents: Long = 0,
    val expenseCategories: List<CategoryChange> = emptyList(),
    val incomeCategories: List<CategorySpend> = emptyList()
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()
    private val period = MutableStateFlow(ReportPeriod(ReportMode.MONTH, YearMonth.now()))

    fun setMode(mode: ReportMode) { period.value = period.value.copy(mode = mode) }

    fun shift(delta: Int) {
        val next = period.value.shift(delta)
        if (delta < 0 || next.start <= LocalDate.now()) period.value = next
    }

    /** Estremi in millisecondi; per il periodo in corso si arriva solo fino ad ora (le uscite future non contano). */
    private fun bounds(p: ReportPeriod, now: LocalDateTime): Pair<Long, Long> {
        val from = p.start.atStartOfDay().toMillis()
        val end = p.endExclusive.atStartOfDay().toMillis()
        return from to minOf(end, now.plusMinutes(1).toMillis())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportState> = combine(period, minuteClock()) { p, now -> p to now }
        .flatMapLatest { (p, now) ->
            val (from, to) = bounds(p, now)
            // Periodo in corso: si confrontano gli stessi giorni del periodo precedente (non l'intero periodo)
            val pFrom = p.previous().start.atStartOfDay().toMillis()
            val pTo = p.previousComparableEnd(now.toLocalDate()).atStartOfDay().toMillis()
            val first = combine(
                dao.observeExpenses(from, to), dao.observeExpenses(pFrom, pTo),
                dao.observeIncome(from, to), dao.observeIncome(pFrom, pTo)
            ) { e, pe, i, pi -> listOf(e, pe, i, pi) }
            combine(
                first,
                dao.observeCategorySpend(from, to), dao.observeCategorySpend(pFrom, pTo),
                dao.observeCategoryIncome(from, to)
            ) { totals, cur, prev, inc ->
                ReportState(
                    period = p,
                    expensesCents = totals[0], previousExpensesCents = totals[1],
                    incomeCents = totals[2], previousIncomeCents = totals[3],
                    expenseCategories = ReportMath.categoryChanges(cur, prev),
                    incomeCategories = inc
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    // --- Grafico entrate/uscite (v1.5): finestra scelta dall'utente e serie mensile
    private val chartViewFlow = MutableStateFlow(CashflowView.initial(YearMonth.now()))
    val chartView: StateFlow<CashflowView> = chartViewFlow

    fun setChartSpan(span: CashflowSpan) { chartViewFlow.value = chartViewFlow.value.withSpan(span, YearMonth.now()) }
    fun shiftChart(delta: Int) { chartViewFlow.value = chartViewFlow.value.shift(delta, YearMonth.now()) }

    /** Entrate e uscite per mese della finestra; come nel resto dei report si arriva solo fino ad ora. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cashflow: StateFlow<List<CashflowPoint>> = combine(chartViewFlow, minuteClock()) { v, now -> v to now }
        .flatMapLatest { (v, now) ->
            val months = v.months
            val current = YearMonth.from(now)
            val from = months.first().atDay(1).atStartOfDay().toMillis()
            val end = months.last().plusMonths(1).atDay(1).atStartOfDay().toMillis()
            val to = minOf(end, now.plusMinutes(1).toMillis())
            if (to <= from) flowOf(CashflowSeries.build(months, emptyMap(), emptyMap(), current))
            else combine(dao.observeIncomeByMonth(from, to), dao.observeExpenseByMonth(from, to)) { inc, exp ->
                CashflowSeries.build(
                    months,
                    inc.associate { it.month to it.totalCents },
                    exp.associate { it.month to it.totalCents },
                    current
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Riepilogo dell'anno in PDF (v1.6)
    private val pdfMessageFlow = MutableStateFlow<String?>(null)
    val pdfMessage: StateFlow<String?> = pdfMessageFlow
    fun clearPdfMessage() { pdfMessageFlow.value = null }

    fun exportYearPdf(year: Int, uri: Uri) {
        viewModelScope.launch {
            pdfMessageFlow.value = withContext(Dispatchers.IO) {
                try {
                    val data = YearSummaryLoader.load(dao, year, LocalDateTime.now())
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                        YearPdf.write(data, out, LocalDate.now())
                    }
                    "Riepilogo $year salvato in PDF."
                } catch (e: Exception) {
                    "Errore nella creazione del PDF: ${e.message}"
                }
            }
        }
    }

    /** Dettaglio per sotto-categoria di una categoria principale, nel periodo indicato. */
    fun subcategories(parentId: Long, income: Boolean, fromMillis: Long, toMillis: Long): Flow<List<CategorySpend>> =
        dao.observeSubcategoryTotals(parentId, if (income) 1 else 0, fromMillis, toMillis)
}

private val monthLabel = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)

private fun periodLabel(p: ReportPeriod): String =
    if (p.mode == ReportMode.MONTH) p.anchor.format(monthLabel).replaceFirstChar { it.uppercase() }
    else p.anchor.year.toString()

private fun previousLabel(p: ReportPeriod): String =
    if (p.mode == ReportMode.MONTH) p.previous().anchor.format(DateTimeFormatter.ofPattern("LLLL", Locale.ITALIAN))
    else p.previous().anchor.year.toString()

/** Testo "+12% rispetto a ottobre" (o "—" se il periodo precedente era a zero). */
private fun changeText(current: Long, previous: Long, label: String): String {
    val pct = ReportMath.percentChange(current, previous) ?: return "Nessun dato per $label"
    val sign = if (pct > 0) "+" else ""
    return "$sign$pct% rispetto a $label"
}

@Composable
fun ReportsScreen(onBack: () -> Unit, vm: ReportsViewModel = viewModel()) {
    val st by vm.state.collectAsState()
    val chartView by vm.chartView.collectAsState()
    val cashflow by vm.cashflow.collectAsState()
    val pdfMessage by vm.pdfMessage.collectAsState()
    val context = LocalContext.current
    var pdfYear by remember { mutableStateOf(LocalDate.now().year) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportYearPdf(pdfYear, uri)
    }
    LaunchedEffect(pdfMessage) {
        pdfMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.clearPdfMessage() }
    }
    val today = LocalDate.now()
    var drill by remember { mutableStateOf<Triple<Long, String, Boolean>?>(null) } // id, nome, entrata?
    val p = st.period

    ScreenScaffold("Report", onBack = onBack) { inner ->
        FormColumn(inner) {
            SegmentedChoice(
                options = listOf(ReportMode.MONTH to "Mese", ReportMode.YEAR to "Anno"),
                selected = p.mode, onSelect = { vm.setMode(it) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Periodo precedente") }
                Text(
                    periodLabel(p), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
                IconButton(onClick = { vm.shift(1) }, enabled = p.shift(1).start <= today) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Periodo successivo")
                }
            }

            // Riepilogo e confronto con il periodo precedente
            val prevLabel = previousLabel(p)
            val saved = st.incomeCents - st.expensesCents
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Entrate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(st.incomeCents), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.income)
                    }
                    Column {
                        Text("Spese", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(st.expensesCents), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Column {
                        Text("Risparmio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatMoney(saved), style = MaterialTheme.typography.titleMedium,
                            color = if (saved < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Spese: " + changeText(st.expensesCents, st.previousExpensesCents, prevLabel),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Entrate: " + changeText(st.incomeCents, st.previousIncomeCents, prevLabel),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (p.isCurrent(today)) {
                    Text(
                        "Il periodo è ancora in corso: il confronto è con lo stesso periodo di $prevLabel " +
                            "(dal ${formatDayMonth(p.previous().start)} al ${formatDayMonth(p.previousComparableEnd(today).minusDays(1))}).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Andamento di entrate e uscite nel tempo (linee o barre; 3/6/12 mesi o anno, anche passato)
            CashflowChartCard(
                view = chartView,
                points = if (cashflow.isEmpty()) CashflowSeries.build(chartView.months, emptyMap(), emptyMap(), YearMonth.now()) else cashflow,
                canGoForward = chartView.canGoForward(YearMonth.now()),
                onSpanChange = { vm.setChartSpan(it) },
                onShift = { vm.shiftChart(it) }
            )

            // Categorie in crescita
            val growing = ReportMath.fastestGrowing(st.expenseCategories)
            if (growing.isNotEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Spese in crescita", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Le categorie che hanno speso di più rispetto a $prevLabel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    growing.forEach { c ->
                        val pct = ReportMath.percentChange(c.currentCents, c.previousCents)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "+" + formatMoney(c.deltaCents) + (pct?.let { " (+$it%)" } ?: " (nuova)"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Spese per categoria con variazione
            AppCard(Modifier.fillMaxWidth()) {
                Text("Spese per categoria", style = MaterialTheme.typography.titleMedium)
                val rows = st.expenseCategories.filter { it.currentCents > 0 }
                if (rows.isEmpty()) {
                    Text("Nessuna spesa nel periodo.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                rows.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val id = c.categoryId
                    CategoryReportRow(
                        name = c.name, icon = c.icon, cents = c.currentCents,
                        sharePercent = ReportMath.share(c.currentCents, st.expensesCents),
                        deltaCents = c.deltaCents, deltaIsBad = true,
                        onClick = if (id != null) ({ drill = Triple(id, c.name, false) }) else null
                    )
                }
            }

            // Entrate per categoria
            AppCard(Modifier.fillMaxWidth()) {
                Text("Entrate per categoria", style = MaterialTheme.typography.titleMedium)
                if (st.incomeCategories.isEmpty()) {
                    Text("Nessuna entrata nel periodo.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                st.incomeCategories.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val id = c.categoryId
                    CategoryReportRow(
                        name = c.name, icon = c.icon, cents = c.totalCents,
                        sharePercent = ReportMath.share(c.totalCents, st.incomeCents),
                        deltaCents = null, deltaIsBad = false,
                        onClick = if (id != null) ({ drill = Triple(id, c.name, true) }) else null
                    )
                }
            }

            // Riepilogo dell'anno da salvare o condividere come PDF
            AppCard(Modifier.fillMaxWidth()) {
                val year = p.anchor.year
                Text("Riepilogo del $year in PDF", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Un documento di poche pagine con totali, confronto con l'anno prima, grafico e dettaglio dei mesi, " +
                        "spese ed entrate per categoria. Lo scegli dove salvarlo e poi lo puoi inviare a chi vuoi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                Button(
                    onClick = { pdfYear = year; pdfPicker.launch("Riepilogo-$year.pdf") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Crea il PDF del $year") }
            }
        }
    }

    drill?.let { (id, name, income) ->
        val from = p.start.atStartOfDay().toMillis()
        val to = minOf(p.endExclusive.atStartOfDay().toMillis(), System.currentTimeMillis() + 60_000)
        SubcategoryDialog(id, name, income, from, to, onDismiss = { drill = null }, vm = vm)
    }
}

@Composable
private fun CategoryReportRow(
    name: String, icon: String?, cents: Long, sharePercent: Int,
    deltaCents: Long?, deltaIsBad: Boolean, onClick: (() -> Unit)?
) {
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        rowModifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(IconRules.resolve(icon, name, null, null), size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (deltaCents != null && deltaCents != 0L) {
                val up = deltaCents > 0
                val color: Color = when {
                    up && deltaIsBad -> MaterialTheme.colorScheme.error
                    !up && deltaIsBad -> MaterialTheme.colorScheme.income
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    (if (up) "+" else "−") + formatMoney(abs(deltaCents)) + " sul periodo precedente",
                    style = MaterialTheme.typography.bodySmall, color = color
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(cents), style = MaterialTheme.typography.bodyLarge)
            Text("$sharePercent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Dettaglio per sotto-categoria di una categoria principale. */
@Composable
fun SubcategoryDialog(
    parentId: Long, parentName: String, income: Boolean, fromMillis: Long, toMillis: Long,
    onDismiss: () -> Unit, vm: ReportsViewModel = viewModel()
) {
    val rows by remember(parentId, income, fromMillis, toMillis) {
        vm.subcategories(parentId, income, fromMillis, toMillis)
    }.collectAsState(initial = emptyList())
    val total = rows.sumOf { it.totalCents }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(parentName) },
        text = {
            Column {
                if (rows.isEmpty()) Text("Nessun dettaglio.")
                rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatMoney(r.totalCents))
                        Text(
                            "${ReportMath.share(r.totalCents, total)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp), textAlign = TextAlign.End
                        )
                    }
                }
                if (rows.size > 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Totale", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(formatMoney(total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}
