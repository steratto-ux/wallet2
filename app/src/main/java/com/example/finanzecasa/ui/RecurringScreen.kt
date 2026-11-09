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
                    "Entrate e uscite che si ripetono: alla scadenza l'app crea da sola la transazione. " +
                        "Puoi cambiare l'importo quando vuoi: vale dalle prossime scadenze.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (list.isEmpty()) item { Text("Nessuna transazione ricorrente. Tocca + per aggiungerne una.") }
            items(list, key = { it.id }) { r ->
                val income = r.amountCents > 0
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(r.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title + if (r.active) "" else " (in pausa)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${cadenceName(r.cadence)} · prossima ${formatDate(LocalDate.ofEpochDay(r.nextDueEpochDay))} · ${accountNames[r.accountId] ?: "?"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (income) "+" else "") + formatMoney(r.amountCents),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (income) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.error
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
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var cadence by rememberSaveable { mutableStateOf(Cadence.MONTHLY) }
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
                income = r.amountCents > 0
                cadence = r.cadence
                dueDate = LocalDate.ofEpochDay(r.nextDueEpochDay)
                accountId = r.accountId
                categoryId = r.categoryId
                active = r.active
            }
            loaded = true
        }
    }
    val effectiveAccountId = accountId?.takeIf { a -> accounts.any { it.id == a } } ?: accounts.firstOrNull()?.id

    ScreenScaffold(if (id == null) "Nuova ricorrente" else "Modifica ricorrente", onBack = onClose) { inner ->
        FormColumn(inner) {
            SegmentedChoice(
                options = listOf(false to "Uscita", true to "Entrata"),
                selected = income, onSelect = { income = it }
            )
            TextInput("Titolo (es. Stipendio, Affitto)", title) { title = it; error = null }
            MoneyInput("Importo (€)", amountText) { amountText = it; error = null }
            SegmentedChoice(
                options = listOf(Cadence.WEEKLY to "Settimanale", Cadence.MONTHLY to "Mensile", Cadence.YEARLY to "Annuale"),
                selected = cadence, onSelect = { cadence = it }
            )
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Prossima scadenza: ${formatDate(dueDate)}")
            }
            if (dueDate.isBefore(LocalDate.now())) {
                Text(
                    "Data passata: le scadenze già trascorse verranno registrate automaticamente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AccountField("Account", accounts, effectiveAccountId) { accountId = it; error = null }
            CategoryField(categories, categoryId, onSelect = { categoryId = it }, onNew = { showNewCategory = true })
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
                    when {
                        title.isBlank() -> error = "Inserisci un titolo."
                        cents == null -> error = "Inserisci un importo maggiore di zero."
                        effectiveAccountId == null -> error = "Non ci sono account: creane uno dalla schermata principale."
                        else -> scope.launch {
                            vm.saveRecurring(
                                RecurringEntity(
                                    id = id ?: 0, title = title.trim(),
                                    amountCents = if (income) cents else -cents,
                                    cadence = cadence, anchorDay = dueDate.dayOfMonth,
                                    nextDueEpochDay = dueDate.toEpochDay(),
                                    accountId = effectiveAccountId, categoryId = categoryId, active = active
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
