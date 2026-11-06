package com.example.finanzecasa.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.finanzecasa.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Cartella (nella memoria privata dell'app) con le foto degli scontrini. */
fun receiptsDir(context: Context): File = File(context.filesDir, "receipts").also { it.mkdirs() }

class RestoreResult(val rowCount: Int, val receiptCount: Int, val createdAt: String)

/**
 * Backup completo: un unico file .zip con tutte le tabelle del database (data.json) e le foto degli scontrini.
 * Il ripristino sostituisce tutti i dati attuali con quelli del file, in un'unica transazione: se qualcosa
 * va storto non cambia nulla.
 */
class BackupRepository(private val context: Context, private val db: AppDatabase) {
    private val dbVersion get() = db.openHelper.readableDatabase.version

    private fun tableNames(sdb: SupportSQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        sdb.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%' ORDER BY name"
        ).use { c -> while (c.moveToNext()) names += c.getString(0) }
        return names
    }

    /** Le tabelle con chiavi esterne vanno inserite dopo i conti e svuotate prima. */
    private fun insertOrder(names: List<String>): List<String> =
        names.sortedBy { when (it) { "accounts" -> 0; "transactions" -> 2; else -> 1 } }

    private fun readValue(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        else -> null
    }

    suspend fun writeBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        val sdb = db.openHelper.readableDatabase
        val tables = insertOrder(tableNames(sdb)).map { name ->
            val rows = mutableListOf<Map<String, Any?>>()
            sdb.query("SELECT * FROM `$name`").use { c ->
                while (c.moveToNext()) {
                    val row = LinkedHashMap<String, Any?>()
                    for (i in 0 until c.columnCount) row[c.getColumnName(i)] = readValue(c, i)
                    rows += row
                }
            }
            TableDump(name, rows)
        }
        val json = BackupCodec.encode(sdb.version, LocalDateTime.now().toString(), tables)

        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry("data.json"))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        receiptsDir(context).listFiles()?.filter { it.isFile }?.forEach { f ->
            zip.putNextEntry(ZipEntry("receipts/${f.name}"))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /** Legge il file, controlla che sia valido e lo applica. Lancia IllegalArgumentException con un messaggio chiaro. */
    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        var json: String? = null
        val staging = File(context.filesDir, "receipts_restore").also { it.deleteRecursively(); it.mkdirs() }
        var receipts = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == "data.json") {
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        json = buffer.toString("UTF-8")
                    } else if (entry.name.startsWith("receipts/")) {
                        val safeName = File(entry.name).name // niente percorsi: solo il nome del file
                        if (safeName.isNotBlank()) {
                            File(staging, safeName).outputStream().use { zip.copyTo(it) }
                            receipts++
                        }
                    }
                }
            }
            val text = json ?: throw IllegalArgumentException("Il file non è un backup di Il mio wallet.")
            val content = try {
                BackupCodec.decode(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("Il file di backup è danneggiato.")
            }
            val current = dbVersion
            if (content.dbVersion > current) {
                throw IllegalArgumentException(
                    "Il backup è stato creato da una versione più recente dell'app. Aggiorna l'app e riprova."
                )
            }
            val rowCount = applyToDatabase(content)

            // Database a posto: ora sostituisco anche le foto.
            val dir = receiptsDir(context)
            dir.listFiles()?.forEach { it.delete() }
            staging.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
            RestoreResult(rowCount, receipts, content.createdAt)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun applyToDatabase(content: BackupContent): Int {
        val sdb = db.openHelper.writableDatabase
        var inserted = 0
        db.runInTransaction(Runnable {
            val current = tableNames(sdb)
            val order = insertOrder(current)
            for (name in order.reversed()) sdb.execSQL("DELETE FROM `$name`")

            val byName = content.tables.associateBy { it.name }
            for (name in order) {
                val dump = byName[name] ?: continue
                val columns = columnsOf(sdb, name)
                for (row in dump.rows) {
                    val values = ContentValues()
                    for (col in columns) {
                        if (row.containsKey(col.name)) {
                            putValue(values, col.name, row[col.name])
                        } else if (col.notNull && col.defaultValue == null) {
                            putValue(values, col.name, BackupCodec.fallbackFor(col.type))
                        }
                    }
                    sdb.insert(name, SQLiteDatabase.CONFLICT_REPLACE, values)
                    inserted++
                }
            }
        })
        return inserted
    }

    private class Column(val name: String, val type: String, val notNull: Boolean, val defaultValue: String?)

    private fun columnsOf(sdb: SupportSQLiteDatabase, table: String): List<Column> {
        val result = mutableListOf<Column>()
        sdb.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val typeIdx = c.getColumnIndexOrThrow("type")
            val notNullIdx = c.getColumnIndexOrThrow("notnull")
            val defIdx = c.getColumnIndexOrThrow("dflt_value")
            while (c.moveToNext()) {
                result += Column(
                    c.getString(nameIdx), c.getString(typeIdx) ?: "",
                    c.getInt(notNullIdx) == 1, if (c.isNull(defIdx)) null else c.getString(defIdx)
                )
            }
        }
        return result
    }

    private fun putValue(values: ContentValues, key: String, v: Any?) {
        when (v) {
            null -> values.putNull(key)
            is Long -> values.put(key, v)
            is Double -> values.put(key, v)
            is Int -> values.put(key, v.toLong())
            else -> values.put(key, v.toString())
        }
    }
}
