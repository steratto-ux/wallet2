package com.example.finanzecasa.importer

import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.ExportRow
import com.example.finanzecasa.data.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CashewCsvExporterTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun millis(iso: String) = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    private val transferTime = millis("2026-11-03T17:51:30")
    private val rows = listOf(
        ExportRow("WeBank", -2474, "Benzinaio", "", millis("2026-11-02T21:06:38"), TxKind.NORMAL, "Carburanti", "Trasporti e biglietti"),
        ExportRow("Portafoglio", -1000, "Pranzo, \"da Mario\"", "riga1\nriga2", millis("2026-11-03T12:00:00"), TxKind.NORMAL, "Pranzi/Cene", null),
        ExportRow("WeBank", -5000, "Bancomat", "Saldo trasferito\nWeBank → Portafoglio", transferTime, TxKind.TRANSFER, null, null),
        ExportRow("Portafoglio", 5000, "Bancomat", "Saldo trasferito\nWeBank → Portafoglio", transferTime, TxKind.TRANSFER, null, null)
    )
    private val accounts = listOf(
        AccountEntity(id = 1, name = "WeBank", type = AccountType.BANK, initialBalanceCents = 100_000),
        AccountEntity(id = 2, name = "Portafoglio", type = AccountType.CASH)
    )

    @Test fun `ogni riga ha le stesse 17 colonne dell'intestazione di Cashew`() {
        val csv = CashewCsvExporter.build(rows, accounts, zone, nowMillis = 0)
        val parsed = CsvParser.parse(csv)
        assertTrue(parsed.all { it.size == 17 })
        assertEquals("account", parsed[0][0])
        assertEquals("subcategory name", parsed[0][10])
    }

    @Test fun `il file esportato si reimporta con lo stesso contenuto`() {
        val csv = CashewCsvExporter.build(rows, accounts, zone, nowMillis = 0)
        val result = CashewCsvParser.parse(csv)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.mergedTransferLegs) // le due righe del trasferimento tornano un solo trasferimento

        val txs = result.items.filterIsInstance<ImportedTx>()
        val fuel = txs.first { it.title == "Benzinaio" }
        assertEquals(-2474L, fuel.amountCents)
        assertEquals("Trasporti e biglietti", fuel.category)
        assertEquals("Carburanti", fuel.subcategory)
        assertEquals(LocalDateTime.of(2026, 11, 2, 21, 6, 38), fuel.date)

        val lunch = txs.first { it.title.startsWith("Pranzo") }
        assertEquals("Pranzo, \"da Mario\"", lunch.title)
        assertEquals("riga1\nriga2", lunch.note)
        assertEquals("Pranzi/Cene", lunch.category)

        val transfer = result.items.filterIsInstance<ImportedTransfer>().single()
        assertEquals("WeBank", transfer.fromAccount)
        assertEquals("Portafoglio", transfer.toAccount)
        assertEquals(5000L, transfer.amountCents)
    }

    @Test fun `il valore iniziale del conto diventa una riga riconoscibile`() {
        val csv = CashewCsvExporter.build(rows, accounts, zone, nowMillis = 0)
        val initial = CashewCsvParser.parse(csv).items.filterIsInstance<ImportedTx>()
            .filter { it.note == CashewCsvExporter.INITIAL_NOTE }
        assertEquals(1, initial.size)           // solo WeBank ha un valore iniziale diverso da zero
        assertEquals("WeBank", initial[0].account)
        assertEquals(100_000L, initial[0].amountCents)
    }

    @Test fun `escape dei campi`() {
        assertEquals("semplice", CashewCsvExporter.escape("semplice"))
        assertEquals("\"a,b\"", CashewCsvExporter.escape("a,b"))
        assertEquals("\"di \"\"x\"\"\"", CashewCsvExporter.escape("di \"x\""))
        assertEquals("\"1\n2\"", CashewCsvExporter.escape("1\n2"))
    }
}
