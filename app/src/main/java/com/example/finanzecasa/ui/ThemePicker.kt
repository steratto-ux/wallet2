package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.domain.ThemePalettes
import com.example.finanzecasa.domain.ThemeStyle

/**
 * Elenco dei temi di colore. Ogni riga è disegnata con i colori, i bordi e gli angoli del proprio tema
 * (nella modalità chiara o scura in uso), così l'anteprima è fedele; il tema scelto ha il bordo spesso.
 */
@Composable
fun ThemePicker(selected: ThemeStyle, onSelect: (ThemeStyle) -> Unit) {
    val dark = LocalDarkTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ThemePalettes.all.forEach { spec ->
            val c = spec.colors(dark)
            val isSelected = spec.style == selected
            val shape = RoundedCornerShape(maxOf(4, spec.radiusDp).dp)
            Row(
                Modifier.fillMaxWidth()
                    .clip(shape)
                    .background(Color(c.background), shape)
                    .border(
                        if (isSelected) 3.dp else spec.cardBorderDp.dp,
                        if (isSelected) Color(c.primary) else Color(c.cardBorder),
                        shape
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(spec.style) })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        spec.style.label, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Color(c.onBackground)
                    )
                    Text(
                        spec.style.description, style = MaterialTheme.typography.bodySmall,
                        color = Color(c.onSurfaceVariant)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Swatch(Color(c.primary), Color(c.onBackground))
                    Swatch(Color(c.primaryContainer), Color(c.navBorder))
                    Swatch(Color(c.accent), Color(c.onBackground))
                    Swatch(Color(c.error), Color(c.onBackground))
                }
                if (isSelected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Tema in uso", tint = Color(c.primary), modifier = Modifier.size(24.dp))
                } else {
                    Box(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Swatch(fill: Color, edge: Color) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(fill, CircleShape)
            .border(1.5.dp, edge.copy(alpha = 0.55f), CircleShape)
    )
}
