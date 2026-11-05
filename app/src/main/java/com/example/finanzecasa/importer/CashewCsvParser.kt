package com.example.finanzecasa.importer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.math.abs

sealed interface ImportItem

data class ImportedTx(
    val account: String?,            // null = conto non indicato nel file
    val amountCents: Long,
    val title: String,
    val note: String,
    val date: LocalDateTime,
    val category: String?,
    val subcategory: String?,
    val recurringType: String?,      // "repetitive" / "upcoming" (modelli ricorrenti di Cashew)
    val recurrence: String?          // es. "repeat every 1 month"
) : ImportItem

data class ImportedTransfer(
    val fromAccount: String,
    val toAccount: String,
    val amountCents: Long,           // sempre positivo
    val title: String,
    val date: LocalDateTime
) : ImportItem

data class ParseResult(
    val items: List<ImportItem>,
    val mergedTransferLegs: Int,
    val errors: List<String>
)

/**
 * Supporta due formati Cashew, riconosciuti dalle intestazioni:
 *  - modello:  Date, Amount, Category, Title, Note, Account
 *  - completo: account, amount, ..., date, income, type, category name, subcategory name, ..., extra
 * I trasferimenti tra conti (categoria "Correzione saldo", nota "Saldo trasferito A → B") compaiono nel file
 * come due righe: vengono fuse in un solo trasferimento.
 */
object CashewCsvParser {
    private val TRANSFER = Regex("""Saldo trasferito\s*(.+?)\s*→\s*(.+?)\s*$""")

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .toFormatter()

    fun amountToCents(raw: String): Long? {
        val bd = raw.trim().toBigDecimalOrNull() ?: return null
        return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun parseDate(raw: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(raw.trim(), DATE_FMT)
    }.getOrElse { runCatching { LocalDate.parse(raw.trim()).atStartOfDay() }.getOrNull() }

    fun parse(text: String): ParseResult {
        val rows = CsvParser.parse(text)
        if (rows.isEmpty()) return ParseResult(emptyList(), 0, listOf("File vuoto"))

        val header = rows[0].map { it.trim().lowercase() }
        fun idx(vararg names: String): Int? =
            names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }

        val iDate = idx("date")
        val iAmount = idx("amount")
        if (iDate == null || iAmount == null) {
            return ParseResult(emptyList(), 0, listOf("Intestazioni non riconosciute: servono almeno Date e Amount"))
        }
        val iAccount = idx("account")
        val iTitle = idx("title")
        val iNote = idx("note")
        val iCat = idx("category name", "category")
        val iSub = idx("subcategory name")
        val iType = idx("type")
        val iExtra = idx("extra")

        val items = mutableListOf<ImportItem>()
        val errors = mutableListOf<String>()
        val seen = mutableMapOf<Triple<String, String, Long>, MutableList<LocalDateTime>>()
        var merged = 0

        for ((n, row) in rows.drop(1).withIndex()) {
            fun col(i: Int?): String = i?.let { row.getOrNull(it) }?.trim().orEmpty()
            val line = n + 2

            val cents = amountToCents(col(iAmount))
            val date = parseDate(col(iDate))
            if (cents == null || date == null) {
                errors += "Riga $line: importo o data non validi"
                continue
            }
            val title = col(iTitle)
            val note = col(iNote)

            val m = TRANSFER.find(note)
            if (m != null) {
                val from = m.groupValues[1].trim()
                val to = m.groupValues[2].trim()
                val abs = abs(cents)
                val dates = seen.getOrPut(Triple(from, to, abs)) { mutableListOf() }
                if (dates.any { Duration.between(it, date).abs().seconds <= 5 }) {
                    merged++ // seconda gamba dello stesso trasferimento
                } else {
                    dates += date
                    items += ImportedTransfer(from, to, abs, title, date)
                }
                continue
            }

            val type = col(iType).lowercase()
            val recurring = type == "repetitive" || type == "upcoming"
            items += ImportedTx(
                account = col(iAccount).ifBlank { null },
                amountCents = cents,
                title = title,
                note = note,
                date = date,
                category = col(iCat).ifBlank { null },
                subcategory = col(iSub).ifBlank { null },
                recurringType = if (recurring) type else null,
                recurrence = if (recurring) col(iExtra).ifBlank { null } else null
            )
        }
        return ParseResult(items, merged, errors)
    }
}
