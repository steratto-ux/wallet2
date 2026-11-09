package com.example.finanzecasa.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.GoalEntity
import com.example.finanzecasa.domain.GoalMath
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Un obiettivo con quanto è stato messo da parte finora. */
data class GoalItem(val goal: GoalEntity, val savedCents: Long)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()

    val accounts: StateFlow<List<AccountEntity>> =
        dao.observeAccountsOrdered().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> =
        dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Avanzamento = importo messo da parte a mano + saldo dell'account collegato
     * + movimento netto della categoria collegata dalla creazione dell'obiettivo.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val goals: StateFlow<List<GoalItem>> = minuteClock().flatMapLatest { now ->
        val upTo = now.plusMinutes(1).toMillis()
        combine(dao.observeGoals(), dao.observeBalances(upTo)) { goals, balances -> goals to balances }
            .flatMapLatest { (goals, balances) ->
                if (goals.isEmpty()) {
                    flowOf(emptyList<GoalItem>())
                } else {
                    val balanceOf = balances.associate { it.id to it.balanceCents }
                    val nets = goals.map { g ->
                        val cat = g.categoryId
                        if (cat != null) {
                            dao.observeCategoryNet(cat, LocalDate.ofEpochDay(g.createdEpochDay).atStartOfDay().toMillis(), upTo)
                        } else {
                            flowOf(0L)
                        }
                    }
                    combine(nets) { values ->
                        goals.mapIndexed { i, g ->
                            GoalItem(g, g.manualCents + (g.accountId?.let { balanceOf[it] } ?: 0L) + values[i])
                        }
                    }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun goalById(id: Long) = dao.goalById(id)

    suspend fun save(g: GoalEntity) {
        if (g.id == 0L) dao.insertGoal(g) else dao.updateGoal(g)
    }

    suspend fun delete(id: Long) = dao.deleteGoal(id)

    suspend fun createCategory(name: String, parentId: Long?, icon: String?): Long =
        dao.categoryByName(name, parentId)?.id
            ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId, icon = icon))
}

@Composable
fun GoalsScreen(onBack: () -> Unit, onOpenForm: (Long?) -> Unit, vm: GoalsViewModel = viewModel()) {
    val list by vm.goals.collectAsState()
    val today = LocalDate.now()

    ScreenScaffold(
        title = "Obiettivi", onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenForm(null) }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) item { Text("Nessun obiettivo. Tocca + per crearne uno (una vacanza, un'auto, un fondo emergenze…).") }
            items(list, key = { it.goal.id }) { item ->
                val g = item.goal
                val done = item.savedCents >= g.targetCents
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenForm(g.id) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${GoalMath.percent(item.savedCents, g.targetCents)}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (done) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    ProgressBar(GoalMath.fraction(item.savedCents, g.targetCents), Modifier.padding(top = 8.dp, bottom = 8.dp))
                    Text(
                        "${formatMoney(item.savedCents)} di ${formatMoney(g.targetCents)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val deadline = g.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
                    if (done) {
                        Text("Obiettivo raggiunto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.income)
                    } else if (deadline != null) {
                        val needed = GoalMath.monthlyNeededCents(item.savedCents, g.targetCents, today, deadline)
                        val line = if (needed != null) "Entro ${formatDate(deadline)} · ${formatMoney(needed)} al mese"
                        else "Scadenza ${formatDate(deadline)} superata"
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private enum class GoalLink { MANUAL, ACCOUNT, CATEGORY }

@Composable
fun GoalFormScreen(id: Long?, onClose: () -> Unit, vm: GoalsViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf(GoalLink.MANUAL) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var manualText by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var createdEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            vm.goalById(id)?.let { g ->
                name = g.name
                targetText = centsToInput(g.targetCents)
                link = when {
                    g.accountId != null -> GoalLink.ACCOUNT
                    g.categoryId != null -> GoalLink.CATEGORY
                    else -> GoalLink.MANUAL
                }
                accountId = g.accountId
                categoryId = g.categoryId
                manualText = if (g.manualCents != 0L) centsToInput(g.manualCents) else ""
                deadline = g.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
                createdEpochDay = g.createdEpochDay
            }
            loaded = true
        }
    }
    val effectiveAccountId = accountId?.takeIf { a -> accounts.any { it.id == a } } ?: accounts.firstOrNull()?.id

    ScreenScaffold(if (id == null) "Nuovo obiettivo" else "Modifica obiettivo", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome (es. Vacanze estate)", name) { name = it; error = null }
            MoneyInput("Importo da raggiungere (€)", targetText) { targetText = it; error = null }

            Text("Come si calcola l'avanzamento", style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                options = listOf(GoalLink.MANUAL to "A mano", GoalLink.ACCOUNT to "Account", GoalLink.CATEGORY to "Categoria"),
                selected = link, onSelect = { link = it; error = null }
            )
            when (link) {
                GoalLink.MANUAL -> {
                    MoneyInput("Già messo da parte (€)", manualText, signed = true) { manualText = it; error = null }
                    Text(
                        "Aggiorna questo importo quando versi altri soldi sull'obiettivo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GoalLink.ACCOUNT -> {
                    AccountField("Account del risparmio", accounts, effectiveAccountId) { accountId = it; error = null }
                    Text(
                        "L'avanzamento è il saldo di questo account (ad esempio un conto deposito).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GoalLink.CATEGORY -> {
                    CategoryField(categories, categoryId, onSelect = { categoryId = it; error = null }, onNew = { showNewCategory = true })
                    Text(
                        "L'avanzamento è il totale netto (entrate meno uscite) delle transazioni di questa categoria " +
                            "dalla creazione dell'obiettivo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(deadline?.let { "Scadenza: ${formatDate(it)}" } ?: "Scadenza (facoltativa)")
            }
            if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Togli la scadenza") }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val target = parseAmountToCents(targetText)
                    val manual = if (link == GoalLink.MANUAL && manualText.isNotBlank()) parseSignedCents(manualText) else 0L
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        target == null -> error = "Inserisci l'importo da raggiungere."
                        manual == null -> error = "Importo già messo da parte non valido."
                        link == GoalLink.ACCOUNT && effectiveAccountId == null -> error = "Non ci sono account."
                        link == GoalLink.CATEGORY && categoryId == null -> error = "Scegli una categoria."
                        else -> scope.launch {
                            vm.save(
                                GoalEntity(
                                    id = id ?: 0, name = name.trim(), targetCents = target,
                                    accountId = effectiveAccountId.takeIf { link == GoalLink.ACCOUNT },
                                    categoryId = categoryId.takeIf { link == GoalLink.CATEGORY },
                                    manualCents = manual,
                                    deadlineEpochDay = deadline?.toEpochDay(),
                                    createdEpochDay = createdEpochDay
                                )
                            )
                            onClose()
                        }
                    }
                }
            ) { Text("Salva") }
            if (id != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elimina obiettivo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDate) {
        DatePickerDialogBox(
            initial = deadline ?: LocalDate.now().plusMonths(6),
            onDismiss = { showDate = false },
            onPick = { deadline = it; showDate = false }
        )
    }
    if (showNewCategory) {
        NewCategoryDialog(
            parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() },
            onDismiss = { showNewCategory = false },
            onCreate = { n, p, i -> scope.launch { categoryId = vm.createCategory(n, p, i) }; showNewCategory = false }
        )
    }
    if (confirmDelete && id != null) {
        ConfirmDialog(
            text = "Eliminare l'obiettivo? Le transazioni non vengono toccate.",
            confirmLabel = "Elimina",
            onConfirm = { scope.launch { vm.delete(id); onClose() } },
            onDismiss = { confirmDelete = false }
        )
    }
}
