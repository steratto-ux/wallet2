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

private val moneyWhole: NumberFormat = NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}

/** Come [formatMoney], ma senza decimali quando l'importo è tondo ("400 €"). */
fun formatMoneyShort(cents: Long): String =
    if (cents % 100 == 0L) moneyWhole.format(cents / 100) else formatMoney(cents)

/** "1 nov", "30 nov". */
fun formatDayMonth(date: java.time.LocalDate): String = date.format(dayFmt)
