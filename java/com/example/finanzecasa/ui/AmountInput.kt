package com.example.finanzecasa.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** Converte "12,50", "12.50", "1.234,50", "-5" in centesimi. Null se non è un numero. */
fun parseSignedCents(input: String): Long? {
    var s = input.trim().replace(" ", "")
    if (s.isEmpty()) return null
    s = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    val value = s.toBigDecimalOrNull() ?: return null
    return value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** Come [parseSignedCents] ma accetta solo importi maggiori di zero. */
fun parseAmountToCents(input: String): Long? = parseSignedCents(input)?.takeIf { it > 0 }

/** Centesimi -> testo modificabile ("-12,50"). */
fun centsToInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
