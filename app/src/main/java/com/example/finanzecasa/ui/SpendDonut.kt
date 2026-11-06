package com.example.finanzecasa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.data.CategorySpend
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val donutColors = listOf(
    Color(0xFF1E9E7C), Color(0xFF3F7BD1), Color(0xFFE0724A), Color(0xFFD9A021), Color(0xFF8A63D2)
)
private val otherColor = Color(0xFF9DB0A8)
private const val MAX_SLICES = 5

/** Una fetta della ciambella: le categorie oltre le prime cinque confluiscono in "Altre". */
internal data class Slice(val name: String, val cents: Long, val color: Color, val categoryId: Long? = null)

internal fun buildSlices(spend: List<CategorySpend>): List<Slice> {
    val sorted = spend.filter { it.totalCents > 0 }.sortedByDescending { it.totalCents }
    val top = sorted.take(MAX_SLICES).mapIndexed { i, s -> Slice(s.name, s.totalCents, donutColors[i], s.categoryId) }
    val rest = sorted.drop(MAX_SLICES).sumOf { it.totalCents }
    return if (rest > 0) top + Slice("Altre", rest, otherColor) else top
}

private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)

/** Spese del mese per categoria principale, come grafico a ciambella con legenda. */
@Composable
fun SpendDonutCard(
    month: YearMonth,
    spend: List<CategorySpend>,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCategoryClick: ((categoryId: Long, name: String) -> Unit)? = null
) {
    val slices = buildSlices(spend)
    val total = slices.sumOf { it.cents }
    val canGoForward = month.isBefore(YearMonth.now())

    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spese per categoria", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mese precedente")
            }
            Text(
                month.format(monthFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge
            )
            IconButton(onClick = { onShiftMonth(1) }, enabled = canGoForward) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Mese successivo")
            }
        }

        if (slices.isEmpty()) {
            Text(
                "Nessuna spesa in questo mese.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            return@AppCard
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Donut(slices, total, Modifier.size(170.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Totale", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMoney(total), style = MaterialTheme.typography.titleLarge)
            }
        }

        slices.forEach { s ->
            val catId = s.categoryId
            val rowModifier = if (onCategoryClick != null && catId != null) {
                Modifier.fillMaxWidth().clickable { onCategoryClick(catId, s.name) }
            } else {
                Modifier.fillMaxWidth()
            }
            Row(
                rowModifier.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                Text(
                    s.name, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(formatMoney(s.cents), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${(s.cents * 100.0 / total).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        if (onCategoryClick != null) {
            Text(
                "Tocca una categoria per vedere le sotto-categorie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Donut(slices: List<Slice>, total: Long, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier) {
        val stroke = 26.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        // Piccolo spazio tra le fette, se ce n'è più di una
        val gap = if (slices.size > 1) 2.5f else 0f
        var start = -90f
        for (s in slices) {
            val sweep = 360f * s.cents / total
            drawArc(
                s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt)
            )
            start += sweep
        }
    }
}
