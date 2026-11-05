package com.example.finanzecasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Indietro") } }
            )
        },
        floatingActionButton = floatingActionButton,
        content = content
    )
}

/** Colonna scorrevole per i moduli. */
@Composable
fun FormColumn(inner: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun MoneyInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun IntInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit)) }, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun AccountField(label: String, accounts: List<AccountEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    DropdownField(
        label = label,
        value = accounts.firstOrNull { it.id == selectedId }?.name ?: "Nessun account",
        entries = accounts.map { a -> a.name to { onSelect(a.id) } }
    )
}

@Composable
fun CategoryField(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit
) {
    val options = buildCategoryOptions(categories)
    DropdownField(
        label = "Categoria",
        value = options.firstOrNull { it.id == selectedId }?.label ?: "Nessuna categoria",
        entries = listOf<Pair<String, () -> Unit>>("Nessuna categoria" to { onSelect(null) }) +
            options.map { o -> o.label to { onSelect(o.id) } } +
            listOf<Pair<String, () -> Unit>>("+ Nuova categoria…" to onNew)
    )
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
