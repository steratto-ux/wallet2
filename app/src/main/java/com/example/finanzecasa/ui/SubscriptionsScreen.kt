package com.example.finanzecasa.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.example.finanzecasa.data.SubscriptionEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun cadenceLabel(c: Cadence) = when (c) {
    Cadence.WEEKLY -> "Settimanale"
    Cadence.MONTHLY -> "Mensile"
    Cadence.YEARLY -> "Annuale"
}

/** Costo annuo di un abbonamento. */
private fun annualCents(s: SubscriptionEntity): Long = when (s.cadence) {
    Cadence.WEEKLY -> s.amountCents * 52
    Cadence.MONTHLY -> s.amountCents * 12
    Cadence.YEARLY -> s.amountCents
}

@Composable
fun SubscriptionsScreen(onBack: () -> Unit, onOpenForm: (Long?) -> Unit, vm: ManageViewModel = viewModel()) {
    val subs by vm.subscriptions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val accountNames = accounts.associate { it.id to it.name }

    val active = subs.filter { it.active }
    val yearlyCents = active.sumOf { annualCents(it) }
    val toReview = active.filter { it.toReview }
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = "Abbonamenti", onBack = onBack,
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
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Costo degli abbonamenti attivi", style = MaterialTheme.typography.labelLarge)
                    Text("${formatMoney(yearlyCents / 12)} al mese", style = MaterialTheme.typography.titleLarge)
                    Text("${formatMoney(yearlyCents)} all'anno", style = MaterialTheme.typography.bodyMedium)
                    if (toReview.isNotEmpty()) {
                        Text(
                            "Da rivedere: ${toReview.size} (${formatMoney(toReview.sumOf { annualCents(it) })} all'anno). " +
                                "Se li disdici, è quello che risparmi in un anno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (subs.isEmpty()) item { Text("Nessun abbonamento. Tocca + per aggiungerne uno.") }
            items(subs) { s ->
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(s.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name + (if (s.active) "" else " (in pausa)") + (if (s.toReview) " · da rivedere" else ""),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${cadenceLabel(s.cadence)} · prossima ${formatDate(LocalDate.ofEpochDay(s.nextDueEpochDay))} · ${accountNames[s.accountId] ?: "?"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(formatMoney(s.amountCents), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatMoney(annualCents(s))} all'anno",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { scope.launch { vm.setSubscriptionReview(s.id, !s.toReview) } }) {
                            Text(if (s.toReview) "Togli da rivedere" else "Da rivedere")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionFormScreen(id: Long?, onClose: () -> Unit, vm: ManageViewModel = viewModel()) {
    val allAccounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var toReview by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
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
            vm.subscriptionById(id)?.let { s ->
                name = s.name
                amountText = centsToInput(s.amountCents)
                cadence = s.cadence
                dueDate = LocalDate.ofEpochDay(s.nextDueEpochDay)
                accountId = s.accountId
                categoryId = s.categoryId
                active = s.active
                toReview = s.toReview
            }
            loaded = true
        }
    }
    val accounts = selectableAccounts(allAccounts, accountId)
    val effectiveAccountId = accountId ?: accounts.firstOrNull()?.id

    ScreenScaffold(if (id == null) "Nuovo abbonamento" else "Modifica abbonamento", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome", name) { name = it; error = null }
            MoneyInput("Importo (€)", amountText) { amountText = it; error = null }
            SegmentedChoice(
                options = listOf(Cadence.MONTHLY to "Mensile", Cadence.YEARLY to "Annuale"),
                selected = cadence, onSelect = { cadence = it }
            )
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Prossima scadenza: ${formatDate(dueDate)}")
            }
            if (dueDate.isBefore(LocalDate.now())) {
                Text(
                    "Data passata: le scadenze già trascorse verranno registrate automaticamente come uscite.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AccountField("Account", accounts, effectiveAccountId) { accountId = it; error = null }
            CategoryField(categories, categoryId, onSelect = { categoryId = it }, onNew = { showNewCategory = true })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Da rivedere")
                    Text(
                        "Ti ricordo di decidere se tenerlo, con un avviso nei giorni prima del rinnovo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = toReview, onCheckedChange = { toReview = it })
            }
            if (id != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attivo", Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseAmountToCents(amountText)
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        cents == null -> error = "Inserisci un importo maggiore di zero."
                        effectiveAccountId == null -> error = "Non ci sono account: creane uno dalla schermata principale."
                        else -> scope.launch {
                            vm.saveSubscription(
                                SubscriptionEntity(
                                    id = id ?: 0, name = name.trim(), amountCents = cents, cadence = cadence,
                                    anchorDay = dueDate.dayOfMonth, nextDueEpochDay = dueDate.toEpochDay(),
                                    accountId = effectiveAccountId, categoryId = categoryId, active = active,
                                    toReview = toReview
                                )
                            )
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina abbonamento", color = MaterialTheme.colorScheme.error)
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
            text = "Eliminare l'abbonamento? Le transazioni già registrate restano.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.deleteSubscription(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}
