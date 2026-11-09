package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

object DateUtil {
    /** Giorno del mese limitato alla lunghezza del mese (31 -> 28 a febbraio). */
    fun clamp(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))
}

fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Logica pura (senza Android) dei periodi di una carta di credito.
 * Esempio: inizio periodo = 12, addebito = 24.
 * Il 24 febbraio si addebita il periodo chiuso più recente: 12 gennaio - 11 febbraio.
 */
object CreditCardCycle {

    /** Ultimo giorno di inizio periodo che cade nella data indicata o prima. */
    fun cycleStartOnOrBefore(date: LocalDate, startDay: Int): LocalDate {
        val thisMonth = DateUtil.clamp(YearMonth.from(date), startDay)
        return if (!thisMonth.isAfter(date)) thisMonth
        else DateUtil.clamp(YearMonth.from(date).minusMonths(1), startDay)
    }

    /** Periodo (inizio, fine inclusi) già chiuso alla data di addebito. */
    fun lastClosedPeriod(debitDate: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val currentStart = cycleStartOnOrBefore(debitDate, startDay)
        val end = currentStart.minusDays(1)
        val start = cycleStartOnOrBefore(end, startDay)
        return start to end
    }

    /** Date di addebito comprese in (afterExclusive, toInclusive]. */
    fun debitDatesIn(afterExclusive: LocalDate, toInclusive: LocalDate, debitDay: Int): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var ym = YearMonth.from(afterExclusive)
        val last = YearMonth.from(toInclusive)
        while (!ym.isAfter(last)) {
            val d = DateUtil.clamp(ym, debitDay)
            if (d.isAfter(afterExclusive) && !d.isAfter(toInclusive)) result += d
            ym = ym.plusMonths(1)
        }
        return result
    }
}
