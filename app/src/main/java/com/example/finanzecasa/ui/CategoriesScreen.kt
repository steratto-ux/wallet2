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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.CategoryEntity
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: ManageViewModel = viewModel()) {
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }

    val byId = categories.associateBy { it.id }
    val parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
    val childrenOf = categories.filter { it.parentId != null }.groupBy { it.parentId }

    ScreenScaffold(
        title = "Categorie", onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp)
        ) {
            if (parents.isEmpty()) item { Text("Nessuna categoria. Tocca + per crearne una.") }
            items(parents) { parent ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = parent }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryBadge(resolveCategoryIcon(parent, byId), size = 40.dp)
                        Text(parent.name, style = MaterialTheme.typography.titleMedium)
                    }
                    childrenOf[parent.id].orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = child }.padding(start = 28.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryBadge(resolveCategoryIcon(child, byId), size = 32.dp)
                            Text(child.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showNew) {
        NewCategoryDialog(
            parents = parents,
            onDismiss = { showNew = false },
            onCreate = { name, parentId, icon -> scope.launch { vm.createCategory(name, parentId, icon) }; showNew = false }
        )
    }
    editing?.let { cat ->
        EditCategoryDialog(
            category = cat,
            onDismiss = { editing = null },
            onSave = { newName, icon ->
                scope.launch { vm.saveCategory(cat.id, newName, icon) }
                editing = null
            },
            onDelete = { deleting = cat; editing = null }
        )
    }
    deleting?.let { cat ->
        ConfirmDialog(
            text = "Eliminare \"${cat.name}\"? Verranno eliminate anche le sue sotto-categorie; " +
                "le transazioni restano, ma senza categoria.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.deleteCategory(cat.id) }; deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EditCategoryDialog(
    category: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var icon by remember { mutableStateOf(category.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Text("Icona", style = MaterialTheme.typography.labelLarge)
                IconPicker(selected = icon, previewName = name, onSelect = { icon = it })
                TextButton(onClick = onDelete) {
                    Text("Elimina categoria", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), icon) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
