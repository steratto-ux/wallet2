package com.example.finanzecasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.finanzecasa.domain.IconRules
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
fun TransactionsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenScheduled: (formRoute: String, id: Long) -> Unit,
    vm: TransactionsViewModel = viewModel()
) {
    val rows by vm.rows.collectAsState()
    val scheduled by vm.scheduled.collectAsState()
    val total by vm.total.collectAsState()
    val categories by vm.categories.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val categoryFilter by vm.categoryFilter.collectAsState()
    val showScheduled by vm.showScheduled.collectAsState()
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
                    SegmentedChoice(
                        options = listOf(
                            TxTypeFilter.ALL to "Tutte", TxTypeFilter.EXPENSE to "Uscite",
                            TxTypeFilter.INCOME to "Entrate", TxTypeFilter.TRANSFER to "Trasf."
                        ),
                        selected = typeFilter, onSelect = { vm.typeFilter.value = it }
                    )
                    // Categorie principali, ognuna seguita dalle sue sotto-categorie
                    val parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
                    val categoryEntries = buildList<Pair<String, () -> Unit>> {
                        add("Tutte le categorie" to { vm.categoryFilter.value = null })
                        parents.forEach { p ->
                            add(p.name to { vm.categoryFilter.value = p.id })
                            categories.filter { it.parentId == p.id }.sortedBy { it.name.lowercase() }.forEach { c ->
                                add("      ${c.name}" to { vm.categoryFilter.value = c.id })
                            }
                        }
                    }
                    DropdownField(
                        label = "Categoria",
                        value = categories.firstOrNull { it.id == categoryFilter }?.let { c ->
                            val parent = categories.firstOrNull { it.id == c.parentId }
                            if (parent != null) "${parent.name} › ${c.name}" else c.name
                        } ?: "Tutte le categorie",
                        entries = categoryEntries
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                            Text(fromDate?.let { "Dal ${formatDate(it)}" } ?: "Dal…", maxLines = 1)
                        }
                        OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
                            Text(toDate?.let { "Al ${formatDate(it)}" } ?: "Al…", maxLines = 1)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (fromDate != null || toDate != null) {
                            TextButton(onClick = { vm.fromDate.value = null; vm.toDate.value = null }) { Text("Azzera le date") }
                        }
                        Spacer(Modifier.weight(1f))
                        // Totale dell'elenco che si sta guardando (entrate meno uscite)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Totale", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                (if (total > 0) "+" else "") + formatMoney(total),
                                style = MaterialTheme.typography.titleMedium,
                                color = when {
                                    total < 0 -> MaterialTheme.colorScheme.error
                                    total > 0 -> MaterialTheme.colorScheme.income
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mostra le scadenze future", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Ricorrenti, abbonamenti e rate: solo la prossima di ognuna.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showScheduled, onCheckedChange = { vm.showScheduled.value = it })
                    }
                }
                }
                if (scheduled.isNotEmpty()) {
                    item {
                        Text("In arrivo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(scheduled, key = { it.key }) { r ->
                        ScheduledItem(r) { onOpenScheduled(r.formRoute, r.sourceId) }
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "Transazioni", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
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

/** Riga di una scadenza futura (non è ancora una transazione): tocca per aprire la ricorrente, l'abbonamento o la rata. */
@Composable
private fun ScheduledItem(r: ScheduledRow, onClick: () -> Unit) {
    val category = listOfNotNull(r.parentCategoryName, r.categoryName).joinToString(" › ")
    val where = listOf(r.kindLabel, r.accountName, category).filter { it.isNotBlank() }.joinToString(" · ")
    val color = if (r.cents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.income
    val amount = (if (r.cents > 0) "+" else "") + formatMoney(r.cents)
    val iconKey = IconRules.resolve(r.categoryIcon, r.categoryName, r.parentCategoryIcon, r.parentCategoryName)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(iconKey, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    formatDayMonth(r.date),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false
                )
            }
            Text(
                "$where · programmata", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}
