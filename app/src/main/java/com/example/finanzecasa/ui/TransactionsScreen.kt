package com.example.finanzecasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import java.time.LocalDate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Elenco di tutte le transazioni, con ricerca e filtro per account. Tocca una riga per modificarla. */
@Composable
fun TransactionsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, vm: TransactionsViewModel = viewModel()) {
    val rows by vm.rows.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val filter by vm.accountFilter.collectAsState()
    val query by vm.query.collectAsState()
    val fromDate by vm.fromDate.collectAsState()
    val toDate by vm.toDate.collectAsState()
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    ScreenScaffold("Transazioni", onBack = onBack) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { vm.query.value = it },
                        label = { Text("Cerca per titolo, nota o categoria") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField(
                        label = "Account",
                        value = accounts.firstOrNull { it.id == filter }?.name ?: "Tutti gli account",
                        entries = listOf<Pair<String, () -> Unit>>("Tutti gli account" to { vm.accountFilter.value = null }) +
                            accounts.map { a -> a.name to { vm.accountFilter.value = a.id } }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                            Text(fromDate?.let { "Dal ${formatDate(it)}" } ?: "Dal…", maxLines = 1)
                        }
                        OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
                            Text(toDate?.let { "Al ${formatDate(it)}" } ?: "Al…", maxLines = 1)
                        }
                    }
                    if (fromDate != null || toDate != null) {
                        TextButton(onClick = { vm.fromDate.value = null; vm.toDate.value = null }) { Text("Azzera le date") }
                    }
                }
                }
                if (rows.isEmpty()) item { Text("Nessuna transazione trovata.") }
                items(rows, key = { it.id }) { t ->
                    TransactionItem(t) { onOpen(t.id) }
                    HorizontalDivider()
                }
            }
        }
    }

    if (pickingFrom) {
        DatePickerDialogBox(
            initial = fromDate ?: toDate ?: LocalDate.now(),
            onDismiss = { pickingFrom = false },
            onPick = { d ->
                vm.fromDate.value = d
                if (toDate?.isBefore(d) == true) vm.toDate.value = d
                pickingFrom = false
            }
        )
    }
    if (pickingTo) {
        DatePickerDialogBox(
            initial = toDate ?: fromDate ?: LocalDate.now(),
            onDismiss = { pickingTo = false },
            onPick = { d ->
                vm.toDate.value = d
                if (fromDate?.isAfter(d) == true) vm.fromDate.value = d
                pickingTo = false
            }
        )
    }
}
