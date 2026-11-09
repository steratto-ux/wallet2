package com.example.finanzecasa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.data.AccountType

/** Colori tra cui scegliere per un account: chiave salvata → (chiaro, scuro). */
object AccountColors {
    val palette: Map<String, Pair<Color, Color>> = linkedMapOf(
        "blue" to (Color(0xFF3F7BD1) to Color(0xFF8DB6F2)),
        "teal" to (Color(0xFF14A3B8) to Color(0xFF6FD3E3)),
        "green" to (Color(0xFF1E9E6A) to Color(0xFF5FD39B)),
        "lime" to (Color(0xFF6FA83A) to Color(0xFFB2DB82)),
        "amber" to (Color(0xFFD9A021) to Color(0xFFF2CF76)),
        "orange" to (Color(0xFFE0892B) to Color(0xFFF2B766)),
        "red" to (Color(0xFFD9566E) to Color(0xFFF2929F)),
        "purple" to (Color(0xFF8A63D2) to Color(0xFFBEA2F0)),
        "slate" to (Color(0xFF64748B) to Color(0xFFA5B1C2))
    )
}

/** Colore di un account: quello scelto, altrimenti quello del tipo. */
fun accountTint(colorKey: String?, type: AccountType, dark: Boolean): Color =
    AccountColors.palette[colorKey]?.let { if (dark) it.second else it.first } ?: accountTint(type, dark)

/** Icona di un account: quella scelta, altrimenti quella del tipo. */
fun accountIcon(iconKey: String?, type: AccountType): ImageVector {
    if (iconKey != null && CategoryIcons.catalog.containsKey(iconKey)) return CategoryIcons.get(iconKey)
    return when (type) {
        AccountType.BANK -> Icons.Outlined.AccountBalance
        AccountType.CASH -> Icons.Outlined.AccountBalanceWallet
        AccountType.CREDIT_CARD -> Icons.Outlined.CreditCard
    }
}

/** Scelta di colore e icona dell'account. null = automatico (in base al tipo). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountStylePicker(
    type: AccountType,
    colorKey: String?, onColor: (String?) -> Unit,
    iconKey: String?, onIcon: (String?) -> Unit
) {
    val dark = LocalDarkTheme.current
    Text("Colore", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = colorKey == null, onClick = { onColor(null) }, label = { Text("Automatico") })
        AccountColors.palette.forEach { (key, _) ->
            val color = accountTint(key, type, dark)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color)
                    .then(if (key == colorKey) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                    .clickable { onColor(key) },
                contentAlignment = Alignment.Center
            ) {
                if (key == colorKey) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
    Text("Icona", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = iconKey == null, onClick = { onIcon(null) }, label = { Text("Automatica") },
            leadingIcon = { Icon(accountIcon(null, type), contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        CategoryIcons.selectable.forEach { key ->
            val selected = key == iconKey
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onIcon(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    CategoryIcons.get(key), contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
