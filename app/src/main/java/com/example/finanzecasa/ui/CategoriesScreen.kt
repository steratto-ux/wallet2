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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.domain.Bucket
import com.example.finanzecasa.domain.BucketRules
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: ManageViewModel = viewModel()) {
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
            parents = parents.filter { it.id != cat.id },
            hasChildren = childrenOf[cat.id].orEmpty().isNotEmpty(),
            onDismiss = { editing = null },
            onSave = { newName, icon, bucket, parentId ->
                scope.launch {
                    val message = vm.saveCategory(cat.id, newName, icon, bucket, parentId)
                    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
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
    parents: List<CategoryEntity>,
    hasChildren: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String?, bucket: Int?, parentId: Long?) -> Unit,
    onDelete: () -> Unit
) {
    var parentId by remember { mutableStateOf(category.parentId) }
    var name by remember { mutableStateOf(category.name) }
    var icon by remember { mutableStateOf(category.icon) }
    var bucket by remember { mutableStateOf(category.budgetBucket) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                // Spostamento in un altro gruppo. Con delle sotto-categorie la categoria resta principale (si va su due livelli soli).
                val none = "Nessuna (categoria principale)"
                if (hasChildren) {
                    Text(
                        "Ha delle sotto-categorie, quindi resta una categoria principale.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DropdownField(
                        label = "Sotto-categoria di",
                        value = parents.firstOrNull { it.id == parentId }?.name ?: none,
                        entries = listOf(none to { parentId = null }) + parents.map { p -> p.name to { parentId = p.id } }
                    )
                }
                Text("Icona", style = MaterialTheme.typography.labelLarge)
                IconPicker(selected = icon, previewName = name, onSelect = { icon = it })
                val isSub = parentId != null
                Text("Nella regola 50/30/20", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(
                    options = listOf(
                        BucketRules.AUTO to (if (isSub) "Come principale" else "Auto"),
                        BucketRules.NEED to "Necessità",
                        BucketRules.WANT to "Desiderio"
                    ),
                    selected = bucket, onSelect = { bucket = it }
                )
                Text(
                    when {
                        bucket != BucketRules.AUTO -> "Scelta manuale."
                        isSub -> "Segue la categoria principale."
                        else -> "Automatico: " + (if (BucketRules.auto(name) == Bucket.NEED) "necessità" else "desiderio") + " (dal nome)."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDelete) {
                    Text("Elimina categoria", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), icon, bucket, parentId) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
