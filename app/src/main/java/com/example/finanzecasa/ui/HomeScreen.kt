package com.example.finanzecasa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.data.TxKind
import kotlin.math.abs

private val IncomeGreen = Color(0xFF2E7D32)

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onNewAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onOpen: (String) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val status by vm.importStatus.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Finanze di casa", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importa CSV") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("categories" to "Categorie", "subscriptions" to "Abbonamenti", "loans" to "Prestiti").forEach { (route, label) ->
                        OutlinedButton(
                            onClick = { onOpen(route) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text(label, maxLines = 1) }
                    }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryBox("Spese del mese", formatMoney(state.monthExpensesCents), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    SummaryBox("Entrate del mese", formatMoney(state.monthIncomeCents), IncomeGreen, Modifier.weight(1f))
                }
            }

            // 3. Patrimonio netto
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Patrimonio netto", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatMoney(state.netWorthCents),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.netWorthCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 4. Ultime transazioni
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ultime transazioni", style = MaterialTheme.typography.labelLarge)
                        if (state.recent.isEmpty()) {
                            Text("Nessuna transazione", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                        state.recent.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider()
                            TransactionItem(t)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountBox(a: AccountBalance, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatMoney(a.balanceCents),
                style = MaterialTheme.typography.titleMedium,
                color = if (a.balanceCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
    }
}

@Composable
private fun TransactionItem(t: TransactionRow) {
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
        else -> IncomeGreen
    }
    val amount = when {
        isTransfer -> formatMoney(abs(t.amountCents))
        t.amountCents > 0 -> "+" + formatMoney(t.amountCents)
        else -> formatMoney(t.amountCents)
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$where · ${formatDay(t.dateMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}
