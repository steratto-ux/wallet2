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

    @Test fun `nuove icone della versione 1_4 si deducono dal nome`() {
        assertEquals("laundry", IconRules.guess("Lavanderia"))
        assertEquals("bike", IconRules.guess("Bici e monopattino"))
        assertEquals("soccer", IconRules.guess("Tennis e padel"))
        assertEquals("legal", IconRules.guess("Multe"))
        assertEquals("party", IconRules.guess("Feste"))
        assertEquals("cake", IconRules.guess("Dolci"))
        assertEquals("kitchen", IconRules.guess("Elettrodomestici"))
    }

    @Test fun `le regole esistenti non cambiano con le nuove icone`() {
        assertEquals("restaurant", IconRules.guess("Pizza"))
        assertEquals("medical", IconRules.guess("Farmacia"))
        assertEquals("bus", IconRules.guess("Taxi"))
        assertEquals("car", IconRules.guess("Parcheggio"))
        assertEquals("beach", IconRules.guess("Hotel"))
        assertEquals("phone", IconRules.guess("Internet"))
        assertEquals("school", IconRules.guess("Libri"))
        assertNull(IconRules.guess("Mobilità"))
        assertNull(IconRules.guess("Fastweb"))
        assertNull(IconRules.guess("Offerte"))
    }

    @Test fun `le chiavi delle regole non si ripetono`() {
        val keys = IconRules.iconKeys()
        assertEquals(keys.size, keys.toSet().size)
    }
}
