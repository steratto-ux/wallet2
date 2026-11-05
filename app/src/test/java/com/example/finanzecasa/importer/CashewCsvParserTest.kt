package com.example.finanzecasa.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashewCsvParserTest {
    private val full = """account,amount,amount unpaid,currency,title,note,date,income,type,category name,subcategory name,color,icon,emoji,budget,objective,extra
Portafoglio,-10.0,,EUR,Rebora,,2026-11-05 08:53:30.000,false,default,Cibo/Bevande e Super,,,picnic,,,,repeat every 1 month
WeBank,-638.33,,EUR,Saldo carta,"Saldo trasferito
WeBank → Amex",2026-10-24 10:00:01.000,false,default,Correzione saldo,,,,,,,
Amex,638.33,,EUR,Saldo carta,"Saldo trasferito
WeBank → Amex",2026-10-24 10:00:00.000,true,default,Correzione saldo,,,,,,,
Revolut,19.999999999999996,,EUR,Trasferimento,"Saldo trasferito
WeBank → Revolut",2026-10-27 09:00:01.000,true,default,Correzione saldo,,,,,,,
WeBank,-20.0,,EUR,Trasferimento,"Saldo trasferito
WeBank → Revolut",2026-10-27 09:00:00.000,false,default,Correzione saldo,,,,,,,
WeBank,-360.49,,EUR,Mutuo,Mutuo Torrazza,2026-10-30 08:00:00.000,false,repetitive,Gestione famiglia,Mutuo,,,,,,repeat every 1 month
"""

    @Test fun `formato completo - fonde le due gambe dei trasferimenti`() {
        val r = CashewCsvParser.parse(full)
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(4, r.items.size)          // 2 transazioni + 2 trasferimenti
        assertEquals(2, r.mergedTransferLegs)

        val transfers = r.items.filterIsInstance<ImportedTransfer>()
        assertEquals("WeBank", transfers[0].fromAccount)
        assertEquals("Amex", transfers[0].toAccount)
        assertEquals(63833L, transfers[0].amountCents)
        assertEquals(2000L, transfers[1].amountCents) // 19.999999999999996 arrotondato
    }

    @Test fun `formato completo - categoria, sotto-categoria e ricorrenza`() {
        val mutuo = CashewCsvParser.parse(full).items.filterIsInstance<ImportedTx>().last()
        assertEquals(-36049L, mutuo.amountCents)
        assertEquals("Gestione famiglia", mutuo.category)
        assertEquals("Mutuo", mutuo.subcategory)
        assertEquals("repetitive", mutuo.recurringType)
        assertEquals("repeat every 1 month", mutuo.recurrence)
    }

    @Test fun `la ricorrenza delle istanze default viene ignorata`() {
        val rebora = CashewCsvParser.parse(full).items.filterIsInstance<ImportedTx>().first()
        assertNull(rebora.recurringType)
        assertNull(rebora.recurrence)
    }

    @Test fun `formato modello con microsecondi e account vuoto`() {
        val csv = "Date,Amount,Category,Title,Note,Account\r\n" +
            "2026-11-05 09:05:05.945053,-50,Groceries,Fruits and Vegetables,Paid with cash,\r\n" +
            "2026-11-05 09:05:05.945057,250,Bills & Fees,Monthly Income,,"
        val items = CashewCsvParser.parse(csv).items.filterIsInstance<ImportedTx>()
        assertEquals(2, items.size)
        assertEquals(-5000L, items[0].amountCents)
        assertNull(items[0].account)
        assertEquals("Groceries", items[0].category)
        assertEquals(25000L, items[1].amountCents)
    }
}
