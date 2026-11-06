package com.example.finanzecasa.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val tables = listOf(
        TableDump(
            "accounts",
            listOf(
                mapOf("id" to 1L, "name" to "WeBank", "initialBalanceCents" to 123_456_789_012L, "colorKey" to null),
                mapOf("id" to 2L, "name" to "Portafoglio \"contanti\"\nriga 2", "initialBalanceCents" to -5000L, "colorKey" to "blue")
            )
        ),
        TableDump(
            "loans",
            listOf(mapOf("id" to 1L, "annualRatePercent" to 5.25, "name" to "Mutuo à è ì €"))
        ),
        TableDump("budgets", emptyList())
    )

    @Test fun roundTripKeepsValuesAndTypes() {
        val json = BackupCodec.encode(4, "2026-11-06T10:00", tables)
        val back = BackupCodec.decode(json)

        assertEquals(BackupCodec.FORMAT, back.format)
        assertEquals(4, back.dbVersion)
        assertEquals("2026-11-06T10:00", back.createdAt)
        assertEquals(setOf("accounts", "loans", "budgets"), back.tables.map { it.name }.toSet())

        val accounts = back.tables.first { it.name == "accounts" }.rows
        assertEquals(2, accounts.size)
        assertEquals(123_456_789_012L, accounts[0]["initialBalanceCents"])   // oltre i 32 bit: resta Long
        assertNull(accounts[0]["colorKey"])
        assertEquals("Portafoglio \"contanti\"\nriga 2", accounts[1]["name"])
        assertEquals(-5000L, accounts[1]["initialBalanceCents"])

        val loan = back.tables.first { it.name == "loans" }.rows.single()
        assertEquals(5.25, loan["annualRatePercent"] as Double, 0.0)
        assertEquals("Mutuo à è ì €", loan["name"])

        assertTrue(back.tables.first { it.name == "budgets" }.rows.isEmpty())
    }

    @Test fun smallIntegersComeBackAsLong() {
        val back = BackupCodec.decode(BackupCodec.encode(4, "x", listOf(TableDump("t", listOf(mapOf("n" to 7L))))))
        assertEquals(7L, back.tables.single().rows.single()["n"])
    }

    @Test fun oldBackupWithoutOptionalFieldsStillDecodes() {
        val json = """{"tables":{"accounts":[{"id":1,"name":"A"}]}}"""
        val back = BackupCodec.decode(json)
        assertEquals(0, back.dbVersion)
        assertEquals(1L, back.tables.single().rows.single()["id"])
    }

    @Test(expected = Exception::class) fun garbageIsRejected() {
        BackupCodec.decode("questo non è un backup")
    }

    @Test fun fallbackValuesFollowTheColumnType() {
        assertEquals("", BackupCodec.fallbackFor("TEXT"))
        assertEquals(0L, BackupCodec.fallbackFor("INTEGER"))
        assertEquals(0L, BackupCodec.fallbackFor("REAL"))
    }
}
