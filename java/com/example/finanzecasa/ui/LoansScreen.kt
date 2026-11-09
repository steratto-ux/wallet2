package com.example.finanzecasa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.domain.LoanMath
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun rateText(rate: Double) = String.format(java.util.Locale.ITALY, "%.2f", rate).trimEnd('0').trimEnd(',')

@Composable
fun LoansScreen(onBack: () -> Unit, onOpenForm: (Long?) -> Unit, vm: ManageViewModel = viewModel()) {
    val loans by vm.loans.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val accountNames = accounts.associate { it.id to it.name }
    val residual = loans.sumOf { LoanMath.remainingPrincipalCents(it.installmentCents, it.annualRatePercent, it.remainingInstallments) }

    ScreenScaffold(
        title = "Prestiti", onBack = onBack,
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
                    Text("Debito residuo stimato", style = MaterialTheme.typography.labelLarge)
                    Text(formatMoney(residual), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Calcolato dalle rate rimanenti e dal tasso; non è sottratto dal patrimonio netto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (loans.isEmpty()) item { Text("Nessun prestito. Tocca + per aggiungerne uno.") }
            items(loans) { l ->
                val rest = LoanMath.remainingPrincipalCents(l.installmentCents, l.annualRatePercent, l.remainingInstallments)
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(l.id) }) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rata ${formatMoney(l.installmentCents)} · ${l.remainingInstallments} rate rimanenti · ${accountNames[l.accountId] ?: "?"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (l.remainingInstallments > 0)
                            "Prossima ${formatDate(LocalDate.ofEpochDay(l.nextDueEpochDay))} · debito residuo ≈ ${formatMoney(rest)}"
                        else "Estinto",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun LoanFormScreen(id: Long?, onClose: () -> Unit, vm: ManageViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var principalText by rememberSaveable { mutableStateOf("") }
    var rateTextValue by rememberSaveable { mutableStateOf("") }
    var installmentText by rememberSaveable { mutableStateOf("") }
    var remainingText by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            vm.loanById(id)?.let { l ->
                name = l.name
                principalText = centsToInput(l.principalCents)
                rateTextValue = rateText(l.annualRatePercent)
                installmentText = centsToInput(l.installmentCents)
                remainingText = l.remainingInstallments.toString()
                dueDate = LocalDate.ofEpochDay(l.nextDueEpochDay)
                accountId = l.accountId
                categoryId = l.categoryId
            }
            loaded = true
        }
    }
    val effectiveAccountId = accountId ?: accounts.firstOrNull()?.id

    ScreenScaffold(if (id == null) "Nuovo prestito" else "Modifica prestito", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome (es. Mutuo casa)", name) { name = it; error = null }
            MoneyInput("Capitale residuo (€)", principalText) { principalText = it; error = null }
            MoneyInput("Tasso annuo (%)", rateTextValue) { rateTextValue = it; error = null }
            IntInput("Rate rimanenti", remainingText) { remainingText = it; error = null }
            MoneyInput("Rata mensile (€) — vuoto = calcolata", installmentText) { installmentText = it; error = null }
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Prossima rata: ${formatDate(dueDate)}")
            }
            AccountField("Account di addebito", accounts, effectiveAccountId) { accountId = it; error = null }
            CategoryField(categories, categoryId, onSelect = { categoryId = it }, onNew = { showNewCategory = true })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val principal = parseAmountToCents(principalText)
                    val rate = if (rateTextValue.isBlank()) 0.0 else rateTextValue.replace(',', '.').toDoubleOrNull()
                    val remaining = remainingText.toIntOrNull()
                    val manualInstallment = if (installmentText.isBlank()) null else parseAmountToCents(installmentText)
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        principal == null -> error = "Inserisci il capitale (maggiore di zero)."
                        rate == null || rate < 0 -> error = "Tasso non valido."
                        remaining == null || remaining < 1 -> error = "Inserisci le rate rimanenti (almeno 1)."
                        installmentText.isNotBlank() && manualInstallment == null -> error = "Rata non valida."
                        effectiveAccountId == null -> error = "Non ci sono account: creane uno dalla schermata principale."
                        else -> scope.launch {
                            val installment = manualInstallment ?: LoanMath.installmentCents(principal, rate, remaining)
                            vm.saveLoan(
                                LoanEntity(
                                    id = id ?: 0, name = name.trim(), principalCents = principal,
                                    annualRatePercent = rate, installmentCents = installment,
                                    dueDay = dueDate.dayOfMonth, nextDueEpochDay = dueDate.toEpochDay(),
                                    remainingInstallments = remaining, accountId = effectiveAccountId,
                                    categoryId = categoryId
                                )
                            )
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina prestito", color = MaterialTheme.colorScheme.error)
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
            text = "Eliminare il prestito? Le rate già registrate restano.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.deleteLoan(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}
