package com.example.finanzecasa.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import com.example.finanzecasa.data.TitleSuggestion
import kotlinx.coroutines.delay
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
import com.example.finanzecasa.domain.Tags
import java.io.File
import java.util.UUID
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
    initialDate: LocalDate? = null,   // nuova transazione su un giorno scelto (dal calendario)
    vm: NewTransactionViewModel = viewModel()
) {
    val allAccounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateTime by rememberSaveable {
        mutableStateOf(
            (initialDate?.atTime(LocalTime.now()) ?: LocalDateTime.now()).truncatedTo(ChronoUnit.MINUTES)
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(transactionId == null) }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var transferFlag by rememberSaveable { mutableStateOf(0) } // 0 = nessuno, 1 = Risparmio, 2 = Accantonamento
    var suggestions by remember { mutableStateOf<List<TitleSuggestion>>(emptyList()) }
    val amountFocus = remember { FocusRequester() }
    var receiptPath by rememberSaveable { mutableStateOf<String?>(null) }
    var addedReceipt by rememberSaveable { mutableStateOf<String?>(null) } // foto aggiunta in questa schermata
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val knownTags by vm.knownTags.collectAsState()

    fun useReceipt(name: String?) {
        if (name == null) {
            Toast.makeText(context, "Impossibile leggere la foto", Toast.LENGTH_SHORT).show()
            return
        }
        if (addedReceipt != null && addedReceipt != name) vm.discardReceipt(addedReceipt)
        addedReceipt = name
        receiptPath = name
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { useReceipt(vm.importReceiptPhoto(uri)) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraUri
        if (ok && target != null) scope.launch {
            useReceipt(vm.importReceiptPhoto(target))
            runCatching { File(context.cacheDir, "camera/" + (target.lastPathSegment ?: "")).delete() }
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != null && !loaded) {
            vm.load(transactionId)?.let { t ->
                type = t.type
                title = t.title
                note = t.note
                amountText = centsToInput(t.amountCents)
                selectedAccountId = t.accountId
                selectedToId = t.toAccountId
                categoryId = t.categoryId
                dateTime = t.dateTime
                tagsText = t.tags
                receiptPath = t.receiptPath
                transferFlag = if (t.isSavings) 1 else if (t.isProvision) 2 else 0
            }
            loaded = true
        }
    }

    // Nuova transazione: tastiera subito sull'importo
    LaunchedEffect(Unit) {
        if (transactionId == null) runCatching { amountFocus.requestFocus() }
    }

    // Suggerimenti per il titolo: titoli già usati che corrispondono a quanto digitato
    LaunchedEffect(title, type) {
        if (title.isBlank() || type == TxType.TRANSFER) {
            suggestions = emptyList()
        } else {
            delay(120)
            suggestions = vm.suggestTitles(title, type).filter { it.title != title }
        }
    }

    // Gli account nascosti non si possono scegliere (tranne quelli già usati da questa transazione)
    val accounts = selectableAccounts(allAccounts, selectedAccountId, selectedToId)
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
                navigationIcon = {
                    TextButton(onClick = { vm.discardReceipt(addedReceipt); onClose() }) { Text("Annulla") }
                }
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
                label = { Text("Importo (€)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
            )
            TitleAutocomplete(
                value = title, onValueChange = { title = it },
                suggestions = suggestions,
                accountNames = accounts.associate { it.id to it.name },
                categoryNames = categories.associate { it.id to it.name },
                onPick = { s ->
                    // Scegliendo un titolo già usato si riprendono categoria e account dell'ultima volta
                    title = s.title
                    categoryId = s.categoryId?.takeIf { id -> categories.any { it.id == id } }
                    if (accounts.any { it.id == s.accountId }) selectedAccountId = s.accountId
                    suggestions = emptyList()
                }
            )

            // Nota (non per i trasferimenti)
            if (type != TxType.TRANSFER) {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth()
                )
            }

            // Tag e foto dello scontrino (non per i trasferimenti)
            if (type != TxType.TRANSFER) {
                OutlinedTextField(
                    value = tagsText, onValueChange = { tagsText = it },
                    label = { Text("Tag (separati da virgola)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                val chosen = Tags.split(Tags.normalize(tagsText)).map { it.lowercase() }.toSet()
                val suggestions = knownTags.filter { it.lowercase() !in chosen }.take(12)
                if (suggestions.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { tag ->
                            AssistChip(onClick = { tagsText = Tags.add(tagsText, tag) }, label = { Text("#$tag") })
                        }
                    }
                }

                Text("Scontrino", style = MaterialTheme.typography.labelLarge)
                val photo = receiptPath
                if (photo != null) {
                    ReceiptImage(photo)
                    OutlinedButton(
                        onClick = {
                            if (photo == addedReceipt) { vm.discardReceipt(photo); addedReceipt = null }
                            receiptPath = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rimuovi foto") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
                                val file = File(dir, "${UUID.randomUUID()}.jpg")
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Scatta foto") }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Dalla galleria") }
                    }
                }
            }

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
                Text("Tipo di trasferimento", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(
                    options = listOf(0 to "Normale", 1 to "Risparmio", 2 to "Accantonamento"),
                    selected = transferFlag, onSelect = { transferFlag = it }
                )
                Text(
                    when (transferFlag) {
                        1 -> "Conta nel 20% della regola 50/30/20 (Analisi)."
                        2 -> "Mette da parte denaro per le spese irregolari (assicurazione, bollo, vacanze…). Lo ritrovi in Spese irregolari."
                        else -> "Sposta denaro tra due tuoi account, senza cambiare il patrimonio."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                vm.save(type, title.trim(), cents, accountId, toId, categoryId, dateTime, note.trim(), tagsText, receiptPath, transferFlag == 1 && type == TxType.TRANSFER, transferFlag == 2 && type == TxType.TRANSFER)
                                runCatching { AppSettings(context).setLastEntryEpochDay(LocalDate.now().toEpochDay()) }
                            } else {
                                vm.update(transactionId, type, title.trim(), cents, accountId, toId, categoryId, dateTime, note.trim(), tagsText, receiptPath, transferFlag == 1 && type == TxType.TRANSFER, transferFlag == 2 && type == TxType.TRANSFER)
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
            onCreate = { name, parentId, icon ->
                scope.launch { categoryId = vm.createCategory(name, parentId, icon) }
                showNewCategory = false
            }
        )
    }
}

/**
 * Campo titolo con completamento automatico: mentre si scrive propone i titoli già usati
 * (con account e categoria dell'ultima volta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<TitleSuggestion>,
    accountNames: Map<Long, String>,
    categoryNames: Map<Long, String>,
    onPick: (TitleSuggestion) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Titolo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            suggestions.forEach { s ->
                val details = listOfNotNull(s.categoryId?.let { categoryNames[it] }, accountNames[s.accountId]).joinToString(" · ")
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(s.title)
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = { onPick(s); expanded = false }
                )
            }
        }
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
    onCreate: (name: String, parentId: Long?, icon: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<String?>(null) }
    var parent by remember { mutableStateOf<CategoryEntity?>(null) }
    val none = "Nessuna (categoria principale)"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                DropdownField(
                    label = "Sotto-categoria di",
                    value = parent?.name ?: none,
                    entries = listOf(none to { parent = null }) + parents.map { p -> p.name to { parent = p } }
                )
                Text("Icona", style = MaterialTheme.typography.labelLarge)
                IconPicker(selected = icon, previewName = name, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), parent?.id, icon) }) { Text("Crea") }
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
