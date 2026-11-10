package com.example.finanzecasa.domain

import java.time.Duration
import java.time.LocalDateTime

/** Quando mandare il promemoria serale. Logica pura. */
object ReminderLogic {
    /**
     * Si ricorda solo se il promemoria è attivo, l'ora scelta è passata, oggi non è stato inserito nulla
     * e oggi non è già partito un promemoria.
     */
    fun shouldRemind(
        enabled: Boolean, hour: Int, now: LocalDateTime,
        lastEntryEpochDay: Long, lastReminderEpochDay: Long
    ): Boolean {
        val today = now.toLocalDate().toEpochDay()
        return enabled && now.hour >= hour && lastEntryEpochDay != today && lastReminderEpochDay != today
    }

    /** Minuti da aspettare fino alla prossima volta che l'orologio segna [hour]:00 (almeno 1). */
    fun minutesUntilNext(now: LocalDateTime, hour: Int): Long {
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return maxOf(1L, Duration.between(now, next).toMinutes())
    }
}
