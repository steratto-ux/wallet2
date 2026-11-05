package com.example.finanzecasa.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finanzecasa.data.AccountType

// Palette volutamente spenta: grigi caldi, un verde salvia come colore principale, rosso terra per le uscite.
private val LightColors = lightColorScheme(
    primary = Color(0xFF45605A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EAE6),
    onPrimaryContainer = Color(0xFF22302C),
    secondary = Color(0xFF6B7773),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9ECEA),
    onSecondaryContainer = Color(0xFF262E2B),
    tertiary = Color(0xFF8A6F63),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F4F1),
    onBackground = Color(0xFF1F2321),
    surface = Color(0xFFF5F4F1),
    onSurface = Color(0xFF1F2321),
    surfaceVariant = Color(0xFFE8E6E1),
    onSurfaceVariant = Color(0xFF6A6E6B),
    surfaceTint = Color(0xFF45605A),
    outline = Color(0xFFB5B6B1),
    outlineVariant = Color(0xFFE3E1DC),
    error = Color(0xFFA85A50),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF3E3DF),
    onErrorContainer = Color(0xFF5A2A23),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF9F7),
    surfaceContainer = Color(0xFFF1F0EC),
    surfaceContainerHigh = Color(0xFFECEAE6),
    surfaceContainerHighest = Color(0xFFE6E4DF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C2B8),
    onPrimary = Color(0xFF16221E),
    primaryContainer = Color(0xFF2B3A35),
    onPrimaryContainer = Color(0xFFD8E5DF),
    secondary = Color(0xFF9AA5A1),
    onSecondary = Color(0xFF1B2220),
    secondaryContainer = Color(0xFF2A302E),
    onSecondaryContainer = Color(0xFFDCE2DF),
    tertiary = Color(0xFFC3A598),
    onTertiary = Color(0xFF2E1E17),
    background = Color(0xFF141615),
    onBackground = Color(0xFFE6E7E4),
    surface = Color(0xFF141615),
    onSurface = Color(0xFFE6E7E4),
    surfaceVariant = Color(0xFF2A2D2B),
    onSurfaceVariant = Color(0xFF9EA39F),
    surfaceTint = Color(0xFFA9C2B8),
    outline = Color(0xFF5A5E5B),
    outlineVariant = Color(0xFF2F3331),
    error = Color(0xFFD99A8F),
    onError = Color(0xFF3A1914),
    errorContainer = Color(0xFF4A2C27),
    onErrorContainer = Color(0xFFF3D9D4),
    surfaceContainerLowest = Color(0xFF1B1E1D),
    surfaceContainerLow = Color(0xFF1E2120),
    surfaceContainer = Color(0xFF232625),
    surfaceContainerHigh = Color(0xFF2A2D2B),
    surfaceContainerHighest = Color(0xFF313533)
)

private val Base = Typography()

private val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Normal),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Normal),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** Verde spento per le entrate (le uscite usano il colore "error", un rosso terra). */
val ColorScheme.income: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF9CC2A8) else Color(0xFF4C7A5B)

/** Tinta tenue dell'icona di un account in base al tipo. */
fun accountTint(type: AccountType, dark: Boolean): Color = when (type) {
    AccountType.BANK -> if (dark) Color(0xFF9FB1C7) else Color(0xFF5F7791)
    AccountType.CASH -> if (dark) Color(0xFF9CC2A8) else Color(0xFF5F8A6D)
    AccountType.CREDIT_CARD -> if (dark) Color(0xFFD2B48F) else Color(0xFF9C7B54)
}

/** Stile del numero grande (saldi, patrimonio). */
val BigNumberStyle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
