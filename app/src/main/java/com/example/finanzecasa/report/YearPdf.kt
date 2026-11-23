package com.example.finanzecasa.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.finanzecasa.domain.CashflowSeries
import com.example.finanzecasa.domain.CategoryLine
import com.example.finanzecasa.domain.ReportMath
import com.example.finanzecasa.domain.YearSummary
import com.example.finanzecasa.domain.YearSummaryData
import com.example.finanzecasa.ui.compactMoney
import com.example.finanzecasa.ui.formatMoney
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Riepilogo dell'anno in PDF (A4): totali con confronto, mese per mese con grafico e tabella, spese ed entrate
 * per categoria. Usa solo il PdfDocument di Android, quindi non servono librerie e nulla esce dal telefono.
 */
object YearPdf {
    fun write(data: YearSummaryData, out: OutputStream, generatedOn: LocalDate) {
        val doc = PdfDocument()
        try {
            PdfBuilder(doc, data, generatedOn).build()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 40f
private const val CONTENT_W = PAGE_W - 2 * MARGIN

private val INK = 0xFF222222.toInt()
private val GRAY = 0xFF6B6B6B.toInt()
private val LINE = 0xFFD9D9D9.toInt()
private val HEADER_BG = 0xFFEFEFEF.toInt()
private val BOX_BG = 0xFFF7F7F7.toInt()
private val GREEN = 0xFF1E8E5A.toInt()
private val RED = 0xFFC42B3F.toInt()

private class PdfBuilder(
    private val doc: PdfDocument,
    private val d: YearSummaryData,
    private val generatedOn: LocalDate
) {
    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    private lateinit var c: Canvas
    private var y = MARGIN

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
            textAlign = align
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float = 0.8f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

    private fun fit(text: String, p: Paint, maxWidth: Float): String {
        val n = p.breakText(text, true, maxWidth, null)
        return if (n >= text.length) text else text.substring(0, maxOf(0, n - 1)).trimEnd() + "…"
    }

    private fun money(cents: Long) = formatMoney(cents)
    private fun signed(cents: Long) = (if (cents > 0) "+" else "") + formatMoney(cents)

    // ---------------------------------------------------------------- pagine

    private fun newPage() {
        finishPage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        page = p
        c = p.canvas
        y = MARGIN
    }

    private fun finishPage() {
        val p = page ?: return
        c.drawText(
            "Il mio wallet · Riepilogo ${d.year} · pagina $pageNo",
            PAGE_W / 2f, PAGE_H - 22f, paint(8f, color = GRAY, align = Paint.Align.CENTER)
        )
        doc.finishPage(p)
        page = null
    }

    /** Va a una nuova pagina se non c'è spazio per [height] punti. Restituisce true se l'ha fatto. */
    private fun ensure(height: Float): Boolean {
        if (y + height <= PAGE_H - MARGIN - 14f) return false
        newPage()
        return true
    }

    fun build() {
        newPage()
        header()
        summaryBoxes()
        facts()
        monthChart()
        monthTable()
        categoryTable(
            "Spese per categoria", d.expenseCategories, d.expenseCents,
            comparison = true, color = RED
        )
        categoryTable(
            "Entrate per categoria", d.incomeCategories, d.incomeCents,
            comparison = true, color = GREEN
        )
        finishPage()
    }

    // ---------------------------------------------------------------- sezioni

    private fun header() {
        y += 22f
        c.drawText("Riepilogo ${d.year}", MARGIN, y, paint(24f, bold = true))
        y += 17f
        val date = generatedOn.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))
        val extra = if (d.inProgress) " · anno in corso, dati fino a oggi" else ""
        c.drawText("Entrate, spese e risparmio · creato il $date$extra", MARGIN, y, paint(10f, color = GRAY))
        y += 18f
    }

    private fun summaryBoxes() {
        val gap = 10f
        val w = (CONTENT_W - 2 * gap) / 3f
        val h = 66f
        val compare = if (d.inProgress) "stessi giorni ${d.year - 1}" else "${d.year - 1}"
        val items = listOf(
            Triple("Entrate", d.incomeCents to d.previousIncomeCents, GREEN),
            Triple("Spese", d.expenseCents to d.previousExpenseCents, RED),
            Triple("Risparmio", d.savingsCents to d.previousSavingsCents, if (d.savingsCents < 0) RED else INK)
        )
        items.forEachIndexed { i, (label, values, color) ->
            val x = MARGIN + i * (w + gap)
            val box = RectF(x, y, x + w, y + h)
            c.drawRoundRect(box, 8f, 8f, fill(BOX_BG))
            c.drawRoundRect(box, 8f, 8f, stroke(LINE))
            c.drawText(label, x + 10f, y + 17f, paint(9.5f, color = GRAY))
            val value = if (label == "Risparmio") signed(values.first) else money(values.first)
            c.drawText(fit(value, paint(16f, bold = true, color = color), w - 20f), x + 10f, y + 39f, paint(16f, bold = true, color = color))
            c.drawText(
                fit("vs $compare: ${YearSummary.changeLabel(values.first, values.second)}", paint(8.5f, color = GRAY), w - 20f),
                x + 10f, y + 56f, paint(8.5f, color = GRAY)
            )
        }
        y += h + 16f
    }

    private fun facts() {
        val p = paint(10f)
        val lines = ArrayList<String>()
        d.savingsRatePercent?.let { lines += "Tasso di risparmio: $it% delle entrate." }
        if (d.savedCents > 0) lines += "Messi da parte con trasferimenti di risparmio: ${money(d.savedCents)}."
        if (d.provisionedCents > 0) lines += "Accantonati per spese future: ${money(d.provisionedCents)}."
        YearSummary.bestAndWorstMonth(d.months)?.let { (best, worst) ->
            lines += "Mese migliore: ${CashflowSeries.longMonth(best.month)} (saldo ${signed(best.netCents)})."
            if (worst.month != best.month) {
                lines += "Mese peggiore: ${CashflowSeries.longMonth(worst.month)} (saldo ${signed(worst.netCents)})."
            }
        }
        for (line in lines) {
            c.drawText(fit(line, p, CONTENT_W), MARGIN, y + 10f, p)
            y += 15f
        }
        y += 8f
    }

    private fun sectionTitle(text: String) {
        c.drawText(text, MARGIN, y + 12f, paint(12.5f, bold = true))
        y += 22f
    }

    private fun monthChart() {
        val chartH = 120f
        ensure(22f + chartH + 40f)
        sectionTitle("Entrate e spese mese per mese")
        val left = MARGIN + 36f
        val width = PAGE_W - MARGIN - left
        val top = y + 4f
        val bottom = top + chartH
        val scale = CashflowSeries.niceMax(CashflowSeries.peak(d.months))
        val gridPaint = stroke(LINE, 0.6f)
        c.drawLine(left, bottom, left + width, bottom, stroke(GRAY, 0.8f))
        if (scale <= 0L) {
            c.drawText("Nessun movimento in quest'anno.", left, top + chartH / 2f, paint(10f, color = GRAY))
            y = bottom + 24f
            return
        }
        val labelPaint = paint(7.5f, color = GRAY, align = Paint.Align.RIGHT)
        c.drawLine(left, top, left + width, top, gridPaint)
        c.drawLine(left, (top + bottom) / 2f, left + width, (top + bottom) / 2f, gridPaint)
        c.drawText(compactMoney(scale), left - 4f, top + 3f, labelPaint)
        c.drawText(compactMoney(scale / 2), left - 4f, (top + bottom) / 2f + 3f, labelPaint)
        c.drawText("0", left - 4f, bottom + 3f, labelPaint)

        val slot = width / 12f
        val barW = minOf(slot * 0.34f, 13f)
        val monthPaint = paint(8f, color = GRAY, align = Paint.Align.CENTER)
        d.months.forEachIndexed { i, m ->
            val cx = left + slot * (i + 0.5f)
            c.drawText(CashflowSeries.shortMonth(m.month), cx, bottom + 11f, monthPaint)
            if (m.future) return@forEachIndexed
            fun bar(cents: Long, x: Float, color: Int) {
                if (cents <= 0L) return
                val barH = maxOf(1.5f, chartH * (cents.toFloat() / scale.toFloat()).coerceIn(0f, 1f))
                c.drawRect(x, bottom - barH, x + barW, bottom, fill(color))
            }
            bar(m.incomeCents, cx - barW - 0.75f, GREEN)
            bar(m.expenseCents, cx + 0.75f, RED)
        }
        y = bottom + 24f
        // Legenda
        c.drawRect(left, y - 7f, left + 8f, y + 1f, fill(GREEN))
        c.drawText("Entrate", left + 12f, y, paint(8.5f, color = GRAY))
        c.drawRect(left + 70f, y - 7f, left + 78f, y + 1f, fill(RED))
        c.drawText("Spese", left + 82f, y, paint(8.5f, color = GRAY))
        y += 18f
    }

    private fun monthTable() {
        val rowH = 15f
        ensure(22f + rowH * 14)
        sectionTitle("Il dettaglio dei mesi")
        val colMonth = MARGIN + 6f
        val colIncome = MARGIN + 250f
        val colExpense = MARGIN + 380f
        val colNet = MARGIN + CONTENT_W - 6f
        header3(colMonth, colIncome, colExpense, colNet)
        val body = paint(9.5f)
        val right = paint(9.5f, align = Paint.Align.RIGHT)
        d.months.forEach { m ->
            val name = CashflowSeries.longMonth(m.month).replaceFirstChar { it.uppercase() }
            if (m.future) {
                c.drawText(name, colMonth, y + 11f, paint(9.5f, color = GRAY))
                c.drawText("—", colNet, y + 11f, paint(9.5f, color = GRAY, align = Paint.Align.RIGHT))
            } else {
                c.drawText(name, colMonth, y + 11f, body)
                c.drawText(money(m.incomeCents), colIncome, y + 11f, right)
                c.drawText(money(m.expenseCents), colExpense, y + 11f, right)
                c.drawText(signed(m.netCents), colNet, y + 11f, paint(9.5f, color = if (m.netCents < 0) RED else INK, align = Paint.Align.RIGHT))
            }
            c.drawLine(MARGIN, y + rowH, MARGIN + CONTENT_W, y + rowH, stroke(LINE, 0.4f))
            y += rowH
        }
        // Totale
        val bold = paint(9.5f, bold = true)
        val boldRight = paint(9.5f, bold = true, align = Paint.Align.RIGHT)
        c.drawText("Totale ${d.year}", colMonth, y + 11f, bold)
        c.drawText(money(d.incomeCents), colIncome, y + 11f, boldRight)
        c.drawText(money(d.expenseCents), colExpense, y + 11f, boldRight)
        c.drawText(signed(d.savingsCents), colNet, y + 11f, paint(9.5f, bold = true, color = if (d.savingsCents < 0) RED else INK, align = Paint.Align.RIGHT))
        y += rowH + 14f
    }

    private fun header3(c1: Float, c2: Float, c3: Float, c4: Float) {
        c.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + 16f, fill(HEADER_BG))
        val h = paint(8.5f, bold = true, color = GRAY)
        val hr = paint(8.5f, bold = true, color = GRAY, align = Paint.Align.RIGHT)
        c.drawText("Mese", c1, y + 11f, h)
        c.drawText("Entrate", c2, y + 11f, hr)
        c.drawText("Spese", c3, y + 11f, hr)
        c.drawText("Saldo", c4, y + 11f, hr)
        y += 17f
    }

    /** Tabella delle categorie con quota sul totale e, se richiesto, confronto con l'anno prima. */
    private fun categoryTable(title: String, all: List<CategoryLine>, total: Long, comparison: Boolean, color: Int) {
        val lines = YearSummary.topWithOthers(all, MAX_CATEGORIES)
        val rowH = 15f
        ensure(22f + 17f + rowH * 3)
        sectionTitle(title)
        if (lines.isEmpty()) {
            c.drawText("Nessun movimento in quest'anno.", MARGIN + 6f, y + 10f, paint(10f, color = GRAY))
            y += 26f
            return
        }
        val colAmount = MARGIN + 290f
        val colShare = MARGIN + 345f
        val colPrev = MARGIN + 440f
        val colChange = MARGIN + CONTENT_W - 6f
        val nameW = colAmount - 85f - (MARGIN + 6f)

        fun tableHeader() {
            c.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + 16f, fill(HEADER_BG))
            val h = paint(8.5f, bold = true, color = GRAY)
            val hr = paint(8.5f, bold = true, color = GRAY, align = Paint.Align.RIGHT)
            c.drawText("Categoria", MARGIN + 6f, y + 11f, h)
            c.drawText("Importo", colAmount, y + 11f, hr)
            c.drawText("Quota", colShare, y + 11f, hr)
            if (comparison) {
                c.drawText(if (d.inProgress) "${d.year - 1} (stessi giorni)" else "${d.year - 1}", colPrev, y + 11f, hr)
                c.drawText("Var.", colChange, y + 11f, hr)
            }
            y += 17f
        }
        tableHeader()
        val body = paint(9.5f)
        val right = paint(9.5f, align = Paint.Align.RIGHT)
        val gray = paint(9.5f, color = GRAY, align = Paint.Align.RIGHT)
        for (line in lines) {
            if (ensure(rowH + 4f)) {
                sectionTitle("$title (continua)")
                tableHeader()
            }
            c.drawText(fit(line.name, body, nameW), MARGIN + 6f, y + 11f, body)
            c.drawText(money(line.cents), colAmount, y + 11f, paint(9.5f, color = color, align = Paint.Align.RIGHT))
            c.drawText("${ReportMath.share(line.cents, total)}%", colShare, y + 11f, right)
            if (comparison) {
                c.drawText(if (line.previousCents > 0) money(line.previousCents) else "—", colPrev, y + 11f, gray)
                c.drawText(YearSummary.changeLabel(line.cents, line.previousCents), colChange, y + 11f, right)
            }
            c.drawLine(MARGIN, y + rowH, MARGIN + CONTENT_W, y + rowH, stroke(LINE, 0.4f))
            y += rowH
        }
        ensure(rowH + 4f)
        c.drawText("Totale", MARGIN + 6f, y + 11f, paint(9.5f, bold = true))
        c.drawText(money(total), colAmount, y + 11f, paint(9.5f, bold = true, align = Paint.Align.RIGHT))
        y += rowH + 16f
    }

    private companion object {
        const val MAX_CATEGORIES = 25
    }
}
