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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.domain.PaletteColors
import com.example.finanzecasa.domain.ThemePalettes
import com.example.finanzecasa.domain.ThemeSpec
import com.example.finanzecasa.domain.ThemeStyle

/** Schema colori di Material per un tema in una modalità. I colori veri stanno in [ThemePalettes]. */
private fun PaletteColors.toScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onTertiary),
        background = Color(background), onBackground = Color(onBackground),
        surface = Color(background), onSurface = Color(onBackground),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        outline = Color(outline), outlineVariant = Color(outlineVariant),
        error = Color(error), onError = Color(onError),
        errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        surfaceContainerLowest = Color(containerLowest), surfaceContainerLow = Color(containerLow),
        surfaceContainer = Color(container), surfaceContainerHigh = Color(containerHigh),
        surfaceContainerHighest = Color(containerHighest)
    )
}

/** Dettagli grafici del tema che Material non prevede: bordi di box e pulsanti, accento, colori dei grafici. */
@Immutable
class AppStyle(
    val cardBorder: Color,
    val cardBorderWidth: Dp,
    val navBorder: Color,
    val navBorderWidth: Dp,
    val accent: Color,
    val income: Color,
    val chart: List<Color>
)

private fun ThemeSpec.appStyle(dark: Boolean): AppStyle {
    val c = colors(dark)
    return AppStyle(
        cardBorder = Color(c.cardBorder), cardBorderWidth = cardBorderDp.dp,
        navBorder = Color(c.navBorder), navBorderWidth = navBorderDp.dp,
        accent = Color(c.accent), income = Color(c.income),
        chart = c.chart.map { Color(it) }
    )
}

/** Stile del tema in uso (bordi, accento, colori dei grafici). */
val LocalAppStyle = staticCompositionLocalOf { ThemePalettes.spec(ThemePalettes.DEFAULT).appStyle(false) }

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

/** Gli angoli seguono il raggio del tema: netti per Minimal e Ferrari, molto morbidi per Indaco. */
private fun shapesFor(radiusDp: Int): Shapes {
    fun r(f: Float) = RoundedCornerShape(maxOf(2f, radiusDp * f).dp)
    return Shapes(extraSmall = r(0.35f), small = r(0.55f), medium = r(0.8f), large = r(1f), extraLarge = r(1.35f))
}

/** Scelta del tema: segue il telefono, oppure sempre chiaro o sempre scuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Vale true quando l'app sta usando i colori scuri (scelta dell'utente o impostazione del telefono). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    style: ThemeStyle = ThemePalettes.DEFAULT,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val spec = ThemePalettes.spec(style)
    val scheme = remember(style, dark) { spec.colors(dark).toScheme(dark) }
    val appStyle = remember(style, dark) { spec.appStyle(dark) }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalAppStyle provides appStyle) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = remember(style) { shapesFor(spec.radiusDp) },
            content = content
        )
    }
}

/** Verde per le entrate (le uscite usano il colore "error"). Dipende dal tema. */
val ColorScheme.income: Color
    @Composable get() = LocalAppStyle.current.income

/** Tinta tenue dell'icona di un account in base al tipo. */
fun accountTint(type: AccountType, dark: Boolean): Color = when (type) {
    AccountType.BANK -> if (dark) Color(0xFF8DB6F2) else Color(0xFF3F7BD1)
    AccountType.CASH -> if (dark) Color(0xFF5FD39B) else Color(0xFF1E9E6A)
    AccountType.CREDIT_CARD -> if (dark) Color(0xFFF2B766) else Color(0xFFE0892B)
    AccountType.INVESTMENT -> if (dark) Color(0xFFBEA2F0) else Color(0xFF8A63D2)
}

/** Stile del numero grande (saldi, patrimonio). */
val BigNumberStyle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
