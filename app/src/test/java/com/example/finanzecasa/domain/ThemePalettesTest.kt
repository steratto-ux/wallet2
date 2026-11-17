package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica la leggibilità (contrasto WCAG) di tutti i temi, in versione chiara e scura. */
class ThemePalettesTest {

    private fun forEachPalette(block: (tag: String, spec: ThemeSpec, c: PaletteColors) -> Unit) {
        for (spec in ThemePalettes.all) {
            block("${spec.style}/chiaro", spec, spec.light)
            block("${spec.style}/scuro", spec, spec.dark)
        }
    }

    private fun need(failures: MutableList<String>, tag: String, what: String, fg: Long, bg: Long, min: Double) {
        val ratio = ThemePalettes.contrast(fg, bg)
        if (ratio < min) failures += "$tag: $what ${"%.2f".format(ratio)} < $min"
    }

    @Test fun contrastFormulaMatchesKnownValues() {
        assertEquals(21.0, ThemePalettes.contrast(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, ThemePalettes.contrast(0xFF336699, 0xFF336699), 0.0001)
        // Il grigio #767676 su bianco è il limite classico di 4,5:1
        assertEquals(4.54, ThemePalettes.contrast(0xFF767676, 0xFFFFFFFF), 0.02)
    }

    @Test fun everyStyleHasASpec() {
        assertEquals(ThemeStyle.values().toSet(), ThemePalettes.all.map { it.style }.toSet())
        assertEquals(5, ThemePalettes.all.size)
        ThemeStyle.values().forEach { assertEquals(it, ThemePalettes.spec(it).style) }
    }

    @Test fun textIsReadableOnEverySurface() {
        val failures = mutableListOf<String>()
        forEachPalette { tag, _, c ->
            val surfaces = listOf(
                c.background, c.containerLowest, c.containerLow, c.container, c.containerHigh, c.containerHighest
            )
            for (s in surfaces) {
                need(failures, tag, "testo principale", c.onBackground, s, 4.5)
                need(failures, tag, "testo secondario", c.onSurfaceVariant, s, 4.5)
            }
            need(failures, tag, "testo su surfaceVariant", c.onSurfaceVariant, c.surfaceVariant, 4.5)
            need(failures, tag, "testo principale su surfaceVariant", c.onBackground, c.surfaceVariant, 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun coloredTextIsReadableOnBackgroundAndCards() {
        val failures = mutableListOf<String>()
        forEachPalette { tag, _, c ->
            for (bg in listOf(c.background, c.containerLowest, c.container)) {
                need(failures, tag, "primario", c.primary, bg, 4.5)
                need(failures, tag, "uscite", c.error, bg, 4.5)
                need(failures, tag, "entrate", c.income, bg, 4.5)
                need(failures, tag, "terziario", c.tertiary, bg, 4.5)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun textOnColoredFillsIsReadable() {
        val failures = mutableListOf<String>()
        forEachPalette { tag, _, c ->
            need(failures, tag, "su primary", c.onPrimary, c.primary, 4.5)
            need(failures, tag, "su primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5)
            need(failures, tag, "su secondary", c.onSecondary, c.secondary, 4.5)
            need(failures, tag, "su secondaryContainer", c.onSecondaryContainer, c.secondaryContainer, 4.5)
            need(failures, tag, "su tertiary", c.onTertiary, c.tertiary, 4.5)
            need(failures, tag, "su error", c.onError, c.error, 4.5)
            need(failures, tag, "su errorContainer", c.onErrorContainer, c.errorContainer, 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun bordersAndChartsAreVisible() {
        val failures = mutableListOf<String>()
        forEachPalette { tag, spec, c ->
            // Bordi dei campi di testo: almeno 3:1 (elementi di interfaccia)
            need(failures, tag, "bordo campi su sfondo", c.outline, c.background, 3.0)
            need(failures, tag, "bordo campi su box", c.outline, c.containerLowest, 3.0)
            // Bordi di box e pulsanti: sempre visibili; nel tema Minimal al massimo contrasto
            val minBorder = if (spec.style == ThemeStyle.MINIMAL) 7.0 else 1.3
            need(failures, tag, "bordo box", c.cardBorder, c.background, minBorder)
            need(failures, tag, "bordo pulsanti", c.navBorder, c.background, minBorder)
            need(failures, tag, "accento", c.accent, c.background, 1.3)
            // Colori del grafico: almeno 3:1 sul box in cui sono disegnati
            assertTrue("$tag: servono almeno 5 colori per il grafico", c.chart.size >= 5)
            c.chart.forEach { need(failures, tag, "grafico", it, c.containerLowest, 3.0) }
            assertEquals("$tag: colori del grafico tutti diversi", c.chart.size, c.chart.toSet().size)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun lightAndDarkVersionsAreDifferent() {
        for (spec in ThemePalettes.all) {
            assertTrue("${spec.style}: sfondo chiaro più chiaro dello scuro",
                ThemePalettes.luminance(spec.light.background) > ThemePalettes.luminance(spec.dark.background))
        }
    }
}
