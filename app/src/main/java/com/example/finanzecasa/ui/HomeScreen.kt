package com.example.finanzecasa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.IconRules
import kotlin.math.abs

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onNewAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onOpen: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenBudget: (Long) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val status by vm.importStatus.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Outlined.Add, contentDescription = "Nuova transazione") }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Finanze di casa", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text("Importa CSV", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "categories" to "Categorie", "budgets" to "Budget",
                        "subscriptions" to "Abbonamenti", "loans" to "Prestiti"
                    ).forEach { (route, label) -> NavChip(label, { onOpen(route) }, Modifier.weight(1f)) }
                }
            }
            if (status.isNotEmpty()) item { Text(status, style = MaterialTheme.typography.bodySmall) }

            // 1. Account (due per riga)
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNewAccount) { Text("+ Nuovo account") }
                }
            }
            if (state.loaded && state.accounts.isEmpty()) {
                item { Text("Nessun account: creane uno o importa un CSV.") }
            }
            items(state.accounts.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { AccountBox(it, Modifier.weight(1f).fillMaxHeight(), onClick = { onEditAccount(it.id) }) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // 2. Spese ed entrate del mese in corso
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryBox("Spese del mese", formatMoney(state.monthExpensesCents), MaterialTheme.colorScheme.error, Modifier.weight(1f).fillMaxHeight())
                    SummaryBox("Entrate del mese", formatMoney(state.monthIncomeCents), MaterialTheme.colorScheme.income, Modifier.weight(1f).fillMaxHeight())
                }
            }

            // 3. Budget mensili
            if (state.budgets.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOpen("budgets") }) { Text("Gestisci") }
                    }
                }
                items(state.budgets, key = { "budget-${it.id}" }) { b ->
                    BudgetCard(b, state.now, onClick = { onOpenBudget(b.id) })
                }
            }

            // 4. Patrimonio netto con andamento del saldo
            item {
                NetWorthCard(
                    netWorthCents = state.netWorthCents,
                    points = state.chart,
                    range = state.range,
                    onRangeChange = vm::selectRange
                )
            }

            // 5. Ultime transazioni
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ultime transazioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOpen("transactions") }) { Text("Vedi tutte") }
                    }
                    if (state.recent.isEmpty()) {
                        Text("Nessuna transazione", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    state.recent.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TransactionItem(t) { onOpenTransaction(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountBox(a: AccountBalance, modifier: Modifier, onClick: () -> Unit) {
    val tint = accountTint(a.type, isSystemInDarkTheme())
    val icon = when (a.type) {
        AccountType.BANK -> Icons.Outlined.AccountBalance
        AccountType.CASH -> Icons.Outlined.AccountBalanceWallet
        AccountType.CREDIT_CARD -> Icons.Outlined.CreditCard
    }
    AppCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            a.name, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            formatMoney(a.balanceCents),
            style = MaterialTheme.typography.titleMedium,
            color = if (a.balanceCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryBox(label: String, value: String, valueColor: Color, modifier: Modifier) {
    AppCard(modifier, padding = 14.dp) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

@Composable
fun TransactionItem(t: TransactionRow, onClick: (() -> Unit)? = null) {
    val isTransfer = t.kind == TxKind.TRANSFER
    val category = listOfNotNull(t.parentCategoryName, t.categoryName).joinToString(" › ")
    val title = t.title.ifBlank {
        category.ifBlank { if (isTransfer) "Trasferimento" else "Senza titolo" }
    }
    val where = if (isTransfer) "${t.accountName} → ${t.counterpartName ?: "?"}"
    else listOf(t.accountName, category).filter { it.isNotBlank() }.joinToString(" · ")

    val color = when {
        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        t.amountCents < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.income
    }
    val amount = when {
        isTransfer -> formatMoney(abs(t.amountCents))
        t.amountCents > 0 -> "+" + formatMoney(t.amountCents)
        else -> formatMoney(t.amountCents)
    }
    val iconKey = if (isTransfer) "transfer"
    else IconRules.resolve(t.categoryIcon, t.categoryName, t.parentCategoryIcon, t.parentCategoryName)
    val scheduled = if (t.dateMillis > System.currentTimeMillis()) " · programmata" else ""
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()

    Row(
        rowModifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(iconKey, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$where · ${formatDay(t.dateMillis)}$scheduled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}
