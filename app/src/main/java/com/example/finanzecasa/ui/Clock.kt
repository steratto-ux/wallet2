package com.example.finanzecasa.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Emette l'istante corrente arrotondato al minuto. Serve a far entrare nei conti le transazioni future
 * quando arriva il loro momento e a spostare l'indicatore "Oggi" dei budget.
 */
fun minuteClock(): Flow<LocalDateTime> = flow {
    while (true) {
        emit(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
        delay(15_000)
    }
}.distinctUntilChanged()
