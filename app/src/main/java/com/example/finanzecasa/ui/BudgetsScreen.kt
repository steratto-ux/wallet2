package com.example.finanzecasa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Elenco dei budget mensili. Tocca un budget per modificarlo. */
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onOpenForm: (Long?) -> Unit,
    onOpenHistory: (Long) -> Unit,
    vm: ManageViewModel = viewModel()
) {
    val budgets by vm.budgets.collectAsState()
    val now by remember { minuteClock() }.collectAsState(initial = LocalDateTime.now())

    ScreenScaffold(
        title = "Budget", onBack = onBack,
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
                    "I budget sono mensili e ripartono ogni primo del mese. Compaiono nella schermata principale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (budgets.isEmpty()) item { Text("Nessun budget. Tocca + per crearne uno.") }
            items(budgets, key = { it.id }) { b ->
                BudgetCard(b, now, onHistory = { onOpenHistory(b.id) }, onClick = { onOpenForm(b.id) })
            }
        }
    }
}

/** Creazione o modifica di un budget: nome, importo mensile e una o più categorie. */
@Composable
fun BudgetFormScreen(id: Long?, onClose: () -> Unit, vm: ManageViewModel = viewModel()) {
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedCsv by rememberSaveable { mutableStateOf("") } // id delle categorie scelte, separati da virgola
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selected: Set<Long> = selectedCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()
    fun setSelected(ids: Set<Long>) { selectedCsv = ids.joinToString(","); error = null }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            vm.budgetById(id)?.let { b ->
                name = b.name
                amountText = centsToInput(b.amountCents)
                selectedCsv = vm.budgetCategoryIds(id).joinToString(",")
            }
            loaded = true
        }
    }

    val byId = categories.associateBy { it.id }
    val parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
    val childrenOf = categories.filter { it.parentId != null }.groupBy { it.parentId }

    ScreenScaffold(if (id == null) "Nuovo budget" else "Modifica budget", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome (es. Spesa alimentare)", name) { name = it; error = null }
            MoneyInput("Importo mensile (€)", amountText) { amountText = it; error = null }

            Text("Categorie incluse", style = MaterialTheme.typography.titleMedium)
            Text(
                "Scegliendo una categoria principale sono incluse anche tutte le sue sotto-categorie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (parents.isEmpty()) Text("Non ci sono categorie: creale dalla schermata Categorie.")

            parents.forEach { parent ->
                CategoryCheckRow(
                    label = parent.name, iconKey = resolveCategoryIcon(parent, byId), indent = 0,
                    checked = parent.id in selected, enabled = true,
                    onChange = { on ->
                        val children = childrenOf[parent.id].orEmpty().map { it.id }.toSet()
                        // Se scelgo la principale, le sotto-categorie sono già incluse: tolgo quelle scelte singolarmente.
                        setSelected(if (on) (selected - children) + parent.id else selected - parent.id)
                    }
                )
                childrenOf[parent.id].orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
                    val viaParent = parent.id in selected
                    CategoryCheckRow(
                        label = child.name, iconKey = resolveCategoryIcon(child, byId), indent = 1,
                        checked = viaParent || child.id in selected, enabled = !viaParent,
                        onChange = { on -> setSelected(if (on) selected + child.id else selected - child.id) }
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseAmountToCents(amountText)
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        cents == null -> error = "Inserisci un importo mensile maggiore di zero."
                        selected.isEmpty() -> error = "Scegli almeno una categoria."
                        else -> scope.launch {
                            vm.saveBudget(id, name.trim(), cents, selected)
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina budget", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && id != null) {
        ConfirmDialog(
            text = "Eliminare il budget? Le transazioni non vengono toccate.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.deleteBudget(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun CategoryCheckRow(
    label: String, iconKey: String, indent: Int,
    checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = (indent * 28).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        CategoryBadge(iconKey, size = if (indent == 0) 32.dp else 28.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
    }
}
