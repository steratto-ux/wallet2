package com.example.finanzecasa.importer

import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.data.ExportRow
import com.example.finanzecasa.data.TxKind
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Esporta i dati con lo stesso tracciato del file completo di Cashew, quindi il file si può reimportare
 * con la funzione di import (backup e ripristino).
 *
 *  - I trasferimenti sono due righe (una per conto) con categoria "Correzione saldo" e nota "Saldo trasferito A → B",
 *    esattamente come nel file di Cashew: l'import le fonde di nuovo in un solo trasferimento.
 *  - Il CSV non ha un campo per il valore iniziale dei conti: viene scritto come una riga "Saldo iniziale"
 *    (nota [INITIAL_NOTE]) che l'import riconosce e rimette come valore iniziale del conto, senza creare transazioni.
 */
object CashewCsvExporter {
    const val INITIAL_TITLE = "Saldo iniziale"
    const val INITIAL_NOTE = "Saldo iniziale (backup)"
    const val TRANSFER_CATEGORY = "Correzione saldo"

    const val HEADER = "account,amount,amount unpaid,currency,title,note,date,income,type," +
        "category name,subcategory name,color,icon,emoji,budget,objective,extra"

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun build(
        rows: List<ExportRow>,
        accounts: List<AccountEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        // Righe dei valori iniziali: datate un minuto prima della prima transazione del conto.
        val initialRows = accounts.filter { it.initialBalanceCents != 0L }.map { a ->
            val first = rows.filter { it.accountName == a.name }.minOfOrNull { it.dateMillis }
            ExportRow(
                accountName = a.name, amountCents = a.initialBalanceCents, title = INITIAL_TITLE, note = INITIAL_NOTE,
                dateMillis = (first ?: nowMillis) - 60_000, kind = TxKind.NORMAL,
                categoryName = null, parentCategoryName = null
            )
        }

        val sb = StringBuilder(HEADER).append('\n')
        for (r in rows + initialRows) {
            val transfer = r.kind == TxKind.TRANSFER
            val category = if (transfer) TRANSFER_CATEGORY else r.parentCategoryName ?: r.categoryName.orEmpty()
            val subcategory = if (transfer) "" else if (r.parentCategoryName != null) r.categoryName.orEmpty() else ""
            val fields = listOf(
                r.accountName,
                BigDecimal.valueOf(r.amountCents, 2).toPlainString(),
                "",
                "EUR",
                r.title,
                r.note,
                Instant.ofEpochMilli(r.dateMillis).atZone(zone).toLocalDateTime().format(DATE_FMT),
                (r.amountCents > 0).toString(),
                "default",
                category,
                subcategory,
                if (transfer) "0XFF607D8B" else "",
                if (transfer) "charts" else "",
                "", "", "", ""
            )
            sb.append(fields.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    /** Racchiude tra virgolette i campi con virgole, virgolette o a capo (le virgolette interne si raddoppiano). */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field
}

class ExportRepository(private val db: AppDatabase) {
    suspend fun buildCsv(): String {
        val dao = db.dao()
        return CashewCsvExporter.build(dao.exportRows(), dao.allAccounts())
    }
}
