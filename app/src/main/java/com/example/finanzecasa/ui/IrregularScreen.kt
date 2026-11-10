package com.example.finanzecasa.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.IrregularExpenseEntity
import com.example.finanzecasa.data.ProvisionUseEntity
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.domain.IrregularInput
import com.example.finanzecasa.domain.IrregularPlan
import com.example.finanzecasa.domain.insertTransferPair
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Spese irregolari (assicurazione, bollo, vacanza...): previsione, frazionamento mensile e uso dell'accantonamento. */
class IrregularViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp
    private val dao = app.db.dao()

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val items: StateFlow<List<IrregularExpenseEntity>> = dao.observeIrregular().asState(emptyList())

    /** Accantonamento disponibile: trasferimenti "Accantonamento" meno quanto già usato. */
    val pool: StateFlow<Long> =
        combine(dao.observeProvisionedTotal(), dao.observeProvisionUsedTotal()) { provisioned, used ->
            maxOf(0L, provisioned - used)
        }.asState(0L)

    val accounts: StateFlow<List<AccountEntity>> = dao.observeAccountsOrdered().asState(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = dao.observeCategories().asState(emptyList())

    suspend fun byId(id: Long) = dao.irregularById(id)

    suspend fun save(e: IrregularExpenseEntity) {
        if (e.id == 0L) dao.insertIrregular(e) else dao.updateIrregular(e)
    }

    suspend fun delete(id: Long) = dao.deleteIrregular(id)

    suspend fun createCategory(name: String, parentId: Long?, icon: String?): Long =
        dao.categoryByName(name, parentId)?.id
            ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId, icon = icon))

    /** Account che ha ricevuto più accantonamenti (di solito quello dove si tengono i soldi messi da parte). */
    suspend fun defaultProvisionAccount(): Long? = dao.mainProvisionAccountId()

    /**
     * Registra il pagamento: una spesa sull'account scelto per l'importo intero e, se si usa l'accantonamento,
     * la registrazione dell'uso. Se il denaro accantonato sta su un altro account, la parte usata viene spostata
     * da lì all'account di pagamento con un trasferimento, così i saldi restano giusti.
     * Restituisce un messaggio d'errore oppure null.
     */
    suspend fun pay(
        id: Long, costCents: Long, payAccountId: Long, date: LocalDate, useCents: Long, sourceAccountId: Long?
    ): String? = app.db.withTransaction {
        val item = dao.irregularById(id) ?: return@withTransaction "Spesa non trovata."
        val available = maxOf(0L, dao.provisionedTotalOnce() - dao.provisionUsedTotalOnce())
        val use = minOf(IrregularPlan.usable(available, costCents), maxOf(0L, useCents))
        val millis = date.atStartOfDay().toMillis()
        dao.insertTransaction(
            TransactionEntity(
                accountId = payAccountId, categoryId = item.categoryId, title = item.name,
                amountCents = -costCents, dateMillis = millis
            )
        )
        if (use > 0) {
            dao.insertProvisionUse(ProvisionUseEntity(irregularId = id, amountCents = use, dateEpochDay = date.toEpochDay()))
            if (sourceAccountId != null && sourceAccountId != payAccountId) {
                insertTransferPair(
                    dao, sourceAccountId, payAccountId, use, "Uso accantonamento: ${item.name}", millis,
                    isSavings = false, isProvision = false
                )
            }
        }
        if (item.repeatYearly) {
            var next = LocalDate.ofEpochDay(item.dueEpochDay).plusYears(1)
            while (!next.isAfter(date)) next = next.plusYears(1)
            dao.updateIrregular(item.copy(dueEpochDay = next.toEpochDay()))
        } else {
            dao.updateIrregular(item.copy(done = true))
        }
        null
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun IrregularScreen(onBack: () -> Unit, onOpenForm: (Long?) -> Unit, vm: IrregularViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    val pool by vm.pool.collectAsState()
    val allAccounts by vm.accounts.collectAsState()
    val now by minuteClock().collectAsState(initial = LocalDateTime.now())
    val today = now.toLocalDate()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val summary = remember(items, pool, today) {
        IrregularPlan.plan(
            items.map { IrregularInput(it.id, it.name, it.estimatedCents, LocalDate.ofEpochDay(it.dueEpochDay)) },
            pool, today
        )
    }
    val rowById = summary.rows.associateBy { it.id }
    var paying by remember { mutableStateOf<IrregularExpenseEntity?>(null) }
    var defaultSource by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(paying) { if (paying != null) defaultSource = vm.defaultProvisionAccount() }

    ScreenScaffold(
        title = "Spese irregolari", onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(null) }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Spese che non arrivano ogni mese (assicurazione, bollo, vacanze, regali…). Inserisci quanto pensi di spendere " +
                        "e la data: l'app calcola quanto mettere da parte ogni mese. Per accantonare davvero il denaro usa un " +
                        "Trasferimento con il tipo Accantonamento, anche come ricorrente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Riepilogo", style = MaterialTheme.typography.titleSmall)
                    SummaryLine("Totale previsto", formatMoney(summary.totalCents))
                    SummaryLine("Accantonato", formatMoney(summary.poolCents))
                    SummaryLine("Ancora da accantonare", formatMoney(summary.missingCents))
                    if (summary.poolCents > summary.coveredCents) {
                        SummaryLine("Accantonato in più del previsto", formatMoney(summary.poolCents - summary.coveredCents))
                    }
                    Text(
                        "Da mettere da parte ogni mese", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(formatMoney(summary.monthlyCents), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (items.isEmpty()) item { Text("Nessuna spesa irregolare. Tocca + per aggiungerne una.") }
            items(items, key = { it.id }) { e ->
                val row = rowById[e.id]
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(e.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${formatDate(LocalDate.ofEpochDay(e.dueEpochDay))}" +
                                    (row?.let { " · tra ${it.monthsLeft} " + if (it.monthsLeft == 1) "mese" else "mesi" } ?: "") +
                                    if (e.repeatYearly) " · ogni anno" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatMoney(e.estimatedCents), style = MaterialTheme.typography.titleMedium)
                    }
                    if (row != null) {
                        val fraction = if (row.estimatedCents > 0) row.coveredCents.toFloat() / row.estimatedCents else 0f
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Text(
                            "Coperti ${formatMoney(row.coveredCents)} · mancano ${formatMoney(row.missingCents)}" +
                                if (row.monthlyCents > 0) " · ${formatMoney(row.monthlyCents)} al mese" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    TextButton(onClick = { paying = e }) { Text("Segna come pagata") }
                }
            }
        }
    }

    paying?.let { e ->
        PayIrregularDialog(
            item = e, pool = pool,
            accounts = selectableAccounts(allAccounts, defaultSource), defaultSource = defaultSource,
            onDismiss = { paying = null },
            onConfirm = { cost, payId, date, use, sourceId ->
                scope.launch {
                    val error = vm.pay(e.id, cost, payId, date, use, sourceId)
                    Toast.makeText(context, error ?: "Pagamento registrato", Toast.LENGTH_SHORT).show()
                }
                paying = null
            }
        )
    }
}

@Composable
private fun PayIrregularDialog(
    item: IrregularExpenseEntity,
    pool: Long,
    accounts: List<AccountEntity>,
    defaultSource: Long?,
    onDismiss: () -> Unit,
    onConfirm: (cost: Long, payAccountId: Long, date: LocalDate, use: Long, sourceAccountId: Long?) -> Unit
) {
    var costText by remember { mutableStateOf(centsToInput(item.estimatedCents)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payId by remember { mutableStateOf<Long?>(null) }
    var useText by remember { mutableStateOf(IrregularPlan.usable(pool, item.estimatedCents).let { if (it > 0) centsToInput(it) else "" }) }
    var sourceId by remember(defaultSource) { mutableStateOf<Long?>(defaultSource) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val effectivePay = payId ?: accounts.firstOrNull { !it.hidden }?.id ?: accounts.firstOrNull()?.id
    val effectiveSource = sourceId ?: effectivePay
    val useCents = parseAmountToCents(useText) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagamento: ${item.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyInput("Importo pagato (€)", costText) { costText = it; error = null }
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Data: ${formatDate(date)}")
                }
                AccountField("Pagato con", accounts, effectivePay) { payId = it; error = null }
                if (pool > 0) {
                    Text(
                        "Accantonamento disponibile: ${formatMoney(pool)}. Puoi usarlo in tutto o in parte per questo pagamento.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyInput("Usa dall'accantonamento (€, vuoto = niente)", useText) { useText = it; error = null }
                    if (useCents > 0) {
                        AccountField("L'accantonamento sta su", accounts, effectiveSource) { sourceId = it; error = null }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cost = parseAmountToCents(costText)
                when {
                    cost == null -> error = "Inserisci l'importo pagato."
                    effectivePay == null -> error = "Non ci sono account."
                    useCents > minOf(pool, cost) ->
                        error = "Puoi usare al massimo ${formatMoney(minOf(pool, cost))} dell'accantonamento."
                    else -> onConfirm(cost, effectivePay, date, useCents, if (useCents > 0) effectiveSource else null)
                }
            }) { Text("Registra") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
    if (showDate) {
        DatePickerDialogBox(initial = date, onDismiss = { showDate = false }, onPick = { date = it; showDate = false })
    }
}

@Composable
fun IrregularFormScreen(id: Long?, onClose: () -> Unit, vm: IrregularViewModel = viewModel()) {
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(6)) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var repeatYearly by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            vm.byId(id)?.let { e ->
                name = e.name
                amountText = centsToInput(e.estimatedCents)
                dueDate = LocalDate.ofEpochDay(e.dueEpochDay)
                categoryId = e.categoryId
                repeatYearly = e.repeatYearly
            }
            loaded = true
        }
    }

    ScreenScaffold(if (id == null) "Nuova spesa irregolare" else "Modifica spesa irregolare", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome (es. Assicurazione auto)", name) { name = it; error = null }
            MoneyInput("Importo previsto (€)", amountText) { amountText = it; error = null }
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Data prevista: ${formatDate(dueDate)}")
            }
            CategoryField(categories, categoryId, onSelect = { categoryId = it }, onNew = { showNewCategory = true })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Si ripete ogni anno")
                    Text(
                        "Dopo il pagamento la spesa si ripresenta l'anno dopo, con lo stesso importo previsto.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseAmountToCents(amountText)
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        cents == null -> error = "Inserisci un importo maggiore di zero."
                        else -> scope.launch {
                            vm.save(
                                IrregularExpenseEntity(
                                    id = id ?: 0, name = name.trim(), estimatedCents = cents,
                                    dueEpochDay = dueDate.toEpochDay(), categoryId = categoryId,
                                    repeatYearly = repeatYearly
                                )
                            )
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina spesa", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDate) {
        DatePickerDialogBox(initial = dueDate, onDismiss = { showDate = false }, onPick = { dueDate = it; showDate = false })
    }
    if (showNewCategory) {
        NewCategoryDialog(
            parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() },
            onDismiss = { showNewCategory = false },
            onCreate = { n, p, i -> scope.launch { categoryId = vm.createCategory(n, p, i) }; showNewCategory = false }
        )
    }
    if (confirmDelete && id != null) {
        ConfirmDialog(
            text = "Eliminare la spesa irregolare? L'accantonamento già messo da parte resta disponibile.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.delete(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}
