package com.example.finanzecasa

import com.example.finanzecasa.ui.parseAmountToCents
import com.example.finanzecasa.ui.parseSignedCents
import com.example.finanzecasa.ui.centsToInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseAmountTest {
    @Test fun `formati italiani e internazionali`() {
        assertEquals(1250L, parseAmountToCents("12,50"))
        assertEquals(1250L, parseAmountToCents("12.50"))
        assertEquals(123450L, parseAmountToCents("1.234,50"))
        assertEquals(1000L, parseAmountToCents(" 10 "))
        assertEquals(1L, parseAmountToCents("0,005")) // arrotondamento al centesimo
    }

    @Test fun `valori non validi`() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("0"))
        assertNull(parseAmountToCents("-5"))
    }

    @Test fun `valori con segno e zero per il valore iniziale`() {
        assertEquals(-15000L, parseSignedCents("-150"))
        assertEquals(0L, parseSignedCents("0"))
        assertEquals(-123450L, parseSignedCents("-1.234,50"))
    }

    @Test fun `centesimi in testo`() {
        assertEquals("12,50", centsToInput(1250))
        assertEquals("-3,05", centsToInput(-305))
        assertEquals("0,00", centsToInput(0))
    }
}
