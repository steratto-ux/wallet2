package com.example.finanzecasa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.BudgetEntity
import com.example.finanzecasa.domain.BudgetHistory
import com.example.finanzecasa.domain.MonthSpend
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val longMonth = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
private val shortMonth = DateTimeFormatter.ofPattern("LLL", Locale.ITALIAN)

/** Storico di un budget: quanto si è speso ogni mese negli ultimi 12 mesi rispetto all'importo mensile. */
@Composable
fun BudgetHistoryScreen(id: Long, onBack: () -> Unit, vm: ManageViewModel = viewModel()) {
    var budget by remember { mutableStateOf<BudgetEntity?>(null) }
    LaunchedEffect(id) { budget = vm.budgetById(id) }
    val history by remember(id) { vm.budgetHistory(id) }.collectAsState(initial = emptyList())
    val b = budget

    ScreenScaffold(b?.name ?: "Storico budget", onBack = onBack) { inner ->
        FormColumn(inner) {
            if (b == null || history.isEmpty()) {
                Text("Caricamento…")
                return@FormColumn
            }
            val current = YearMonth.now()
            val average = BudgetHistory.averageClosed(history, current)
            val overCount = history.count { it.spentCents > b.amountCents }

            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Budget mensile", formatMoneyShort(b.amountCents))
                    Stat("Media mesi chiusi", average?.let { formatMoney(it) } ?: "—")
                    Stat("Mesi superati", overCount.toString())
                }
                HistoryBars(history, b.amountCents, Modifier.fillMaxWidth().height(150.dp).padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    history.forEach { m ->
                        Text(
                            m.month.format(shortMonth).take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                "Il confronto usa l'importo attuale del budget per tutti i mesi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppCard(Modifier.fillMaxWidth()) {
                history.reversed().forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MonthRow(m, b.amountCents)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MonthRow(m: MonthSpend, amountCents: Long) {
    val over = m.spentCents > amountCents
    val percent = if (amountCents > 0) (m.spentCents * 100.0 / amountCents).roundToInt() else 0
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.month.format(longMonth).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)
            )
            Text(
                "${formatMoney(m.spentCents)} · $percent%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(
                Modifier.fillMaxWidth((m.spentCents.toFloat() / amountCents.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .fillMaxHeight().clip(RoundedCornerShape(50)).background(barColor)
            )
        }
    }
}

/** Una colonna per mese; la linea tratteggiata è l'importo del budget, le colonne oltre la linea sono in rosso. */
@Composable
private fun HistoryBars(history: List<MonthSpend>, amountCents: Long, modifier: Modifier) {
    val ok = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.surfaceContainer
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val max = maxOf(amountCents, history.maxOfOrNull { it.spentCents } ?: 0L).coerceAtLeast(1L) * 1.1f
        val slot = size.width / history.size
        val barWidth = slot * 0.6f
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        history.forEachIndexed { i, m ->
            val h = size.height * (m.spentCents / max)
            val x = i * slot + (slot - barWidth) / 2
            // Colonna "vuota" di fondo, poi quella del mese
            drawRoundRect(empty, Offset(x, 0f), Size(barWidth, size.height), radius)
            if (h > 0f) {
                drawRoundRect(
                    if (m.spentCents > amountCents) bad else ok,
                    Offset(x, size.height - h), Size(barWidth, h), radius
                )
            }
        }
        val y = size.height * (1f - amountCents / max)
        drawLine(
            line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
    }
}
