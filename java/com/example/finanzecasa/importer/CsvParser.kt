package com.example.finanzecasa.importer

/** Parser CSV (RFC 4180): campi tra virgolette, "" come escape, a capo dentro le note, CRLF/LF, BOM. */
object CsvParser {
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false

        fun endRow() {
            row.add(field.toString())
            field.setLength(0)
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row) // salta righe vuote
            row = mutableListOf()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
