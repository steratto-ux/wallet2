package com.example.finanzecasa.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val money: NumberFormat = NumberFormat.getCurrencyInstance(Locale.ITALY)
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

fun formatMoney(cents: Long): String = money.format(BigDecimal.valueOf(cents, 2))

fun formatDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

fun formatDate(date: java.time.LocalDate): String = date.format(dateFmt)
