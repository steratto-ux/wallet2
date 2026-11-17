package com.example.finanzecasa.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Temi di colore dell'app. Ogni tema ha una versione chiara e una scura, i propri colori per i bordi
 * di box e pulsanti e un raggio degli angoli. I colori sono numeri ARGB (0xFFRRGGBB) in Kotlin puro, così
 * i test possono verificarne la leggibilità (contrasto WCAG) senza avviare Android.
 */
enum class ThemeStyle(val label: String, val description: String) {
    SMERALDO("Smeraldo", "Verde vivace, bordi verdi"),
    INDACO("Indaco e corallo", "Indaco con accenti corallo"),
    FERRARI("Rosso Ferrari", "Rosso corsa, nero e giallo"),
    MINIMAL("Minimal B/N", "Bianco e nero, massimo contrasto"),
    ARDESIA("Ardesia e corallo", "Grigio ardesia, salvia e corallo")
}

/** Tutti i colori di un tema in una modalità (chiara o scura). */
data class PaletteColors(
    val primary: Long, val onPrimary: Long,
    val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long,
    val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long,
    val background: Long, val onBackground: Long,
    val surfaceVariant: Long, val onSurfaceVariant: Long,
    val outline: Long, val outlineVariant: Long,
    val error: Long, val onError: Long,
    val errorContainer: Long, val onErrorContainer: Long,
    val containerLowest: Long, val containerLow: Long, val container: Long,
    val containerHigh: Long, val containerHighest: Long,
    /** Verde delle entrate (le uscite usano [error]). */
    val income: Long,
    /** Bordo dei box (card). */
    val cardBorder: Long,
    /** Bordo dei pulsanti rapidi. */
    val navBorder: Long,
    /** Colore d'accento: bordo del pulsante largo e dettagli. */
    val accent: Long,
    /** Colori dei grafici (ciambella delle categorie), distinguibili tra loro e dallo sfondo. */
    val chart: List<Long>
)

/** Un tema completo: versione chiara, versione scura, spessore dei bordi e raggio degli angoli (dp). */
data class ThemeSpec(
    val style: ThemeStyle,
    val radiusDp: Int,
    val cardBorderDp: Float,
    val navBorderDp: Float,
    val light: PaletteColors,
    val dark: PaletteColors
) {
    fun colors(dark: Boolean): PaletteColors = if (dark) this.dark else this.light
}

object ThemePalettes {
    val DEFAULT = ThemeStyle.SMERALDO

    private val specs: Map<ThemeStyle, ThemeSpec> by lazy { all.associateBy { it.style } }

    fun spec(style: ThemeStyle): ThemeSpec = specs.getValue(style)

    /** Luminanza relativa (WCAG 2.x) di un colore 0xFFRRGGBB. */
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Rapporto di contrasto WCAG tra due colori (da 1 a 21). Per il testo normale serve almeno 4,5. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    val all: List<ThemeSpec> by lazy {
        listOf(
            ThemeSpec(
                style = ThemeStyle.SMERALDO, radiusDp = 18, cardBorderDp = 1.5f, navBorderDp = 2.0f,
                light = PaletteColors(
                    primary = 0xFF00785A,
                    onPrimary = 0xFFFFFFFF,
                    primaryContainer = 0xFFBDF5DC,
                    onPrimaryContainer = 0xFF00321F,
                    secondary = 0xFF2F6B7E,
                    onSecondary = 0xFFFFFFFF,
                    secondaryContainer = 0xFFD2EEF7,
                    onSecondaryContainer = 0xFF0B3441,
                    tertiary = 0xFFA85A00,
                    onTertiary = 0xFFFFFFFF,
                    background = 0xFFF2FAF6,
                    onBackground = 0xFF0F1F1A,
                    surfaceVariant = 0xFFDCEEE5,
                    onSurfaceVariant = 0xFF3F5A50,
                    outline = 0xFF5E8173,
                    outlineVariant = 0xFFB5D6C6,
                    error = 0xFFC42B3F,
                    onError = 0xFFFFFFFF,
                    errorContainer = 0xFFFFD9DC,
                    onErrorContainer = 0xFF5C0A16,
                    containerLowest = 0xFFFFFFFF,
                    containerLow = 0xFFF7FCF9,
                    container = 0xFFE8F5EE,
                    containerHigh = 0xFFDDF0E6,
                    containerHighest = 0xFFD2EADF,
                    income = 0xFF087443,
                    cardBorder = 0xFF8FD1B5,
                    navBorder = 0xFF00A878,
                    accent = 0xFFD4A017,
                    chart = listOf(0xFF009A6E, 0xFF2F6FD6, 0xFFE0592F, 0xFFB87800, 0xFF8A4FD6)
                ),
                dark = PaletteColors(
                    primary = 0xFF3EE8AE,
                    onPrimary = 0xFF00382A,
                    primaryContainer = 0xFF0F5A44,
                    onPrimaryContainer = 0xFFC2F8E3,
                    secondary = 0xFF8CD0E6,
                    onSecondary = 0xFF08303D,
                    secondaryContainer = 0xFF1D4A5A,
                    onSecondaryContainer = 0xFFD1EEF9,
                    tertiary = 0xFFFFC24B,
                    onTertiary = 0xFF3A2600,
                    background = 0xFF0B1512,
                    onBackground = 0xFFE4F3EC,
                    surfaceVariant = 0xFF20352D,
                    onSurfaceVariant = 0xFFA9C6BA,
                    outline = 0xFF6E9486,
                    outlineVariant = 0xFF25403A,
                    error = 0xFFFF8A97,
                    onError = 0xFF5C0A16,
                    errorContainer = 0xFF6B1B27,
                    onErrorContainer = 0xFFFFD9DC,
                    containerLowest = 0xFF111D19,
                    containerLow = 0xFF15231E,
                    container = 0xFF1A2B25,
                    containerHigh = 0xFF213730,
                    containerHighest = 0xFF28423A,
                    income = 0xFF4BE39A,
                    cardBorder = 0xFF2E6B58,
                    navBorder = 0xFF3EE8AE,
                    accent = 0xFFFFC24B,
                    chart = listOf(0xFF3EE8AE, 0xFF6EA8FF, 0xFFFF8A5E, 0xFFFFC24B, 0xFFC39BFF)
                )
            ),
            ThemeSpec(
                style = ThemeStyle.INDACO, radiusDp = 24, cardBorderDp = 1.5f, navBorderDp = 2.0f,
                light = PaletteColors(
                    primary = 0xFF4338CA,
                    onPrimary = 0xFFFFFFFF,
                    primaryContainer = 0xFFE0DEFF,
                    onPrimaryContainer = 0xFF1B1464,
                    secondary = 0xFFB83A0A,
                    onSecondary = 0xFFFFFFFF,
                    secondaryContainer = 0xFFFFDDD1,
                    onSecondaryContainer = 0xFF5A1A06,
                    tertiary = 0xFF9A5000,
                    onTertiary = 0xFFFFFFFF,
                    background = 0xFFF5F4FF,
                    onBackground = 0xFF17163A,
                    surfaceVariant = 0xFFE6E4FA,
                    onSurfaceVariant = 0xFF4B4A7A,
                    outline = 0xFF6F6CB0,
                    outlineVariant = 0xFFCFCDF0,
                    error = 0xFFC42B3F,
                    onError = 0xFFFFFFFF,
                    errorContainer = 0xFFFFD9DC,
                    onErrorContainer = 0xFF5C0A16,
                    containerLowest = 0xFFFFFFFF,
                    containerLow = 0xFFFAF9FF,
                    container = 0xFFEEEDFC,
                    containerHigh = 0xFFE6E4FA,
                    containerHighest = 0xFFDDDBF6,
                    income = 0xFF0B7A57,
                    cardBorder = 0xFFB9B5F2,
                    navBorder = 0xFFFF7A59,
                    accent = 0xFFFF7A59,
                    chart = listOf(0xFF4338CA, 0xFFE0592F, 0xFF0F8F7A, 0xFFB87800, 0xFFB5367A)
                ),
                dark = PaletteColors(
                    primary = 0xFFA5A0FF,
                    onPrimary = 0xFF1B1464,
                    primaryContainer = 0xFF3B33A8,
                    onPrimaryContainer = 0xFFE3E1FF,
                    secondary = 0xFFFF9E80,
                    onSecondary = 0xFF4A1400,
                    secondaryContainer = 0xFF6A2A12,
                    onSecondaryContainer = 0xFFFFDDD1,
                    tertiary = 0xFFFFC24B,
                    onTertiary = 0xFF3A2600,
                    background = 0xFF12112B,
                    onBackground = 0xFFEAE9FF,
                    surfaceVariant = 0xFF26244F,
                    onSurfaceVariant = 0xFFBDBBE8,
                    outline = 0xFF7F7CC4,
                    outlineVariant = 0xFF34326A,
                    error = 0xFFFF8A97,
                    onError = 0xFF5C0A16,
                    errorContainer = 0xFF6B1B27,
                    onErrorContainer = 0xFFFFD9DC,
                    containerLowest = 0xFF181738,
                    containerLow = 0xFF1D1C42,
                    container = 0xFF232250,
                    containerHigh = 0xFF2B2A5E,
                    containerHighest = 0xFF33326C,
                    income = 0xFF52E0A7,
                    cardBorder = 0xFF4A46A0,
                    navBorder = 0xFFFF7A59,
                    accent = 0xFFFF7A59,
                    chart = listOf(0xFFA5A0FF, 0xFFFF9E80, 0xFF4FE0C4, 0xFFFFC24B, 0xFFFF8FC4)
                )
            ),
            ThemeSpec(
                style = ThemeStyle.FERRARI, radiusDp = 8, cardBorderDp = 1.5f, navBorderDp = 2.0f,
                light = PaletteColors(
                    primary = 0xFFC40000,
                    onPrimary = 0xFFFFFFFF,
                    primaryContainer = 0xFFFFD9D3,
                    onPrimaryContainer = 0xFF4A0000,
                    secondary = 0xFF2B2B2B,
                    onSecondary = 0xFFFFFFFF,
                    secondaryContainer = 0xFFE6E1DF,
                    onSecondaryContainer = 0xFF1A1111,
                    tertiary = 0xFF8A5A00,
                    onTertiary = 0xFFFFFFFF,
                    background = 0xFFFAF7F5,
                    onBackground = 0xFF1A1111,
                    surfaceVariant = 0xFFEDE5E2,
                    onSurfaceVariant = 0xFF5A4743,
                    outline = 0xFF8A6F6A,
                    outlineVariant = 0xFFDCCBC6,
                    error = 0xFFB00020,
                    onError = 0xFFFFFFFF,
                    errorContainer = 0xFFFFD9DC,
                    onErrorContainer = 0xFF5C0A16,
                    containerLowest = 0xFFFFFFFF,
                    containerLow = 0xFFFCFAF9,
                    container = 0xFFF3EEEC,
                    containerHigh = 0xFFECE5E2,
                    containerHighest = 0xFFE3DAD6,
                    income = 0xFF0B6B3A,
                    cardBorder = 0xFF1A1111,
                    navBorder = 0xFFFFD100,
                    accent = 0xFFFFD100,
                    chart = listOf(0xFFC40000, 0xFF1A1111, 0xFF2F6FD6, 0xFF8A5A00, 0xFF0B6B3A)
                ),
                dark = PaletteColors(
                    primary = 0xFFFF5A4D,
                    onPrimary = 0xFF3A0000,
                    primaryContainer = 0xFF8E0000,
                    onPrimaryContainer = 0xFFFFDAD5,
                    secondary = 0xFFD8D0CE,
                    onSecondary = 0xFF1A1111,
                    secondaryContainer = 0xFF3A3231,
                    onSecondaryContainer = 0xFFF1E8E5,
                    tertiary = 0xFFFFD100,
                    onTertiary = 0xFF2B2100,
                    background = 0xFF0F0B0B,
                    onBackground = 0xFFF6EDEA,
                    surfaceVariant = 0xFF2A2120,
                    onSurfaceVariant = 0xFFCDBFBB,
                    outline = 0xFF8F7C77,
                    outlineVariant = 0xFF3B2C2A,
                    error = 0xFFFF8A80,
                    onError = 0xFF4A0000,
                    errorContainer = 0xFF6B1B1B,
                    onErrorContainer = 0xFFFFDAD5,
                    containerLowest = 0xFF171110,
                    containerLow = 0xFF1C1514,
                    container = 0xFF221A19,
                    containerHigh = 0xFF2A201F,
                    containerHighest = 0xFF332726,
                    income = 0xFF53D98C,
                    cardBorder = 0xFF8E0000,
                    navBorder = 0xFFFFD100,
                    accent = 0xFFFFD100,
                    chart = listOf(0xFFFF5A4D, 0xFFF6EDEA, 0xFF6EA8FF, 0xFFFFD100, 0xFF53D98C)
                )
            ),
            ThemeSpec(
                style = ThemeStyle.MINIMAL, radiusDp = 4, cardBorderDp = 1.5f, navBorderDp = 2.0f,
                light = PaletteColors(
                    primary = 0xFF000000,
                    onPrimary = 0xFFFFFFFF,
                    primaryContainer = 0xFFEDEDED,
                    onPrimaryContainer = 0xFF000000,
                    secondary = 0xFF333333,
                    onSecondary = 0xFFFFFFFF,
                    secondaryContainer = 0xFFE0E0E0,
                    onSecondaryContainer = 0xFF000000,
                    tertiary = 0xFF555555,
                    onTertiary = 0xFFFFFFFF,
                    background = 0xFFFFFFFF,
                    onBackground = 0xFF000000,
                    surfaceVariant = 0xFFEBEBEB,
                    onSurfaceVariant = 0xFF3D3D3D,
                    outline = 0xFF6B6B6B,
                    outlineVariant = 0xFF8A8A8A,
                    error = 0xFFB00020,
                    onError = 0xFFFFFFFF,
                    errorContainer = 0xFFFFE0E0,
                    onErrorContainer = 0xFF5A0010,
                    containerLowest = 0xFFFFFFFF,
                    containerLow = 0xFFFAFAFA,
                    container = 0xFFF2F2F2,
                    containerHigh = 0xFFEAEAEA,
                    containerHighest = 0xFFE0E0E0,
                    income = 0xFF006B2D,
                    cardBorder = 0xFF000000,
                    navBorder = 0xFF000000,
                    accent = 0xFF000000,
                    chart = listOf(0xFF000000, 0xFF0B57D0, 0xFFB3261E, 0xFF8A5A00, 0xFF6A3FA0)
                ),
                dark = PaletteColors(
                    primary = 0xFFFFFFFF,
                    onPrimary = 0xFF000000,
                    primaryContainer = 0xFF262626,
                    onPrimaryContainer = 0xFFFFFFFF,
                    secondary = 0xFFCCCCCC,
                    onSecondary = 0xFF000000,
                    secondaryContainer = 0xFF2E2E2E,
                    onSecondaryContainer = 0xFFFFFFFF,
                    tertiary = 0xFFB8B8B8,
                    onTertiary = 0xFF000000,
                    background = 0xFF000000,
                    onBackground = 0xFFFFFFFF,
                    surfaceVariant = 0xFF1A1A1A,
                    onSurfaceVariant = 0xFFD0D0D0,
                    outline = 0xFF9A9A9A,
                    outlineVariant = 0xFF6E6E6E,
                    error = 0xFFFF8A80,
                    onError = 0xFF4A0000,
                    errorContainer = 0xFF4A1010,
                    onErrorContainer = 0xFFFFDAD5,
                    containerLowest = 0xFF0A0A0A,
                    containerLow = 0xFF101010,
                    container = 0xFF161616,
                    containerHigh = 0xFF1D1D1D,
                    containerHighest = 0xFF262626,
                    income = 0xFF5CE08A,
                    cardBorder = 0xFFFFFFFF,
                    navBorder = 0xFFFFFFFF,
                    accent = 0xFFFFFFFF,
                    chart = listOf(0xFFFFFFFF, 0xFF6EA8FF, 0xFFFF8A80, 0xFFFFC24B, 0xFFC39BFF)
                )
            ),
            ThemeSpec(
                style = ThemeStyle.ARDESIA, radiusDp = 14, cardBorderDp = 1.5f, navBorderDp = 2.0f,
                light = PaletteColors(
                    primary = 0xFF3F6B64,
                    onPrimary = 0xFFFFFFFF,
                    primaryContainer = 0xFFCFE0DB,
                    onPrimaryContainer = 0xFF1E3A35,
                    secondary = 0xFF575757,
                    onSecondary = 0xFFFFFFFF,
                    secondaryContainer = 0xFFDAD6C8,
                    onSecondaryContainer = 0xFF2B2D2D,
                    tertiary = 0xFFB8332A,
                    onTertiary = 0xFFFFFFFF,
                    background = 0xFFF1EEE3,
                    onBackground = 0xFF2B2D2D,
                    surfaceVariant = 0xFFE2DED0,
                    onSurfaceVariant = 0xFF4A4C4C,
                    outline = 0xFF6F7171,
                    outlineVariant = 0xFFCFCBBB,
                    error = 0xFFC0322A,
                    onError = 0xFFFFFFFF,
                    errorContainer = 0xFFFBD9D5,
                    onErrorContainer = 0xFF4A100C,
                    containerLowest = 0xFFFBF9F2,
                    containerLow = 0xFFF6F3EA,
                    container = 0xFFEBE7DA,
                    containerHigh = 0xFFE4E0D2,
                    containerHighest = 0xFFDCD8C9,
                    income = 0xFF2F6E4F,
                    cardBorder = 0xFF74958F,
                    navBorder = 0xFFF26B62,
                    accent = 0xFFF26B62,
                    chart = listOf(0xFF3F6B64, 0xFFD9453B, 0xFFB07A00, 0xFF4A6FA5, 0xFF7A5C8F)
                ),
                dark = PaletteColors(
                    primary = 0xFF9CC0B8,
                    onPrimary = 0xFF17302B,
                    primaryContainer = 0xFF4F6F69,
                    onPrimaryContainer = 0xFFFFFFFF,
                    secondary = 0xFFD0D0D0,
                    onSecondary = 0xFF2B2D2D,
                    secondaryContainer = 0xFF626464,
                    onSecondaryContainer = 0xFFFFFFFF,
                    tertiary = 0xFFFF8F85,
                    onTertiary = 0xFF3A0A06,
                    background = 0xFF3A3C3C,
                    onBackground = 0xFFF1EEE3,
                    surfaceVariant = 0xFF4A4C4C,
                    onSurfaceVariant = 0xFFE3E0D4,
                    outline = 0xFFB0B2B2,
                    outlineVariant = 0xFF747676,
                    error = 0xFFFFA89F,
                    onError = 0xFF4A100C,
                    errorContainer = 0xFF6B2A24,
                    onErrorContainer = 0xFFFFDAD5,
                    containerLowest = 0xFF2F3131,
                    containerLow = 0xFF333535,
                    container = 0xFF3E4040,
                    containerHigh = 0xFF474949,
                    containerHighest = 0xFF505252,
                    income = 0xFFA3E6BC,
                    cardBorder = 0xFF74958F,
                    navBorder = 0xFFF26B62,
                    accent = 0xFFF26B62,
                    chart = listOf(0xFF9CC0B8, 0xFFFF8F85, 0xFFFFD27A, 0xFF9DC1F2, 0xFFD2B2E6)
                )
            )
        )
    }
}
