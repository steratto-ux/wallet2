package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.domain.BudgetMath
import com.example.finanzecasa.domain.BudgetState
import com.example.finanzecasa.domain.BudgetStatus
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Colori spenti del riquadro: salvia se si è in linea, rosato se si spende più in fretta del mese, rosa scuro se superato. */
private class BudgetPalette(val header: Color, val fill: Color, val track: Color)

@Composable
private fun paletteFor(state: BudgetState): BudgetPalette {
    val dark = isSystemInDarkTheme()
    return when (state) {
        BudgetState.ON_TRACK ->
            if (dark) BudgetPalette(Color(0xFF27322D), Color(0xFF4A6357), Color(0xFF222925))
            else BudgetPalette(Color(0xFFE4ECE6), Color(0xFFC3D6C9), Color(0xFFEEF2EE))
        BudgetState.AHEAD ->
            if (dark) BudgetPalette(Color(0xFF3A2A28), Color(0xFF6E4C47), Color(0xFF2C2321))
            else BudgetPalette(Color(0xFFF4E3E0), Color(0xFFE3BFB9), Color(0xFFF6ECEA))
        BudgetState.OVER ->
            if (dark) BudgetPalette(Color(0xFF4A2C27), Color(0xFF8A5249), Color(0xFF33231F))
            else BudgetPalette(Color(0xFFEBCBC5), Color(0xFFD79C94), Color(0xFFF2DEDA))
    }
}

private val TooltipHeight = 22.dp
private val BarHeight = 28.dp
private val BarGap = 4.dp

@Composable
fun BudgetCard(data: BudgetCardData, now: LocalDateTime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val status = BudgetMath.status(data.amountCents, data.spentCents, now)
    val palette = paletteFor(status.state)
    val monthStart = now.toLocalDate().withDayOfMonth(1)
    val monthEnd = now.toLocalDate().withDayOfMonth(now.toLocalDate().lengthOfMonth())

    AppCard(modifier.fillMaxWidth(), onClick = onClick, padding = 0.dp) {
        // Intestazione colorata: icona, nome e quanto speso
        Row(
            Modifier.fillMaxWidth().background(palette.header).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryBadge(data.iconKey, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    data.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatMoney(data.spentCents)} speso di ${formatMoneyShort(data.amountCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDayMonth(monthStart), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDayMonth(monthEnd), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            ProgressWithToday(status, palette)
            Text(
                footerText(status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun footerText(s: BudgetStatus): String = when {
    s.state == BudgetState.OVER -> "Superato di ${formatMoney(-s.remainingCents)}"
    s.remainingCents <= 0L -> "Budget esaurito"
    else -> "Puoi spendere ${formatMoney(s.perDayCents)}/giorno per ${s.daysLeft} " + if (s.daysLeft == 1) "giorno" else "giorni"
}

/** Barra del budget con la percentuale al centro e l'indicatore "Oggi" che avanza da sinistra a destra. */
@Composable
private fun ProgressWithToday(status: BudgetStatus, palette: BudgetPalette) {
    val tooltipBg = MaterialTheme.colorScheme.onSurface
    val tooltipText = MaterialTheme.colorScheme.surface
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    Box(Modifier.fillMaxWidth().height(TooltipHeight + BarGap + BarHeight)) {
        // Barra
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(BarHeight)
                .clip(RoundedCornerShape(50)).background(palette.track)
        ) {
            Box(
                Modifier.fillMaxWidth(status.fillFraction).fillMaxHeight()
                    .clip(RoundedCornerShape(50)).background(palette.fill)
            )
            Text(
                "${status.percent}%",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // Linea verticale dell'indicatore
        Box(
            Modifier.align(Alignment.BottomStart)
                .positionAtFraction(status.todayFraction)
                .width(2.dp).height(BarGap + BarHeight + TooltipHeight / 2)
                .background(markerColor)
        )
        // Etichetta "Oggi"
        Box(
            Modifier.align(Alignment.TopStart)
                .positionAtFraction(status.todayFraction)
                .height(TooltipHeight)
                .clip(RoundedCornerShape(6.dp)).background(tooltipBg)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Oggi", style = MaterialTheme.typography.labelMedium, color = tooltipText, maxLines = 1)
        }
    }
}

/** Sposta il contenuto in orizzontale a [fraction] della larghezza disponibile, centrandolo e tenendolo dentro i bordi. */
private fun Modifier.positionAtFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
    val x = (constraints.maxWidth * fraction - placeable.width / 2f).roundToInt().coerceIn(0, maxX)
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(x, 0) }
}
