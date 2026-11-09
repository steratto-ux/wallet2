package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Calcoli degli obiettivi di risparmio. */
object GoalMath {
    /** Avanzamento da 0 a 1 (oltre l'obiettivo resta 1). */
    fun fraction(savedCents: Long, targetCents: Long): Float =
        if (targetCents <= 0) 0f else (savedCents.toDouble() / targetCents).coerceIn(0.0, 1.0).toFloat()

    fun percent(savedCents: Long, targetCents: Long): Int =
        if (targetCents <= 0) 0 else (savedCents * 100.0 / targetCents).roundToInt().coerceAtLeast(0)

    fun remainingCents(savedCents: Long, targetCents: Long): Long = (targetCents - savedCents).coerceAtLeast(0)

    /**
     * Quanto mettere da parte ogni mese per arrivare all'obiettivo entro la scadenza.
     * Null se l'obiettivo è già raggiunto o la scadenza è passata.
     */
    fun monthlyNeededCents(savedCents: Long, targetCents: Long, today: LocalDate, deadline: LocalDate): Long? {
        val remaining = remainingCents(savedCents, targetCents)
        if (remaining == 0L) return null
        val days = ChronoUnit.DAYS.between(today, deadline)
        if (days <= 0) return null
        val months = maxOf(1, ceil(days / 30.4375).toInt())
        return ceil(remaining.toDouble() / months).toLong()
    }
}
