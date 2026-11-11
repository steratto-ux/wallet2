package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Riepilogo dei dati finanziari che l'Analisi AI manda al servizio. Contiene solo totali e nomi scelti
 * dall'utente (account, categorie, abbonamenti, prestiti, obiettivi): mai i titoli, le note o le foto
 * delle singole transazioni.
 */
data class AiSnapshot(
    val today: LocalDate,
    val netWorthCents: Long,
    val accounts: List<AiAccountLine>,
    val months: List<AiMonthLine>,                // dal più vecchio al più recente (l'ultimo è il mese in corso, parziale)
    val categories: List<AiCategoryLine>,
    val forecastEndOfMonthCents: Long?,
    val fixedMonthlyCents: Long?,
    val rule: AiRuleLine?,
    val loans: List<AiLoanLine>,
    val subscriptions: List<AiSubscriptionLine>,
    val goals: List<AiGoalLine>,
    val budgets: List<AiBudgetLine>,
    val savedYearCents: Long,
    val provisionedYearCents: Long,
    val liquidCents: Long,                         // conti correnti e contanti
    val averageMonthlyExpenseCents: Long?,
    val emergencyMonths: Int?                      // mesi di spese coperti dal fondo di emergenza scelto, se impostato
)

data class AiAccountLine(val name: String, val type: String, val balanceCents: Long)
data class AiMonthLine(val month: YearMonth, val incomeCents: Long, val expenseCents: Long, val savedCents: Long)
data class AiCategoryLine(val name: String, val currentCents: Long, val averageCents: Long)
data class AiRuleLine(val month: YearMonth, val needPercent: Int?, val wantPercent: Int?, val savingsPercent: Int?)
data class AiLoanLine(val name: String, val installmentCents: Long, val remainingInstallments: Int, val ratePercent: Double)
data class AiSubscriptionLine(val name: String, val annualCents: Long, val toReview: Boolean)
data class AiGoalLine(val name: String, val savedCents: Long, val targetCents: Long, val deadline: LocalDate?)
data class AiBudgetLine(val name: String, val spentCents: Long, val amountCents: Long)

object AiReport {
    const val SYSTEM_PROMPT =
        "Sei un consulente di finanza personale prudente e concreto. Ricevi il riepilogo dei dati finanziari " +
            "di una famiglia italiana, calcolato da un'app di gestione delle spese. Scrivi un report in italiano " +
            "sullo stato delle sue finanze e sul loro andamento.\n\n" +
            "Regole:\n" +
            "- Usa solo i dati forniti: non inventare cifre e, se un dato manca o è poco affidabile (per esempio " +
            "pochi mesi di storico), dillo.\n" +
            "- Il mese in corso è parziale: non confrontarlo come se fosse concluso con i mesi chiusi.\n" +
            "- Testo semplice, senza markdown: niente asterischi, cancelletti, tabelle o grassetti. I titoletti " +
            "sono righe in MAIUSCOLO; gli elenchi usano il trattino.\n" +
            "- Struttura: STATO GENERALE (2-3 frasi), ANDAMENTO (entrate, uscite e risparmio degli ultimi mesi), " +
            "PUNTI DI ATTENZIONE, COSA VA BENE, TRE AZIONI PER IL PROSSIMO MESE (concrete e con importi quando ha senso).\n" +
            "- Importi in euro. Massimo circa 350 parole.\n" +
            "- Non consigliare prodotti finanziari o titoli specifici e non promettere rendimenti: sei un aiuto " +
            "per capire i numeri, non un consulente abilitato."

    private val monthFmt = DateTimeFormatter.ofPattern("MM/yyyy", Locale.ITALIAN)
    private val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)

    /** "1234,56 €", "-80,00 €" (senza separatore delle migliaia: più facile da leggere per il modello). */
    fun eur(cents: Long): String {
        val a = abs(cents)
        val sign = if (cents < 0) "-" else ""
        return "$sign${a / 100},${(a % 100).toString().padStart(2, '0')} €"
    }

    private fun percent(p: Int?): String = if (p == null) "n.d." else "$p%"

    /** Testo del messaggio utente: tutti i dati, in sezioni brevi. */
    fun buildPrompt(s: AiSnapshot): String = buildString {
        appendLine("Dati al ${s.today.format(dayFmt)}.")
        appendLine()
        appendLine("PATRIMONIO")
        appendLine("- Patrimonio netto (somma dei saldi): ${eur(s.netWorthCents)}")
        appendLine("- Liquidità (conti correnti e contanti): ${eur(s.liquidCents)}")
        s.averageMonthlyExpenseCents?.let { appendLine("- Spesa media mensile (ultimi mesi chiusi): ${eur(it)}") }
        s.emergencyMonths?.let { appendLine("- Fondo di emergenza: copre circa $it mesi di spese") }
        if (s.accounts.isNotEmpty()) {
            appendLine("- Conti:")
            s.accounts.forEach { appendLine("  - ${it.name} (${it.type}): ${eur(it.balanceCents)}") }
        }
        appendLine()
        appendLine("ULTIMI MESI (entrate, uscite, risparmio con trasferimenti dedicati)")
        val current = YearMonth.from(s.today)
        s.months.forEach {
            val partial = if (it.month == current) " (mese in corso, parziale)" else ""
            appendLine(
                "- ${it.month.format(monthFmt)}$partial: entrate ${eur(it.incomeCents)}, uscite ${eur(it.expenseCents)}, " +
                    "risparmiato ${eur(it.savedCents)}"
            )
        }
        appendLine()
        appendLine("Dall'inizio dell'anno: risparmiato ${eur(s.savedYearCents)}, accantonato per spese irregolari ${eur(s.provisionedYearCents)}.")
        if (s.categories.isNotEmpty()) {
            appendLine()
            appendLine("SPESE PER CATEGORIA (mese in corso, media dei mesi chiusi)")
            s.categories.forEach {
                appendLine("- ${it.name}: ${eur(it.currentCents)} (media ${eur(it.averageCents)})")
            }
        }
        s.rule?.let {
            appendLine()
            appendLine(
                "REGOLA 50/30/20 (${it.month.format(monthFmt)}): necessità ${percent(it.needPercent)}, " +
                    "desideri ${percent(it.wantPercent)}, risparmio ${percent(it.savingsPercent)} delle entrate"
            )
        }
        s.fixedMonthlyCents?.let { appendLine("Spese fisse mensili stimate: ${eur(it)}") }
        s.forecastEndOfMonthCents?.let { appendLine("Saldo totale previsto a fine mese: ${eur(it)}") }
        if (s.budgets.isNotEmpty()) {
            appendLine()
            appendLine("BUDGET DEL MESE")
            s.budgets.forEach { appendLine("- ${it.name}: speso ${eur(it.spentCents)} su ${eur(it.amountCents)}") }
        }
        if (s.loans.isNotEmpty()) {
            appendLine()
            appendLine("PRESTITI")
            s.loans.forEach {
                appendLine("- ${it.name}: rata ${eur(it.installmentCents)}, ${it.remainingInstallments} rate rimaste, tasso ${it.ratePercent}%")
            }
        }
        if (s.subscriptions.isNotEmpty()) {
            appendLine()
            appendLine("ABBONAMENTI (costo annuo)")
            s.subscriptions.forEach {
                appendLine("- ${it.name}: ${eur(it.annualCents)}" + if (it.toReview) " (segnato da rivedere)" else "")
            }
        }
        if (s.goals.isNotEmpty()) {
            appendLine()
            appendLine("OBIETTIVI DI RISPARMIO")
            s.goals.forEach {
                val by = it.deadline?.let { d -> ", entro il ${d.format(dayFmt)}" } ?: ""
                appendLine("- ${it.name}: ${eur(it.savedCents)} su ${eur(it.targetCents)}$by")
            }
        }
        appendLine()
        append("Scrivi il report.")
    }.trim()

    /**
     * Spese per categoria principale: totale del mese in corso e media dei (fino a 3) mesi chiusi precedenti
     * a partire dal primo mese con movimenti. Le [top] categorie più pesanti, per media o per mese in corso.
     */
    fun categoryLines(items: List<SpendItem>, current: YearMonth, top: Int = 10): List<AiCategoryLine> {
        if (items.isEmpty()) return emptyList()
        val firstMonth = items.minOf { YearMonth.from(it.date) }
        val closed = (1..3).map { current.minusMonths(it.toLong()) }.filter { !it.isBefore(firstMonth) }
        val byCategory = items.groupBy { it.categoryName }
        return byCategory.map { (name, list) ->
            val now = list.filter { YearMonth.from(it.date) == current }.sumOf { it.cents }
            val past = if (closed.isEmpty()) 0L else list.filter { YearMonth.from(it.date) in closed }.sumOf { it.cents } / closed.size
            AiCategoryLine(name, now, past)
        }.filter { it.currentCents > 0 || it.averageCents > 0 }
            .sortedByDescending { maxOf(it.currentCents, it.averageCents) }
            .take(top)
    }

    /** Ultimi [count] mesi (compreso quello in corso) con entrate, uscite e risparmio; i mesi senza dati valgono zero. */
    fun monthLines(
        current: YearMonth,
        income: Map<YearMonth, Long>,
        expense: Map<YearMonth, Long>,
        saved: Map<YearMonth, Long>,
        count: Int = 6
    ): List<AiMonthLine> = (count - 1 downTo 0).map { current.minusMonths(it.toLong()) }.map { m ->
        AiMonthLine(m, income[m] ?: 0L, expense[m] ?: 0L, saved[m] ?: 0L)
    }
}
