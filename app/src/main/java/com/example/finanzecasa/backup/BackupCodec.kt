package com.example.finanzecasa.backup

import org.json.JSONArray
import org.json.JSONObject

/** Una tabella del database: nome e righe (colonna -> valore Long, Double, String o null). */
class TableDump(val name: String, val rows: List<Map<String, Any?>>)

class BackupContent(val format: Int, val dbVersion: Int, val createdAt: String, val tables: List<TableDump>)

/**
 * Formato JSON del backup. È volutamente generico (tabella -> righe con nome colonna): una colonna o una tabella
 * aggiunta in futuro entra nel backup senza toccare questo codice, e un backup vecchio resta leggibile.
 */
object BackupCodec {
    const val FORMAT = 1

    fun encode(dbVersion: Int, createdAt: String, tables: List<TableDump>): String {
        val tablesJson = JSONObject()
        for (t in tables) {
            val rows = JSONArray()
            for (row in t.rows) {
                val o = JSONObject()
                for ((k, v) in row) o.put(k, v ?: JSONObject.NULL)
                rows.put(o)
            }
            tablesJson.put(t.name, rows)
        }
        return JSONObject()
            .put("app", "Il mio wallet")
            .put("format", FORMAT)
            .put("dbVersion", dbVersion)
            .put("createdAt", createdAt)
            .put("tables", tablesJson)
            .toString()
    }

    fun decode(json: String): BackupContent {
        val root = JSONObject(json)
        val tablesJson = root.getJSONObject("tables")
        val tables = mutableListOf<TableDump>()
        for (name in tablesJson.keys()) {
            val arr = tablesJson.getJSONArray(name)
            val rows = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val map = LinkedHashMap<String, Any?>()
                for (k in o.keys()) map[k] = normalize(o.get(k))
                map
            }
            tables += TableDump(name, rows)
        }
        return BackupContent(
            format = root.optInt("format", 1),
            dbVersion = root.optInt("dbVersion", 0),
            createdAt = root.optString("createdAt", ""),
            tables = tables
        )
    }

    private fun normalize(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is Boolean -> if (v) 1L else 0L
        is Int, is Long, is Short, is Byte -> (v as Number).toLong()
        is Number -> v.toDouble() // Double, Float o BigDecimal (a seconda dell'implementazione di org.json)
        else -> v.toString()
    }

    /**
     * Valore da usare per una colonna NOT NULL senza default che il backup (di una versione più vecchia)
     * non contiene: stringa vuota per il testo, zero per il resto.
     */
    fun fallbackFor(declaredType: String): Any =
        if (declaredType.uppercase().contains("CHAR") || declaredType.uppercase().contains("TEXT")) "" else 0L
}
