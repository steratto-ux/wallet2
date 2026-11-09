package com.example.finanzecasa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.domain.BalancePoint
import com.example.finanzecasa.domain.ChartRange
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Riquadro del patrimonio netto con il grafico dell'andamento del saldo.
 * Il patrimonio netto coincide con il saldo totale di tutti gli account, quindi il grafico ne è la storia.
 * Trascinando il dito sul grafico (o toccandolo) si legge il saldo di un giorno preciso.
 */
@Composable
fun NetWorthCard(
    netWorthCents: Long,
    points: List<BalancePoint>,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    debtCents: Long = 0L // debiti dei prestiti già sottratti da netWorthCents (0 = non inclusi)
) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val point = selected?.let { points.getOrNull(it) }
    val shown = point?.let { it.cents - debtCents } ?: netWorthCents
    val delta = if (points.size >= 2) points.last().cents - points.first().cents else 0L

    AppCard(modifier.fillMaxWidth()) {
        Text(
            "Patrimonio netto",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatMoney(shown),
            style = BigNumberStyle,
            color = if (shown < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (point != null) {
            Text(
                formatDate(point.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (if (delta > 0) "+" else "") + formatMoney(delta),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.income
                )
                Text(
                    range.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (debtCents > 0) {
            Text(
                "Include i debiti dei prestiti: −${formatMoney(debtCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BalanceChart(
            points = points,
            selected = selected,
            onSelect = { selected = it },
            lineColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)
        )
        if (points.size >= 2) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(points.first().date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(points.last().date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RangeSelector(range, onRangeChange, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RangeSelector(range: ChartRange, onChange: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ChartRange.entries.forEach { r ->
            val isSelected = r == range
            Box(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .clickable { onChange(r) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    r.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Linea con area sfumata. Nessun asse: contano l'andamento e il valore del giorno selezionato. */
@Composable
fun BalanceChart(
    points: List<BalancePoint>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotFill = MaterialTheme.colorScheme.surfaceContainerLowest

    fun indexFor(x: Float, width: Int): Int =
        ((x / width) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)

    val gestures = if (points.size < 2) Modifier else Modifier
        .pointerInput(points) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> onSelect(indexFor(offset.x, size.width)) },
                onDragEnd = { onSelect(null) },
                onDragCancel = { onSelect(null) }
            ) { change, _ ->
                change.consume()
                onSelect(indexFor(change.position.x, size.width))
            }
        }
        .pointerInput(points, selected) {
            detectTapGestures(onTap = { offset ->
                onSelect(if (selected == null) indexFor(offset.x, size.width) else null)
            })
        }

    Canvas(modifier.then(gestures)) {
        val n = points.size
        val w = size.width
        val h = size.height
        // Linea di riferimento in basso
        drawLine(gridColor, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1.dp.toPx())
        if (n < 2) return@Canvas

        val min = points.minOf { it.cents }
        val max = points.maxOf { it.cents }
        val top = 8.dp.toPx()
        val bottom = 8.dp.toPx()
        fun xAt(i: Int) = w * i / (n - 1)
        fun yAt(cents: Long): Float {
            val frac = if (max == min) 0.5f else (cents - min).toFloat() / (max - min).toFloat()
            return top + (h - top - bottom) * (1f - frac)
        }

        val line = Path().apply {
            moveTo(xAt(0), yAt(points[0].cents))
            for (i in 1 until n) lineTo(xAt(i), yAt(points[i].cents))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
                startY = 0f, endY = h
            )
        )
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        selected?.takeIf { it in 0 until n }?.let { i ->
            val x = xAt(i)
            val y = yAt(points[i].cents)
            drawLine(lineColor.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawCircle(dotFill, radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}
