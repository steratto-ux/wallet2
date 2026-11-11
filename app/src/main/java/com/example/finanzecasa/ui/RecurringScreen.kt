package com.example.finanzecasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.finanzecasa.domain.RecurringSchedule
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.RecurringEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** Importo mensile equivalente di una ricorrente (le settimanali valgono 52/12 al mese, le annuali 1/12). */
private fun monthlyEquivalent(r: RecurringEntity): Long = when (r.cadence) {
    Cadence.WEEKLY -> r.amountCents * 52 / 12
    Cadence.MONTHLY -> r.amountCents
    Cadence.YEARLY -> r.amountCents / 12
}

private fun cadenceName(c: Cadence) = when (c) {
    Cadence.WEEKLY -> "Settimanale"
    Cadence.MONTHLY -> "Mensile"
    Cadence.YEARLY -> "Annuale"
}

/** Elenco delle transazioni ricorrenti (stipendio, affitto, bollette...). */
@Composable
fun RecurringScreen(onBack: () -> Unit, onOpenForm: (Long?) -> Unit, vm: ManageViewModel = viewModel()) {
    val list by vm.recurring.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val accountNames = accounts.associate { it.id to it.name }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var registering by remember { mutableStateOf<RecurringEntity?>(null) }

    ScreenScaffold(
        title = "Ricorrenti", onBack = onBack,
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
                    "Entrate, uscite e trasferimenti che si ripetono: alla scadenza l'app crea da sola la transazione. " +
                        "Con un trasferimento ricorrente puoi mettere da parte il risparmio o l'accantonamento in automatico. " +
                        "Puoi cambiare l'importo quando vuoi: vale dalle prossime scadenze.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (list.isEmpty()) item { Text("Nessuna transazione ricorrente. Tocca + per aggiungerne una.") }
            items(list, key = { it.id }) { r ->
                val isTransfer = r.toAccountId != null
                val income = r.amountCents > 0 && !isTransfer
                val flagLabel = when (r.transferFlag) { 1 -> " · Risparmio"; 2 -> " · Accantonamento"; else -> "" }
                val where = if (isTransfer) {
                    "${accountNames[r.accountId] ?: "?"} → ${accountNames[r.toAccountId] ?: "?"}$flagLabel"
                } else accountNames[r.accountId] ?: "?"
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(r.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val finished = r.remainingOccurrences < 0
                            Text(
                                r.title + when { finished -> " (conclusa)"; !r.active -> " (in pausa)"; else -> "" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            val durationLabel = when {
                                finished -> " · ultima scadenza registrata"
                                r.remainingOccurrences == 1 -> " · ultima scadenza"
                                r.remainingOccurrences > 1 -> " · ancora ${r.remainingOccurrences} scadenze"
                                else -> ""
                            }
                            Text(
                                "${cadenceName(r.cadence)}$durationLabel · prossima ${formatDate(LocalDate.ofEpochDay(r.nextDueEpochDay))} · $where",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (income) "+" else "") + formatMoney(r.amountCents),
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                isTransfer -> MaterialTheme.colorScheme.onSurface
                                income -> MaterialTheme.colorScheme.income
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (r.active) {
                        TextButton(onClick = { registering = r }) { Text("Registra ora") }
                    }
                }
            }
        }
    }

    registering?.let { r ->
        val due = LocalDate.ofEpochDay(r.nextDueEpochDay)
        val next = RecurringSchedule.following(due, r.cadence, r.anchorDay)
        ConfirmDialog(
            text = "Registrare adesso \"${r.title}\" (${formatMoney(r.amountCents)}) con la data di oggi? " +
                "La scadenza del ${formatDate(due)} verrà considerata pagata e la prossima sarà il ${formatDate(next)}.",
            confirmLabel = "Registra",
            onConfirm = {
                scope.launch {
                    vm.registerRecurringNow(r.id)
                    Toast.makeText(context, "Registrata: ${r.title}", Toast.LENGTH_SHORT).show()
                }
                registering = null
            },
            onDismiss = { registering = null }
        )
    }
}

@Composable
fun RecurringFormScreen(id: Long?, onClose: () -> Unit, vm: ManageViewModel = viewModel()) {
    val allAccounts by vm.accounts.collectAsState()
    val allRecurring by vm.recurring.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var isTransfer by rememberSaveable { mutableStateOf(false) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transferFlag by rememberSaveable { mutableStateOf(0) } // 0 = nessuno, 1 = risparmio, 2 = accantonamento
    var cadence by rememberSaveable { mutableStateOf(Cadence.MONTHLY) }
    var forever by rememberSaveable { mutableStateOf(true) }   // durata: per sempre oppure un numero di scadenze
    var countText by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var active by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            vm.recurringById(id)?.let { r ->
                title = r.title
                amountText = centsToInput(abs(r.amountCents))
                income = r.amountCents > 0 && r.toAccountId == null
                isTransfer = r.toAccountId != null
                toAccountId = r.toAccountId
                transferFlag = r.transferFlag
                cadence = r.cadence
                forever = r.remainingOccurrences <= 0
                countText = if (r.remainingOccurrences > 0) r.remainingOccurrences.toString() else ""
                dueDate = LocalDate.ofEpochDay(r.nextDueEpochDay)
                accountId = r.accountId
                categoryId = r.categoryId
                active = r.active
            }
            loaded = true
        }
    }
    // Gli account nascosti non si possono scegliere (tranne quelli già usati da questa ricorrente)
    val accounts = selectableAccounts(allAccounts, accountId, toAccountId)
    val effectiveAccountId = accountId?.takeIf { a -> accounts.any { it.id == a } } ?: accounts.firstOrNull()?.id
    val effectiveToId = toAccountId?.takeIf { t -> t != effectiveAccountId && accounts.any { it.id == t } }
        ?: accounts.firstOrNull { it.id != effectiveAccountId }?.id

    ScreenScaffold(if (id == null) "Nuova ricorrente" else "Modifica ricorrente", onBack = onClose) { inner ->
        FormColumn(inner) {
            SegmentedChoice(
                options = listOf(0 to "Uscita", 1 to "Entrata", 2 to "Trasferimento"),
                selected = if (isTransfer) 2 else if (income) 1 else 0,
                onSelect = { isTransfer = it == 2; income = it == 1; error = null }
            )
            TextInput("Titolo (es. Stipendio, Affitto)", title) { title = it; error = null }
            MoneyInput("Importo (€)", amountText) { amountText = it; error = null }
            SegmentedChoice(
                options = listOf(Cadence.WEEKLY to "Settimanale", Cadence.MONTHLY to "Mensile", Cadence.YEARLY to "Annuale"),
                selected = cadence, onSelect = { cadence = it }
            )
            Text("Durata", style = MaterialTheme.typography.labelLarge)
            SegmentedChoice(
                options = listOf(true to "Per sempre", false to "Numero di scadenze"),
                selected = forever, onSelect = { forever = it; error = null }
            )
            if (!forever) {
                IntInput(if (id == null) "Quante scadenze in tutto (es. 3 rate)" else "Scadenze ancora da registrare", countText) {
                    countText = it.take(4); error = null
                }
                val n = countText.toIntOrNull()
                val each = parseAmountToCents(amountText)
                Text(
                    if (n != null && n > 0 && each != null)
                        "${n} × ${formatMoney(each)} = ${formatMoney(each * n)}. Dopo l'ultima scadenza la ricorrente si ferma da sola."
                    else "Dopo l'ultima scadenza la ricorrente si ferma da sola.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (forever) "Prossima scadenza: ${formatDate(dueDate)}" else "Prima scadenza: ${formatDate(dueDate)}")
            }
            if (dueDate.isBefore(LocalDate.now())) {
                Text(
                    "Data passata: le scadenze già trascorse verranno registrate automaticamente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AccountField(if (isTransfer) "Da account" else "Account", accounts, effectiveAccountId) { accountId = it; error = null }
            if (isTransfer) {
                AccountField("A account", accounts.filter { it.id != effectiveAccountId }, effectiveToId) { toAccountId = it; error = null }
                Text("Tipo di trasferimento", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(
                    options = listOf(0 to "Normale", 1 to "Risparmio", 2 to "Accantonamento"),
                    selected = transferFlag, onSelect = { transferFlag = it }
                )
                val monthlyIncome = allRecurring
                    .filter { it.active && it.toAccountId == null && it.amountCents > 0 && it.id != id }
                    .sumOf { monthlyEquivalent(it) }
                Text(
                    when {
                        transferFlag == 1 && monthlyIncome > 0 ->
                            "Conta nel 20% della regola 50/30/20. Il 20% delle tue entrate ricorrenti è circa ${formatMoney(monthlyIncome / 5)} al mese."
                        transferFlag == 1 -> "Conta nel 20% della regola 50/30/20 (Analisi)."
                        transferFlag == 2 -> "Mette da parte denaro per le spese irregolari: lo ritrovi in Spese irregolari."
                        else -> "Sposta denaro tra due tuoi account, senza cambiare il patrimonio."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                CategoryField(categories, categoryId, onSelect = { categoryId = it }, onNew = { showNewCategory = true })
            }
            if (id != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attiva", Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseAmountToCents(amountText)
                    val count = countText.toIntOrNull()
                    when {
                        title.isBlank() -> error = "Inserisci un titolo."
                        !forever && (count == null || count < 1) -> error = "Inserisci quante scadenze (almeno 1)."
                        cents == null -> error = "Inserisci un importo maggiore di zero."
                        effectiveAccountId == null -> error = "Non ci sono account: creane uno dalla schermata principale."
                        isTransfer && (effectiveToId == null || effectiveToId == effectiveAccountId) ->
                            error = "Scegli due account diversi."
                        else -> scope.launch {
                            vm.saveRecurring(
                                RecurringEntity(
                                    id = id ?: 0, title = title.trim(),
                                    amountCents = if (isTransfer || income) cents else -cents,
                                    cadence = cadence, anchorDay = dueDate.dayOfMonth,
                                    nextDueEpochDay = dueDate.toEpochDay(),
                                    accountId = effectiveAccountId,
                                    categoryId = if (isTransfer) null else categoryId, active = active,
                                    toAccountId = if (isTransfer) effectiveToId else null,
                                    transferFlag = if (isTransfer) transferFlag else 0,
                                    remainingOccurrences = if (forever) RecurringSchedule.FOREVER else (count ?: 1),
                                )
                            )
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina ricorrente", color = MaterialTheme.colorScheme.error)
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
            text = "Eliminare la ricorrente? Le transazioni già registrate restano.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.deleteRecurring(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}
