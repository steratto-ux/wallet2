package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.AccountBalance
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.TransactionRow
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.IconRules
import com.example.finanzecasa.domain.Tags
import com.example.finanzecasa.domain.toMillis
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onNewAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onOpen: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenBudget: (Long) -> Unit,
    onOpenBudgetHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var drill by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Outlined.Add, contentDescription = "Nuova transazione") }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Greeting(Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurazione")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavFullButton(Icons.Outlined.CalendarMonth, "CALENDARIO", { onOpen("calendar") }, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavIconButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Categorie", { onOpen("categories") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.Speed, "Budget", { onOpen("budgets") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.Autorenew, "Abbonamenti", { onOpen("subscriptions") }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavIconButton(Icons.Outlined.AccountBalance, "Prestiti", { onOpen("loans") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.EventRepeat, "Ricorrenti", { onOpen("recurring") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.Savings, "Spese irregolari", { onOpen("irregular") }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavIconButton(Icons.Outlined.Flag, "Obiettivi", { onOpen("goals") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.BarChart, "Report", { onOpen("reports") }, Modifier.weight(1f))
                        NavIconButton(Icons.Outlined.Receipt, "Transazioni", { onOpen("transactions") }, Modifier.weight(1f))
                    }
                    NavWideButton(Icons.Outlined.Insights, "ANALISI ECONOMICA", { onOpen("analysis") }, Modifier.fillMaxWidth())
                }
            }

            // 1. Account in una riga che scorre in orizzontale
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.accounts.filter { !it.hidden }, key = { it.id }) { a ->
                        AccountBox(a, Modifier.width(158.dp).height(112.dp), onClick = { onEditAccount(a.id) })
                    }
                    item {
                        AppCard(Modifier.width(110.dp).height(112.dp), onClick = onNewAccount, padding = 14.dp) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(10.dp))
                            Text("Nuovo account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 2. Spese ed entrate del mese in corso
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryBox("Entrate del mese", formatMoney(state.monthIncomeCents), MaterialTheme.colorScheme.income, Modifier.weight(1f).fillMaxHeight())
                    SummaryBox("Spese del mese", formatMoney(state.monthExpensesCents), MaterialTheme.colorScheme.error, Modifier.weight(1f).fillMaxHeight())
                }
            }

            // 3. Budget mensili
            if (state.budgets.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOpen("budgets") }) { Text("Gestisci") }
                    }
                }
                item {
                    // Come gli account: schede affiancate che scorrono in orizzontale
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.budgets, key = { "budget-${it.id}" }) { b ->
                            BudgetCard(
                                b, state.now, Modifier.width(300.dp),
                                onHistory = { onOpenBudgetHistory(b.id) }, onClick = { onOpenBudget(b.id) }
                            )
                        }
                    }
                }
            }

            // 4. Spese per categoria (ciambella)
            item {
                SpendDonutCard(
                    month = state.spendMonth,
                    spend = state.categorySpend,
                    onShiftMonth = vm::shiftSpendMonth,
                    onCategoryClick = { id, name -> drill = id to name }
                )
            }

            // 5. Patrimonio netto con andamento del saldo
            item {
                NetWorthCard(
                    netWorthCents = state.netWorthCents,
                    points = state.chart,
                    range = state.range,
                    onRangeChange = vm::selectRange,
                    debtCents = state.loanDebtCents
                )
            }

            // 6. Ultime transazioni
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ultime transazioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOpen("transactions") }) { Text("Vedi tutte") }
                    }
                    if (state.recent.isEmpty()) {
                        Text("Nessuna transazione", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    state.recent.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TransactionItem(t) { onOpenTransaction(t.id) }
                    }
                }
            }
        }
    }

    drill?.let { (id, name) ->
        val month: YearMonth = state.spendMonth
        val from = month.atDay(1).atStartOfDay().toMillis()
        val to = minOf(month.plusMonths(1).atDay(1).atStartOfDay().toMillis(), System.currentTimeMillis() + 60_000)
        SubcategoryDialog(id, name, income = false, fromMillis = from, toMillis = to, onDismiss = { drill = null })
    }
}

@Composable
private fun AccountBox(a: AccountBalance, modifier: Modifier, onClick: () -> Unit) {
    val tint = accountTint(a.colorKey, a.type, LocalDarkTheme.current)
    val icon = accountIcon(a.iconKey, a.type)
    AppCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            a.name, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            formatMoney(a.balanceCents),
            style = MaterialTheme.typography.titleMedium,
            color = if (a.balanceCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryBox(label: String, value: String, valueColor: Color, modifier: Modifier) {
    AppCard(modifier, padding = 14.dp) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

@Composable
fun TransactionItem(t: TransactionRow, onClick: (() -> Unit)? = null) {
    val isTransfer = t.kind == TxKind.TRANSFER
    val category = listOfNotNull(t.parentCategoryName, t.categoryName).joinToString(" › ")
    val title = t.title.ifBlank {
        category.ifBlank { if (isTransfer) "Trasferimento" else "Senza titolo" }
    }
    val tagText = Tags.split(t.tags).joinToString(" ") { "#$it" }
    val where = if (isTransfer) "${t.accountName} → ${t.counterpartName ?: "?"}"
    else listOf(t.accountName, category, tagText, if (t.receiptPath != null) "scontrino" else "")
        .filter { it.isNotBlank() }.joinToString(" · ")

    val color = when {
        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        t.amountCents < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.income
    }
    val amount = when {
        isTransfer -> formatMoney(abs(t.amountCents))
        t.amountCents > 0 -> "+" + formatMoney(t.amountCents)
        else -> formatMoney(t.amountCents)
    }
    val iconKey = if (isTransfer) "transfer"
    else IconRules.resolve(t.categoryIcon, t.categoryName, t.parentCategoryIcon, t.parentCategoryName)
    val scheduled = if (t.dateMillis > System.currentTimeMillis()) " · programmata" else ""
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()

    Row(
        rowModifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(iconKey, size = 40.dp)
        Column(Modifier.weight(1f)) {
            // Titolo a sinistra e data a destra: il titolo si accorcia, la data resta sempre visibile
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(
                    formatDay(t.dateMillis), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false
                )
            }
            Text(
                where + scheduled,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Saluto in alto nella schermata principale, secondo l'ora del giorno e con il nome scelto nelle impostazioni. */
@Composable
private fun Greeting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val name by settings.userNameFlow().collectAsState(initial = settings.userName())
    val now by minuteClock().collectAsState(initial = java.time.LocalDateTime.now())
    val text = greetingText(now.hour, name)
    // Il saluto parte con il carattere grande e si rimpicciolisce finché sta tutto su una riga
    var sizeSp by remember(text) { mutableStateOf(24f) }
    Text(
        text, style = MaterialTheme.typography.headlineSmall, fontSize = sizeSp.sp, modifier = modifier,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.didOverflowWidth && sizeSp > 12f) sizeSp = maxOf(12f, sizeSp * 0.92f) }
    )
}

/** "Buongiorno Stefano!", "Buon pomeriggio Stefano!"... */
fun greetingText(hour: Int, name: String): String {
    val hello = when (hour) {
        in 5..11 -> "Buongiorno"
        in 12..17 -> "Buon pomeriggio"
        in 18..21 -> "Buonasera"
        else -> "Ciao"
    }
    val who = name.trim()
    return if (who.isEmpty()) "$hello!" else "$hello $who!"
}
