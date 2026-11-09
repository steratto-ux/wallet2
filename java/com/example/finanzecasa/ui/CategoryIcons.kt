package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Work
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.domain.IconRules

/** Catalogo delle icone disponibili per le categorie (chiave salvata nel database → icona). */
object CategoryIcons {
    val catalog: Map<String, ImageVector> by lazy {
        linkedMapOf(
            "grocery" to Icons.Outlined.ShoppingCart,
            "restaurant" to Icons.Outlined.Restaurant,
            "coffee" to Icons.Outlined.LocalCafe,
            "home" to Icons.Outlined.Home,
            "gas" to Icons.Outlined.LocalGasStation,
            "bolt" to Icons.Outlined.Bolt,
            "water" to Icons.Outlined.WaterDrop,
            "shield" to Icons.Outlined.Shield,
            "car" to Icons.Outlined.DirectionsCar,
            "bus" to Icons.Outlined.DirectionsBus,
            "medical" to Icons.Outlined.MedicalServices,
            "fitness" to Icons.Outlined.FitnessCenter,
            "school" to Icons.Outlined.School,
            "childcare" to Icons.Outlined.ChildCare,
            "pets" to Icons.Outlined.Pets,
            "movie" to Icons.Outlined.Movie,
            "music" to Icons.Outlined.MusicNote,
            "games" to Icons.Outlined.SportsEsports,
            "subscriptions" to Icons.Outlined.Subscriptions,
            "phone" to Icons.Outlined.Smartphone,
            "clothes" to Icons.Outlined.Checkroom,
            "beauty" to Icons.Outlined.ContentCut,
            "beach" to Icons.Outlined.BeachAccess,
            "travel" to Icons.Outlined.Flight,
            "gift" to Icons.Outlined.CardGiftcard,
            "work" to Icons.Outlined.Work,
            "savings" to Icons.Outlined.Savings,
            "invest" to Icons.Outlined.TrendingUp,
            "receipt" to Icons.Outlined.Receipt,
            "bank" to Icons.Outlined.AccountBalance,
            "shopping" to Icons.Outlined.ShoppingBag,
            "computer" to Icons.Outlined.Computer,
            "money" to Icons.Outlined.Paid,
            "category" to Icons.Outlined.Category,
            "transfer" to Icons.Outlined.SwapHoriz
        )
    }

    /** Icone che l'utente può scegliere (tutte tranne quella riservata ai trasferimenti). */
    val selectable: List<String> by lazy { catalog.keys.filter { it != "transfer" } }

    fun get(key: String): ImageVector = catalog[key] ?: catalog.getValue(IconRules.DEFAULT)
}

/** Chiave dell'icona da mostrare per una categoria (scelta, dedotta dal nome o ereditata dalla principale). */
fun resolveCategoryIcon(c: CategoryEntity?, byId: Map<Long, CategoryEntity>): String {
    if (c == null) return IconRules.DEFAULT
    val parent = c.parentId?.let { byId[it] }
    return IconRules.resolve(c.icon, c.name, parent?.icon, parent?.name)
}

private val badgeAccents = listOf(
    Color(0xFF1E9E7C), Color(0xFF3F7BD1), Color(0xFFE0724A), Color(0xFFD9A021),
    Color(0xFF8A63D2), Color(0xFF14A3B8), Color(0xFF6FA83A), Color(0xFFD9566E)
)

/** Cerchio tenue con l'icona della categoria. Il colore dipende solo dalla chiave, quindi è stabile. */
@Composable
fun CategoryBadge(iconKey: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val accent = badgeAccents[Math.floorMod(iconKey.hashCode(), badgeAccents.size)]
    val dark = LocalDarkTheme.current
    Box(
        modifier.size(size).clip(CircleShape).background(accent.copy(alpha = if (dark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            CategoryIcons.get(iconKey), contentDescription = null,
            tint = if (dark) accent.copy(alpha = 1f).lighten() else accent,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

private fun Color.lighten(): Color = Color(
    red = red + (1f - red) * 0.35f, green = green + (1f - green) * 0.35f, blue = blue + (1f - blue) * 0.35f, alpha = alpha
)

/** Selettore di icona: "Automatica" (dal nome) più tutte le icone del catalogo. [selected] == null = automatica. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IconPicker(selected: String?, previewName: String, onSelect: (String?) -> Unit) {
    val auto = IconRules.guess(previewName) ?: IconRules.DEFAULT
    FilterChip(
        selected = selected == null,
        onClick = { onSelect(null) },
        label = { Text("Automatica") },
        leadingIcon = { Icon(CategoryIcons.get(auto), contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryIcons.selectable.forEach { key ->
            val isSelected = key == selected
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    CategoryIcons.get(key), contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
