package com.example.finanzecasa.domain

import com.example.finanzecasa.data.MonthTotal
import java.time.YearMonth

/** Fondo di emergenza: quanti mesi di spese si hanno da parte. Logica pura. */
object EmergencyFund {
    /**
     * Media mensile delle uscite sui mesi già chiusi (fino a [maxMonths], dal più recente) che hanno almeno
     * una spesa. Restituisce null se non c'è nessun mese chiuso con dati.
     */
    fun averageMonthly(months: List<MonthTotal>, current: YearMonth, maxMonths: Int = 6): Long? {
        val closed = months.filter { it.month < current.toString() && it.totalCents > 0 }
            .sortedByDescending { it.month }
            .take(maxMonths)
        if (closed.isEmpty()) return null
        return closed.sumOf { it.totalCents } / closed.size
    }

    fun target(averageMonthlyCents: Long, months: Int): Long = averageMonthlyCents * months

    /** Quanti mesi di spese copre la somma messa da parte (0 se la media non è nota). */
    fun monthsCovered(savedCents: Long, averageMonthlyCents: Long): Double =
        if (averageMonthlyCents <= 0) 0.0 else savedCents.toDouble() / averageMonthlyCents

    /** Percentuale raggiunta, tra 0 e 100. */
    fun percent(savedCents: Long, targetCents: Long): Int =
        if (targetCents <= 0) 0 else (savedCents * 100 / targetCents).coerceIn(0, 100).toInt()
}
