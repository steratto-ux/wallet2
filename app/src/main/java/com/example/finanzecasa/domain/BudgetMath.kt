package com.example.finanzecasa.domain

import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.roundToInt

enum class BudgetState { ON_TRACK, AHEAD, OVER }

data class BudgetStatus(
    val percent: Int,            // percentuale spesa, arrotondata (anche oltre 100)
    val fillFraction: Float,     // 0..1, lunghezza della barra riempita
    val todayFraction: Float,    // 0..1, posizione dell'indicatore "Oggi" nel mese
    val remainingCents: Long,    // può essere negativo
    val daysLeft: Int,           // giorni rimasti nel mese, oggi compreso
    val perDayCents: Long,       // quanto si può spendere al giorno (0 se il budget è finito)
    val state: BudgetState
)

object BudgetMath {
    fun status(amountCents: Long, spentCents: Long, now: LocalDateTime): BudgetStatus {
        val daysInMonth = YearMonth.from(now).lengthOfMonth()
        val day = now.dayOfMonth
        val minutesOfDay = now.hour * 60 + now.minute
        val todayFraction = ((day - 1) + minutesOfDay / 1440.0) / daysInMonth
        val daysLeft = daysInMonth - day + 1

        val remaining = amountCents - spentCents
        val spentFraction = if (amountCents > 0) spentCents.toDouble() / amountCents else 0.0
        val perDay = if (remaining > 0) (remaining + daysLeft / 2) / daysLeft else 0L

        val state = when {
            spentCents > amountCents -> BudgetState.OVER
            spentFraction > todayFraction -> BudgetState.AHEAD
            else -> BudgetState.ON_TRACK
        }
        return BudgetStatus(
            percent = (spentFraction * 100).roundToInt(),
            fillFraction = spentFraction.coerceIn(0.0, 1.0).toFloat(),
            todayFraction = todayFraction.toFloat(),
            remainingCents = remaining,
            daysLeft = daysLeft,
            perDayCents = perDay,
            state = state
        )
    }
}
