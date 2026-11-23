package com.example.finanzecasa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finanzecasa.domain.CashflowPoint
import com.example.finanzecasa.domain.CashflowSeries
import com.example.finanzecasa.domain.CashflowSpan
import com.example.finanzecasa.domain.CashflowView
import java.time.YearMonth

private val ChartHeight = 200.dp
private val ChartTop = 16.dp

/**
 * Andamento di entrate e uscite mese per mese. Si sceglie la durata (3, 6, 12 mesi o anno solare), con le frecce
 * si va indietro negli anni, e un tasto passa dalle linee (continue) alle barre. Toccando o trascinando sul grafico
 * si leggono i valori di un mese preciso.
 */
@Composable
fun CashflowChartCard(
    view: CashflowView,
    points: List<CashflowPoint>,
    canGoForward: Boolean,
    onSpanChange: (CashflowSpan) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var bars by rememberSaveable { mutableStateOf(false) }
    var selected by remember(view) { mutableStateOf<Int?>(null) }
    val point = selected?.let { points.getOrNull(it) }

    val incomeColor = MaterialTheme.colorScheme.income
    val expenseColor = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val (totalIncome, totalExpense, totalNet) = CashflowSeries.totals(points)
    val income = point?.incomeCents ?: totalIncome
    val expense = point?.expenseCents ?: totalExpense
    val net = point?.netCents ?: totalNet
    val current = YearMonth.now()
    val partial = point == null && points.any { it.month == current }

    AppCard(modifier.fillMaxWidth()) {
        Text("Andamento entrate e uscite", style = MaterialTheme.typography.titleMedium)
        Spacer8()
        SegmentedChoice(
            options = CashflowSpan.entries.map { it to it.label },
            selected = view.span, onSelect = onSpanChange
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Periodo precedente") }
            Text(
                view.label(), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            IconButton(onClick = { onShift(1) }, enabled = canGoForward) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Periodo successivo")
            }
        }
        SegmentedChoice(options = listOf(false to "Linee", true to "Barre"), selected = bars, onSelect = { bars = it })

        // Totali del periodo, oppure i valori del mese toccato
        Text(
            if (point != null) CashflowSeries.longMonth(point.month).replaceFirstChar { it.uppercase() } else "Totale del periodo",
            style = MaterialTheme.typography.labelMedium, color = muted,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Entrate", style = MaterialTheme.typography.labelMedium, color = muted)
                Text(formatMoney(income), style = MaterialTheme.typography.titleMedium, color = incomeColor)
            }
            Column {
                Text("Uscite", style = MaterialTheme.typography.labelMedium, color = muted)
                Text(formatMoney(expense), style = MaterialTheme.typography.titleMedium, color = expenseColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Saldo", style = MaterialTheme.typography.labelMedium, color = muted)
                Text(
                    (if (net > 0) "+" else "") + formatMoney(net), style = MaterialTheme.typography.titleMedium,
                    color = if (net < 0) expenseColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

        val peak = CashflowSeries.peak(points)
        val scale = CashflowSeries.niceMax(peak)
        Box(Modifier.fillMaxWidth().height(ChartHeight)) {
            CashflowCanvas(
                points = points, scale = scale, bars = bars, selected = selected,
                onSelect = { i -> selected = i },
                incomeColor = incomeColor, expenseColor = expenseColor,
                modifier = Modifier.fillMaxWidth().height(ChartHeight)
            )
            if (scale > 0) {
                Text(compactMoney(scale), style = MaterialTheme.typography.labelSmall, color = muted, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopStart).offset(y = 1.dp))
                Text(compactMoney(scale / 2), style = MaterialTheme.typography.labelSmall, color = muted, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopStart).offset(y = (ChartTop + ChartHeight) / 2 - 14.dp))
            } else {
                Text(
                    "Nessun movimento nel periodo", style = MaterialTheme.typography.bodyMedium, color = muted,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEachIndexed { i, p ->
                Text(
                    CashflowSeries.shortMonth(p.month),
                    style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                    color = if (p.future) muted.copy(alpha = 0.5f) else muted,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(incomeColor, "Entrate")
            LegendDot(expenseColor, "Uscite")
        }
        if (partial) {
            Text(
                "Il mese in corso è ancora parziale.",
                style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun Spacer8() {
    Box(Modifier.height(8.dp))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Il disegno: due linee morbide e continue (entrate e uscite) oppure coppie di barre, una colonna per mese.
 * Linee e barre usano gli stessi centri di colonna, così le etichette dei mesi restano allineate in entrambe le modalità.
 */
@Composable
private fun CashflowCanvas(
    points: List<CashflowPoint>,
    scale: Long,
    bars: Boolean,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val dotFill = MaterialTheme.colorScheme.surfaceContainerLowest

    fun pick(x: Float, width: Float): Int? {
        val i = CashflowSeries.slotIndex(x, width, points.size)
        return if (points.getOrNull(i)?.future == false) i else null
    }

    val gestures = if (points.isEmpty() || scale <= 0L) Modifier else Modifier
        .pointerInput(points) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> pick(offset.x, size.width.toFloat())?.let { onSelect(it) } }
            ) { change, _ ->
                change.consume()
                pick(change.position.x, size.width.toFloat())?.let { onSelect(it) }
            }
        }
        .pointerInput(points, selected) {
            detectTapGestures(onTap = { offset ->
                val i = pick(offset.x, size.width.toFloat())
                onSelect(if (i == null || i == selected) null else i)
            })
        }

    Canvas(modifier.then(gestures)) {
        val n = points.size
        val w = size.width
        val h = size.height
        val top = ChartTop.toPx()
        val baseline = h - 1f
        val strokeThin = 1.dp.toPx()

        // Linee di riferimento: zero, metà e fondo scala
        drawLine(gridColor, Offset(0f, baseline), Offset(w, baseline), strokeWidth = strokeThin)
        drawLine(gridColor.copy(alpha = 0.6f), Offset(0f, (top + baseline) / 2f), Offset(w, (top + baseline) / 2f), strokeWidth = strokeThin)
        drawLine(gridColor.copy(alpha = 0.6f), Offset(0f, top), Offset(w, top), strokeWidth = strokeThin)
        if (n == 0 || scale <= 0L) return@Canvas

        val slot = w / n
        fun xc(i: Int) = slot * (i + 0.5f)
        fun yAt(cents: Long): Float = baseline - (baseline - top) * (cents.toFloat() / scale.toFloat()).coerceIn(0f, 1f)

        selected?.takeIf { it in 0 until n }?.let { i ->
            drawRoundRect(highlight, Offset(slot * i, top - 4.dp.toPx()), Size(slot, baseline - top + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
        }

        if (bars) {
            val gap = 2.dp.toPx()
            val barW = minOf(slot * 0.36f, 16.dp.toPx())
            val radius = CornerRadius(minOf(3.dp.toPx(), barW / 2f))
            for (i in 0 until n) {
                val p = points[i]
                if (p.future) continue
                fun bar(cents: Long, left: Float, color: Color) {
                    if (cents <= 0L) return
                    val y = minOf(yAt(cents), baseline - 2.dp.toPx())
                    drawRoundRect(color, Offset(left, y), Size(barW, baseline - y), radius)
                }
                bar(p.incomeCents, xc(i) - barW - gap / 2f, incomeColor)
                bar(p.expenseCents, xc(i) + gap / 2f, expenseColor)
            }
        } else {
            fun series(valueOf: (CashflowPoint) -> Long, color: Color) {
                val pts = (0 until n).filter { !points[it].future }.map { Offset(xc(it), yAt(valueOf(points[it]))) }
                if (pts.isEmpty()) return
                if (pts.size >= 2) {
                    // Curva continua: tangenti orizzontali nei punti, quindi mai oltre il minimo e il massimo dei mesi
                    val line = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (k in 1 until pts.size) {
                            val a = pts[k - 1]
                            val b = pts[k]
                            val mx = (a.x + b.x) / 2f
                            cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                        }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(pts.last().x, baseline)
                        lineTo(pts.first().x, baseline)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0f)), startY = top, endY = baseline))
                    drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                pts.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
            }
            series({ it.expenseCents }, expenseColor)
            series({ it.incomeCents }, incomeColor)

            selected?.takeIf { it in 0 until n && !points[it].future }?.let { i ->
                for ((cents, color) in listOf(points[i].expenseCents to expenseColor, points[i].incomeCents to incomeColor)) {
                    val c = Offset(xc(i), yAt(cents))
                    drawCircle(dotFill, radius = 6.dp.toPx(), center = c)
                    drawCircle(color, radius = 4.5.dp.toPx(), center = c)
                }
            }
        }
    }
}
