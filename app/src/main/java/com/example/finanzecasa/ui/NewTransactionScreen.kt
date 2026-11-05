package com.example.finanzecasa.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.CategoryEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal data class CategoryOption(val id: Long, val label: String)

/** Categorie principali in ordine alfabetico, ciascuna seguita dalle sue sotto-categorie ("Casa › Luce"). */
internal fun buildCategoryOptions(all: List<CategoryEntity>): List<CategoryOption> {
    val children = all.filter { it.parentId != null }.groupBy { it.parentId }
    return all.filter { it.parentId == null }.sortedBy { it.name.lowercase() }.flatMap { parent ->
        listOf(CategoryOption(parent.id, parent.name)) +
            children[parent.id].orEmpty().sortedBy { it.name.lowercase() }
                .map { CategoryOption(it.id, "${parent.name} › ${it.name}") }
    }
}

/** Nuova transazione (transactionId == null) oppure modifica/eliminazione di una esistente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransactionScreen(
    transactionId: Long? = null,
    onClose: () -> Unit,
    vm: NewTransactionViewModel = viewModel()
) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateTime by rememberSaveable { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(transactionId == null) }

    LaunchedEffect(transactionId) {
        if (transactionId != null && !loaded) {
            vm.load(transactionId)?.let { t ->
                type = t.type
                title = t.title
                amountText = centsToInput(t.amountCents)
                selectedAccountId = t.accountId
                selectedToId = t.toAccountId
                categoryId = t.categoryId
                dateTime = t.dateTime
            }
            loaded = true
        }
    }

    val accountId = selectedAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id
    val toId = selectedToId?.takeIf { id -> id != accountId && accounts.any { it.id == id } }
        ?: accounts.firstOrNull { it.id != accountId }?.id
    val categoryOptions = buildCategoryOptions(categories)
    val isFuture = dateTime.isAfter(LocalDateTime.now())
    val whenFmt = DateTimeFormatter.ofPattern("d MMM yyyy 'alle' HH:mm", Locale.ITALIAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transactionId == null) "Nuova transazione" else "Modifica transazione") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Annulla") } }
            )
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tipologia
            val labels = mapOf(TxType.EXPENSE to "Uscita", TxType.INCOME to "Entrata", TxType.TRANSFER to "Trasferimento")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.values().forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, TxType.values().size)
                    ) { Text(labels.getValue(t), maxLines = 1) }
                }
            }

            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it; error = null },
                label = { Text("Importo (€)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            // Categoria (non per i trasferimenti)
            if (type != TxType.TRANSFER) {
                val current = categoryOptions.firstOrNull { it.id == categoryId }?.label ?: "Nessuna categoria"
                DropdownField(
                    label = "Categoria", value = current,
                    entries = listOf("Nessuna categoria" to { categoryId = null }) +
                        categoryOptions.map { opt -> opt.label to { categoryId = opt.id } } +
                        listOf("+ Nuova categoria…" to { showNewCategory = true })
                )
            }

            // Account
            val accountEntries = accounts.map { a -> a.name to { selectedAccountId = a.id; error = null } }
            DropdownField(
                label = if (type == TxType.TRANSFER) "Da account" else "Account",
                value = accounts.firstOrNull { it.id == accountId }?.name ?: "Nessun account",
                entries = accountEntries
            )
            if (type == TxType.TRANSFER) {
                DropdownField(
                    label = "A account",
                    value = accounts.firstOrNull { it.id == toId }?.name ?: "Nessun account",
                    entries = accounts.filter { it.id != accountId }.map { a -> a.name to { selectedToId = a.id; error = null } }
                )
            }

            // Data e ora
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)))
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                    Text(dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
            if (isFuture) {
                Text(
                    "Data futura: la transazione verrà conteggiata il ${dateTime.format(whenFmt)}.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseAmountToCents(amountText)
                    when {
                        accounts.isEmpty() -> error = "Non ci sono account: creane uno dalla schermata principale."
                        cents == null -> error = "Inserisci un importo maggiore di zero."
                        accountId == null -> error = "Seleziona un account."
                        type == TxType.TRANSFER && (toId == null || toId == accountId) ->
                            error = "Scegli due account diversi."
                        else -> scope.launch {
                            if (transactionId == null) {
                                vm.save(type, title.trim(), cents, accountId, toId, categoryId, dateTime)
                            } else {
                                vm.update(transactionId, type, title.trim(), cents, accountId, toId, categoryId, dateTime)
                            }
                            val msg = if (isFuture) "Programmata: verrà conteggiata il ${dateTime.format(whenFmt)}"
                            else "Transazione salvata"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }

            if (transactionId != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina transazione", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && transactionId != null) {
        ConfirmDialog(
            text = if (type == TxType.TRANSFER) "Eliminare il trasferimento? Verranno tolte entrambe le righe."
            else "Eliminare la transazione?",
            confirmLabel = "Elimina",
            onConfirm = {
                scope.launch {
                    vm.delete(transactionId)
                    Toast.makeText(context, "Transazione eliminata", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
    if (showDate) {
        DatePickerDialogBox(
            initial = dateTime.toLocalDate(),
            onDismiss = { showDate = false },
            onPick = { d -> dateTime = LocalDateTime.of(d, dateTime.toLocalTime()); showDate = false }
        )
    }
    if (showTime) {
        TimePickerDialogBox(
            initial = dateTime.toLocalTime(),
            onDismiss = { showTime = false },
            onPick = { t -> dateTime = LocalDateTime.of(dateTime.toLocalDate(), t); showTime = false }
        )
    }
    if (showNewCategory) {
        NewCategoryDialog(
            parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() },
            onDismiss = { showNewCategory = false },
            onCreate = { name, parentId ->
                scope.launch { categoryId = vm.createCategory(name, parentId) }
                showNewCategory = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, entries: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { action(); expanded = false })
            }
        }
    }
}

@Composable
fun NewCategoryDialog(
    parents: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, parentId: Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf<CategoryEntity?>(null) }
    val none = "Nessuna (categoria principale)"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                DropdownField(
                    label = "Sotto-categoria di",
                    value = parent?.name ?: none,
                    entries = listOf(none to { parent = null }) + parents.map { p -> p.name to { parent = p } }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), parent?.id) }) { Text("Crea") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogBox(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogBox(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
