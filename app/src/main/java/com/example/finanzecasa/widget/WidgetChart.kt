package com.example.finanzecasa.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.finanzecasa.R
import com.example.finanzecasa.domain.Rule503020
import com.example.finanzecasa.domain.RuleMonth
import com.example.finanzecasa.domain.RuleStatus
import com.example.finanzecasa.ui.formatMoneyShort

/**
 * Disegna il grafico a barre della regola 50/30/20 come immagine: un widget non può usare il disegno di Compose,
 * quindi le barre sono disegnate con Canvas e mostrate in un ImageView.
 */
object WidgetChart {
    private const val W = 720
    private const val H = 300
    private const val ROW = 100f

    private const val NEED = 0xFF3F7BD1.toInt()
    private const val WANT = 0xFFE0724A.toInt()
    private const val SAVE = 0xFF1E9E7C.toInt()
    private const val GOOD = 0xFF1E9E6A.toInt()
    private const val BAD = 0xFFE0524A.toInt()
    private const val WARN = 0xFFD98A3D.toInt()

    private class Line(
        val name: String, val color: Int, val cents: Long, val targetPercent: Int,
        val fill: Float, val fillColor: Int, val status: String, val statusColor: Int
    )

    fun render(context: Context, m: RuleMonth, showAmounts: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val text = context.getColor(R.color.widget_text)
        val secondary = context.getColor(R.color.widget_text_secondary)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text; textSize = 30f; textAlign = Paint.Align.RIGHT }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondary; textSize = 23f }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 23f; textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (secondary and 0x00FFFFFF) or 0x33000000 }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val income = m.incomeCents
        val needTarget = Rule503020.target(income, Rule503020.NEED_PERCENT)
        val wantTarget = Rule503020.target(income, Rule503020.WANT_PERCENT)
        val saveTarget = Rule503020.target(income, Rule503020.SAVINGS_PERCENT)

        fun share(value: Long, target: Long): Float =
            if (income <= 0 || target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)

        fun money(cents: Long) = if (showAmounts) formatMoneyShort(cents) else "l'obiettivo"

        val needStatus = Rule503020.spendStatus(m, m.needCents, Rule503020.NEED_PERCENT)
        val wantStatus = Rule503020.spendStatus(m, m.wantCents, Rule503020.WANT_PERCENT)
        val saveStatus = Rule503020.savingsStatus(m)

        val lines = listOf(
            Line(
                "Necessità", NEED, m.needCents, Rule503020.NEED_PERCENT, share(m.needCents, needTarget),
                if (needStatus == RuleStatus.OVER) BAD else NEED,
                when (needStatus) {
                    RuleStatus.OVER -> if (showAmounts) "Oltre di ${money(m.needCents - needTarget)}" else "Oltre l'obiettivo"
                    RuleStatus.OK -> "Entro l'obiettivo"
                    else -> "Nessuna entrata nel mese"
                },
                if (needStatus == RuleStatus.OVER) BAD else GOOD
            ),
            Line(
                "Desideri", WANT, m.wantCents, Rule503020.WANT_PERCENT, share(m.wantCents, wantTarget),
                if (wantStatus == RuleStatus.OVER) BAD else WANT,
                when (wantStatus) {
                    RuleStatus.OVER -> if (showAmounts) "Oltre di ${money(m.wantCents - wantTarget)}" else "Oltre l'obiettivo"
                    RuleStatus.OK -> "Entro l'obiettivo"
                    else -> ""
                },
                if (wantStatus == RuleStatus.OVER) BAD else GOOD
            ),
            Line(
                "Risparmio", SAVE, m.savingsCents, Rule503020.SAVINGS_PERCENT, share(m.savingsCents, saveTarget), SAVE,
                when (saveStatus) {
                    RuleStatus.SHORT -> if (showAmounts) "Mancano ${money(saveTarget - m.savingsCents)}" else "Sotto l'obiettivo"
                    RuleStatus.OK -> "Obiettivo raggiunto"
                    else -> ""
                },
                if (saveStatus == RuleStatus.SHORT) WARN else GOOD
            )
        )

        lines.forEachIndexed { i, l ->
            val top = i * ROW
            fillPaint.color = l.color
            canvas.drawCircle(11f, top + 20f, 9f, fillPaint)
            canvas.drawText(l.name, 30f, top + 31f, labelPaint)

            val pct = m.percentOfIncome(l.cents)
            val value = (if (pct == null) "—" else "$pct%") + (if (showAmounts) "  ·  ${formatMoneyShort(l.cents)}" else "")
            canvas.drawText(value, W.toFloat(), top + 31f, valuePaint)

            val bar = RectF(0f, top + 43f, W.toFloat(), top + 61f)
            canvas.drawRoundRect(bar, 9f, 9f, trackPaint)
            if (l.fill > 0f) {
                fillPaint.color = l.fillColor
                canvas.drawRoundRect(RectF(0f, bar.top, maxOf(W * l.fill, 18f), bar.bottom), 9f, 9f, fillPaint)
            }

            canvas.drawText("obiettivo ${l.targetPercent}%", 0f, top + 90f, smallPaint)
            statusPaint.color = l.statusColor
            canvas.drawText(l.status, W.toFloat(), top + 90f, statusPaint)
        }
        return bitmap
    }
}
