package com.example.finanzecasa.domain

import java.time.YearMonth

/** Intervalli del grafico entrate/uscite: gli ultimi 3, 6 o 12 mesi, oppure un anno solare (gennaio–dicembre). */
enum class CashflowSpan(val label: String, val months: Int) {
    THREE_MONTHS("3 mesi", 3),
    SIX_MONTHS("6 mesi", 6),
    TWELVE_MONTHS("12 mesi", 12),
    CALENDAR_YEAR("Anno", 12)
}

/**
 * Finestra mostrata dal grafico. Per 3/6/12 mesi [anchor] è l'ultimo mese della finestra; per "Anno" conta solo
 * l'anno di [anchor]. Le frecce spostano la finestra della sua stessa durata (3, 6, 12 mesi o un anno).
 */
data class CashflowView(val span: CashflowSpan, val anchor: YearMonth) {

    val months: List<YearMonth>
        get() = if (span == CashflowSpan.CALENDAR_YEAR) {
            (1..12).map { YearMonth.of(anchor.year, it) }
        } else {
            (0 until span.months).map { anchor.minusMonths((span.months - 1 - it).toLong()) }
        }

    /** Si può andare avanti solo finché la finestra non contiene già il mese in corso. */
    fun canGoForward(current: YearMonth): Boolean =
        if (span == CashflowSpan.CALENDAR_YEAR) anchor.year < current.year else anchor.isBefore(current)

    fun shift(delta: Int, current: YearMonth): CashflowView {
        val next = if (span == CashflowSpan.CALENDAR_YEAR) anchor.plusYears(delta.toLong())
        else anchor.plusMonths(delta.toLong() * span.months)
        if (delta > 0 && !canGoForward(current)) return this
        val capped = when {
            span == CashflowSpan.CALENDAR_YEAR -> if (next.year > current.year) anchor else next
            next.isAfter(current) -> current
            else -> next
        }
        return copy(anchor = capped)
    }

    /** Cambia durata mantenendo, per quanto possibile, il periodo che si stava guardando. */
    fun withSpan(newSpan: CashflowSpan, current: YearMonth): CashflowView = when {
        newSpan == span -> this
        newSpan == CashflowSpan.CALENDAR_YEAR -> CashflowView(newSpan, YearMonth.of(anchor.year, 12))
        else -> CashflowView(newSpan, if (anchor.isAfter(current)) current else anchor)
    }.let { if (it.span == CashflowSpan.CALENDAR_YEAR && it.anchor.year > current.year) it.copy(anchor = YearMonth.of(current.year, 12)) else it }

    fun label(): String {
        val first = months.first()
        val last = months.last()
        return when {
            span == CashflowSpan.CALENDAR_YEAR -> anchor.year.toString()
            first.year == last.year -> "${CashflowSeries.shortMonth(first)} – ${CashflowSeries.shortMonth(last)} ${last.year}"
            else -> "${CashflowSeries.shortMonth(first)} ${first.year} – ${CashflowSeries.shortMonth(last)} ${last.year}"
        }
    }

    companion object {
        /** Vista iniziale: l'anno in corso. */
        fun initial(current: YearMonth) = CashflowView(CashflowSpan.CALENDAR_YEAR, YearMonth.of(current.year, 12))
    }
}

/** Entrate e uscite di un mese. I mesi futuri ([future]) non hanno dati e non vengono disegnati. */
data class CashflowPoint(val month: YearMonth, val incomeCents: Long, val expenseCents: Long, val future: Boolean) {
    val netCents: Long get() = incomeCents - expenseCents
}

object CashflowSeries {
    private val SHORT = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    private val LONG = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
    )

    fun shortMonth(m: YearMonth): String = SHORT[m.monthValue - 1]
    fun longMonth(m: YearMonth): String = "${LONG[m.monthValue - 1]} ${m.year}"

    /**
     * Un punto per ogni mese di [months]. [income] e [expense] sono i totali per mese con chiave "2026-11"
     * (le uscite come numeri positivi). I mesi dopo [current] sono futuri e restano a zero.
     */
    fun build(
        months: List<YearMonth>,
        income: Map<String, Long>,
        expense: Map<String, Long>,
        current: YearMonth
    ): List<CashflowPoint> = months.map { m ->
        val future = m.isAfter(current)
        CashflowPoint(
            month = m,
            incomeCents = if (future) 0L else income[m.toString()] ?: 0L,
            expenseCents = if (future) 0L else expense[m.toString()] ?: 0L,
            future = future
        )
    }

    /** Totali della finestra: solo i mesi già trascorsi o in corso. */
    fun totals(points: List<CashflowPoint>): Triple<Long, Long, Long> {
        val shown = points.filter { !it.future }
        val inc = shown.sumOf { it.incomeCents }
        val exp = shown.sumOf { it.expenseCents }
        return Triple(inc, exp, inc - exp)
    }

    /** Valore massimo da disegnare (entrate o uscite), 0 se non c'è nessun movimento. */
    fun peak(points: List<CashflowPoint>): Long =
        points.filter { !it.future }.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) } ?: 0L

    /**
     * Fondo scala "tondo" (in centesimi) per l'asse verticale: il primo valore di una scala 1-1,5-2-2,5-3-4-5-6-8-10
     * che contiene [peakCents]. Serve a dare etichette leggibili alle linee di riferimento.
     */
    fun niceMax(peakCents: Long): Long {
        if (peakCents <= 0L) return 0L
        val euros = Math.ceil(peakCents / 100.0).coerceAtLeast(1.0)
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(euros)))
        for (step in listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)) {
            if (step * magnitude >= euros) return Math.round(step * magnitude * 100.0)
        }
        return Math.round(10.0 * magnitude * 100.0)
    }

    /** Indice del mese sotto il dito: la larghezza è divisa in [count] colonne uguali. */
    fun slotIndex(x: Float, width: Float, count: Int): Int {
        if (count <= 0 || width <= 0f) return 0
        return ((x / width) * count).toInt().coerceIn(0, count - 1)
    }
}
