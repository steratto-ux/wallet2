package com.example.finanzecasa.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.domain.CalendarMath
import com.example.finanzecasa.domain.CalendarScheduled
import com.example.finanzecasa.domain.CalendarTx
import com.example.finanzecasa.domain.DayTotals
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Mese e giorno scelti restano nel ViewModel: tornando dalla modifica di una transazione si ritrova la stessa vista. */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()

    private val monthState = MutableStateFlow(YearMonth.now())
    private val selectedState = MutableStateFlow(LocalDate.now())
    val month: StateFlow<YearMonth> = monthState.asStateFlow()
    val selected: StateFlow<LocalDate> = selectedState.asStateFlow()

    fun setMonth(m: YearMonth) {
        if (m == monthState.value) return
        monthState.value = m
        // Cambiando mese il giorno scelto passa a oggi (mese in corso) o al primo del mese
        selectedState.value = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }

    fun select(d: LocalDate) { selectedState.value = d }

    /** Riparte dal mese e dal giorno di oggi: si usa quando il calendario si apre dalla schermata principale. */
    fun reset() {
        monthState.value = YearMonth.now()
        selectedState.value = LocalDate.now()
    }

    /** Transazioni del mese mostrato e dei due adiacenti, così lo scorrimento non mostra pagine vuote. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TransactionRow>> = monthState.flatMapLatest { m ->
        dao.observeInRange(
            m.minusMonths(1).atDay(1).atStartOfDay().toMillis(),
            m.plusMonths(2).atDay(1).atStartOfDay().toMillis()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Scadenze previste (abbonamenti, rate, ricorrenti) da oggi alla fine del mese successivo a quello mostrato. */
    val scheduled: StateFlow<List<CalendarScheduled>> = combine(
        monthState, dao.observeSubscriptions(), dao.observeLoans(), dao.observeRecurring()
    ) { m, subs, loans, rec ->
        CalendarMath.scheduled(subs, loans, rec, LocalDate.now(), m.plusMonths(1).atEndOfMonth())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private const val BACK_MONTHS = 240
private const val FORWARD_MONTHS = 36
private const val CELL_HEIGHT_DP = 60

private val dayHeadingFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val monthNameFmt = DateTimeFormatter.ofPattern("LLLL", Locale.ITALIAN)
private val monthYearFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
private val calendarBlue = Color(0xFF4A90C2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onAddTransaction: (LocalDate) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    vm: CalendarViewModel = viewModel()
) {
    val base = remember { YearMonth.now() }
    val month by vm.month.collectAsState()
    val selected by vm.selected.collectAsState()
    val rows by vm.rows.collectAsState()
    val scheduled by vm.scheduled.collectAsState()
    val scope = rememberCoroutineScope()

    fun pageOf(m: YearMonth): Int = BACK_MONTHS + ChronoUnit.MONTHS.between(base, m).toInt()
    val pager = rememberPagerState(
        initialPage = pageOf(month).coerceIn(0, BACK_MONTHS + FORWARD_MONTHS),
        pageCount = { BACK_MONTHS + FORWARD_MONTHS + 1 }
    )
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { vm.setMonth(base.plusMonths((it - BACK_MONTHS).toLong())) }
    }

    val zone = remember { ZoneId.systemDefault() }
    val days = remember(rows) {
        val nowMillis = System.currentTimeMillis()
        CalendarMath.dayTotals(rows.map {
            CalendarTx(Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate(), it.kind, it.amountCents, it.dateMillis > nowMillis)
        })
    }
    val scheduledDays = remember(scheduled) { scheduled.map { it.date }.toSet() }
    val totals = remember(days, month) { CalendarMath.monthTotals(days, month) }
    val today = LocalDate.now()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Calendario") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Indietro") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            pager.animateScrollToPage(pageOf(YearMonth.now()))
                            vm.select(LocalDate.now())
                        }
                    }) { Text("Oggi") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            MonthTabs(
                month = month, base = base,
                onPrevious = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                onNext = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(BACK_MONTHS + FORWARD_MONTHS)) } }
            )
            MonthSummary(totals)
            WeekdayHeader()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height((CELL_HEIGHT_DP * CalendarMath.weeks(month)).dp),
                verticalAlignment = Alignment.Top
            ) { page ->
                MonthGrid(
                    month = base.plusMonths((page - BACK_MONTHS).toLong()),
                    days = days, scheduledDays = scheduledDays,
                    selected = selected, today = today,
                    onSelect = vm::select, onAdd = onAddTransaction
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
            DayPanel(
                date = selected,
                transactions = rows.filter { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() == selected }
                    .sortedByDescending { it.dateMillis },
                scheduled = scheduled.filter { it.date == selected },
                netCents = days[selected]?.netCents ?: 0L,
                onOpenTransaction = onOpenTransaction,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Mesi vicini come schede: quello centrale è il mese mostrato; toccando i lati ci si sposta. */
@Composable
private fun MonthTabs(month: YearMonth, base: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    fun label(m: YearMonth) = (if (m.year == base.year) m.format(monthNameFmt) else m.format(monthYearFmt))
        .replaceFirstChar { it.lowercase() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label(month.minusMonths(1)), modifier = Modifier.weight(1f).clickable(onClick = onPrevious).padding(vertical = 10.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
            Text(
                label(month), modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
            Text(
                label(month.plusMonths(1)), modifier = Modifier.weight(1f).clickable(onClick = onNext).padding(vertical = 10.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.outlineVariant))
            Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.onSurface)
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Uscite, entrate e saldo di tutto il mese. */
@Composable
private fun MonthSummary(t: DayTotals) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Uscite", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Text(formatMoneyShort(t.expenseCents), color = MaterialTheme.colorScheme.error, fontSize = 15.sp, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowDropUp, contentDescription = "Entrate", tint = MaterialTheme.colorScheme.income, modifier = Modifier.size(22.dp))
            Text(formatMoneyShort(t.incomeCents), color = MaterialTheme.colorScheme.income, fontSize = 15.sp, maxLines = 1, softWrap = false)
        }
        Text(
            "= " + formatMoneyShort(t.netCents), fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom").forEach {
            Text(
                it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    days: Map<LocalDate, DayTotals>,
    scheduledDays: Set<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit
) {
    val cells = remember(month) { CalendarMath.grid(month) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT_DP.dp)) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (d != null) {
                            DayCell(
                                date = d, totals = days[d], hasScheduled = d in scheduledDays,
                                isSelected = d == selected, isToday = d == today,
                                onClick = { onSelect(d) }, onAdd = { onAdd(d) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    totals: DayTotals?,
    hasScheduled: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val hasTx = totals != null && totals.count > 0
    val background = when {
        isSelected -> scheme.primary
        isToday -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val onBackground = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val net = totals?.netCents ?: 0L
    val amountColor = when {
        isSelected -> scheme.onPrimary.copy(alpha = 0.9f)
        net < 0 -> scheme.error
        net > 0 -> scheme.income
        else -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val dotRed = scheme.error
    val dotGreen = scheme.income
    val dotGrey = scheme.onSurfaceVariant
    val dots = buildList {
        if (totals != null && totals.expenseCents > 0) add(dotRed)
        if (totals != null && totals.incomeCents > 0) add(dotGreen)
        if (totals != null && totals.transfers > 0) add(dotGrey)
        if ((totals != null && totals.hasFuture) || hasScheduled) add(calendarBlue)
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
        Column(
            Modifier.fillMaxSize().padding(top = 6.dp)
                .clip(shape)
                .background(background, shape)
                .then(if (hasTx && !isSelected && !isToday) Modifier.border(1.dp, scheme.outlineVariant, shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                date.dayOfMonth.toString(), color = onBackground, fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
            Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                dots.forEach { c ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (isSelected) scheme.onPrimary else c))
                }
            }
            Text(
                compactMoney(net), color = amountColor, fontSize = 10.sp,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center
            )
        }
        if (isSelected) {
            // Il + sul giorno selezionato: aggiunge una transazione con quella data
            Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape)
                    .background(scheme.tertiary).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Aggiungi transazione", tint = scheme.onTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Importo corto per le celle: centesimi sotto i 100 €, euro interi fino a 10.000 €, poi in migliaia. */
internal fun compactMoney(cents: Long): String {
    val a = abs(cents)
    return when {
        a < 10_000 -> formatMoneyShort(a)
        a < 1_000_000 -> formatMoneyShort((a + 50) / 100 * 100)
        else -> "${a / 100_000}k €"
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    transactions: List<TransactionRow>,
    scheduled: List<CalendarScheduled>,
    netCents: Long,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.format(dayHeadingFmt), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (transactions.isNotEmpty()) {
                    Text(
                        formatMoney(netCents), style = MaterialTheme.typography.titleMedium,
                        color = when {
                            netCents < 0 -> MaterialTheme.colorScheme.error
                            netCents > 0 -> MaterialTheme.colorScheme.income
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        if (transactions.isEmpty() && scheduled.isEmpty()) {
            item {
                Text(
                    "Nessuna transazione in questo giorno. Tocca il + sul giorno per aggiungerne una.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        items(transactions, key = { it.id }) { t ->
            TransactionItem(t) { onOpenTransaction(t.id) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (scheduled.isNotEmpty()) {
            item {
                Text(
                    "Previste", style = MaterialTheme.typography.titleSmall,
                    color = calendarBlue, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(scheduled, key = { "${it.label}|${it.title}|${it.date.toEpochDay()}" }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.label + " · programmata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (s.cents > 0) "+" else "") + formatMoney(s.cents),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        color = if (s.cents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.income
                    )
                }
            }
        }
    }
}
