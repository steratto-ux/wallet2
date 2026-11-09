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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finanzecasa.data.AccountType

// Palette fresca ma non aggressiva: verde acqua come colore principale, rosso corallo per le uscite.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1E9E7C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F1E6),
    onPrimaryContainer = Color(0xFF0B3D2E),
    secondary = Color(0xFF5B7A8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0ECF3),
    onSecondaryContainer = Color(0xFF1D3441),
    tertiary = Color(0xFFD98A3D),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F6F4),
    onBackground = Color(0xFF16201D),
    surface = Color(0xFFF2F6F4),
    onSurface = Color(0xFF16201D),
    surfaceVariant = Color(0xFFE3ECE8),
    onSurfaceVariant = Color(0xFF5C6B66),
    surfaceTint = Color(0xFF1E9E7C),
    outline = Color(0xFF9DB0A8),
    outlineVariant = Color(0xFFDAE5E0),
    error = Color(0xFFD6493D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDDD9),
    onErrorContainer = Color(0xFF5A1A14),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFA),
    surfaceContainer = Color(0xFFEAF2EE),
    surfaceContainerHigh = Color(0xFFE3EDE9),
    surfaceContainerHighest = Color(0xFFDCE7E2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1A8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1B4A3C),
    onPrimaryContainer = Color(0xFFBDF2E0),
    secondary = Color(0xFF9CC0D4),
    onSecondary = Color(0xFF12303F),
    secondaryContainer = Color(0xFF2A4455),
    onSecondaryContainer = Color(0xFFD3E8F4),
    tertiary = Color(0xFFF0B070),
    onTertiary = Color(0xFF3A2108),
    background = Color(0xFF101514),
    onBackground = Color(0xFFE3EAE7),
    surface = Color(0xFF101514),
    onSurface = Color(0xFFE3EAE7),
    surfaceVariant = Color(0xFF2A3431),
    onSurfaceVariant = Color(0xFFA5B5AF),
    surfaceTint = Color(0xFF4FD1A8),
    outline = Color(0xFF5E716A),
    outlineVariant = Color(0xFF2B3935),
    error = Color(0xFFFF8A7D),
    onError = Color(0xFF4A150F),
    errorContainer = Color(0xFF5A2A24),
    onErrorContainer = Color(0xFFFFDAD5),
    surfaceContainerLowest = Color(0xFF171D1B),
    surfaceContainerLow = Color(0xFF1A2120),
    surfaceContainer = Color(0xFF1F2725),
    surfaceContainerHigh = Color(0xFF263230),
    surfaceContainerHighest = Color(0xFF2C3A37)
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

/** Scelta del tema: segue il telefono, oppure sempre chiaro o sempre scuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Vale true quando l'app sta usando i colori scuri (scelta dell'utente o impostazione del telefono). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/** Verde per le entrate (le uscite usano il colore "error", un rosso corallo). */
val ColorScheme.income: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF5FD39B) else Color(0xFF1E9E6A)

/** Tinta tenue dell'icona di un account in base al tipo. */
fun accountTint(type: AccountType, dark: Boolean): Color = when (type) {
    AccountType.BANK -> if (dark) Color(0xFF8DB6F2) else Color(0xFF3F7BD1)
    AccountType.CASH -> if (dark) Color(0xFF5FD39B) else Color(0xFF1E9E6A)
    AccountType.CREDIT_CARD -> if (dark) Color(0xFFF2B766) else Color(0xFFE0892B)
}

/** Stile del numero grande (saldi, patrimonio). */
val BigNumberStyle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
