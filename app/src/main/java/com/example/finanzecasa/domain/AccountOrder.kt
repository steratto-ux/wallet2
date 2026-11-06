package com.example.finanzecasa.domain

/** Riordino degli account: sposta un elemento su o giù di [delta] posizioni. */
object AccountOrder {
    fun move(ids: List<Long>, id: Long, delta: Int): List<Long> {
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val to = (from + delta).coerceIn(0, ids.size - 1)
        if (from == to) return ids
        val result = ids.toMutableList()
        result.removeAt(from)
        result.add(to, id)
        return result
    }
}
