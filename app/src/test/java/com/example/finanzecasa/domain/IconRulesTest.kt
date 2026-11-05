package com.example.finanzecasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconRulesTest {
    @Test fun `parole chiave del nome scelgono l'icona`() {
        assertEquals("grocery", IconRules.guess("Cibo/Bevande e Super"))
        assertEquals("grocery", IconRules.guess("Spesa alimentare"))
        assertEquals("home", IconRules.guess("Mutuo"))
        assertEquals("gas", IconRules.guess("Benzina"))
        assertEquals("bolt", IconRules.guess("Luce"))
        assertEquals("bolt", IconRules.guess("Gas"))
    }

    @Test fun `nome senza parole note non ha suggerimenti`() {
        assertNull(IconRules.guess("Business"))
        assertNull(IconRules.guess(""))
        assertNull(IconRules.guess(null))
    }

    @Test fun `l'icona scelta dall'utente vince su tutto`() {
        assertEquals("pets", IconRules.resolve("pets", "Mutuo", "bolt", "Casa"))
    }

    @Test fun `senza scelta si usa il nome, poi la categoria principale, poi quella generica`() {
        assertEquals("home", IconRules.resolve(null, "Mutuo", "bolt", "Casa"))
        assertEquals("bolt", IconRules.resolve(null, "Varie", "bolt", "Casa"))
        assertEquals("home", IconRules.resolve(null, "Varie", null, "Casa"))
        assertEquals(IconRules.DEFAULT, IconRules.resolve(null, "Varie", null, "Altro"))
        assertEquals(IconRules.DEFAULT, IconRules.resolve(null, null, null, null))
    }
}
