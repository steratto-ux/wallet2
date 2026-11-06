package com.example.finanzecasa.domain

/** Etichette (tag) delle transazioni: memorizzate come testo separato da virgole. */
object Tags {
    /** "Vacanza, #regalo ,vacanza" -> "Vacanza,regalo" (senza #, senza doppioni, senza spazi). */
    fun normalize(input: String): String {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (raw in input.split(',', ';', '\n')) {
            val tag = raw.trim().trimStart('#').trim()
            if (tag.isNotEmpty() && seen.add(tag.lowercase())) result += tag
        }
        return result.joinToString(",")
    }

    fun split(stored: String): List<String> = if (stored.isBlank()) emptyList() else stored.split(',').filter { it.isNotBlank() }

    /** Tutti i tag usati, senza doppioni (ignora le maiuscole), in ordine alfabetico. */
    fun distinct(columns: List<String>): List<String> {
        val byLower = linkedMapOf<String, String>()
        columns.flatMap { split(it) }.forEach { byLower.putIfAbsent(it.lowercase(), it) }
        return byLower.values.sortedBy { it.lowercase() }
    }

    /** Aggiunge [tag] al testo in modifica, se non c'è già. */
    fun add(current: String, tag: String): String = normalize("$current,$tag")
}
