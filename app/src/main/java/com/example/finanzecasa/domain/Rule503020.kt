package com.example.finanzecasa.domain

import java.util.Locale
import kotlin.math.roundToInt

/** Le due famiglie di spesa della regola 50/30/20 (il risparmio viene dai trasferimenti segnati "Risparmio"). */
enum class Bucket { NEED, WANT }

object BucketRules {
    /** 0 = automatico, 1 = necessità, 2 = desiderio (valore salvato nella categoria). */
    const val AUTO = 0
    const val NEED = 1
    const val WANT = 2

    /** Parole (o inizi di parola) che indicano una spesa necessaria. */
    private val needPrefixes = listOf(
        "affitt", "mutuo", "bollett", "utenz", "energia", "riscaldam", "condomin",
        "spesa", "aliment", "supermerc", "trasport", "carburant", "benzin", "diesel", "mezzi", "treno", "autobus",
        "assicuraz", "medic", "salute", "farmac", "sanit", "dentist", "tass", "imposte", "bollo",
        "scuol", "istruzion", "bambin", "figli", "internet", "telefon", "cellulare", "prestit", "finanziament"
    )
    private val needWords = setOf("auto", "rata", "rate", "tari", "imu", "casa", "luce", "gas", "acqua")

    /** Classificazione automatica dal nome della categoria: ciò che non è riconosciuto come necessario è un desiderio. */
    fun auto(name: String): Bucket {
        val words = name.lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val need = words.any { w -> w in needWords || needPrefixes.any { w.startsWith(it) } }
        return if (need) Bucket.NEED else Bucket.WANT
    }

    fun effective(setting: Int, name: String): Bucket = when (setting) {
        NEED -> Bucket.NEED
        WANT -> Bucket.WANT
        else -> auto(name)
    }
}

/** Il mese letto con la regola 50/30/20; le percentuali sono sul reddito del mese. */
data class RuleMonth(
    val incomeCents: Long,
    val needCents: Long,
    val wantCents: Long,
    val savingsCents: Long,
    val needLines: List<SplitLine>,
    val wantLines: List<SplitLine>
) {
    /** Quanto del reddito non è né speso né risparmiato (negativo: si è speso più di quanto entrato). */
    val leftCents: Long get() = incomeCents - needCents - wantCents - savingsCents

    /** Percentuale sul reddito, arrotondata; null se nel mese non c'è reddito. */
    fun percentOfIncome(cents: Long): Int? =
        if (incomeCents <= 0) null else (cents * 100.0 / incomeCents).roundToInt()
}

enum class RuleStatus { OK, OVER, SHORT, NO_INCOME }

object Rule503020 {
    const val NEED_PERCENT = 50
    const val WANT_PERCENT = 30
    const val SAVINGS_PERCENT = 20

    /** Importo obiettivo per una quota del reddito. */
    fun target(incomeCents: Long, percent: Int): Long = incomeCents * percent / 100

    fun compute(
        items: List<SpendItem>,
        bucketOf: (SpendItem) -> Bucket,
        incomeCents: Long,
        savingsCents: Long
    ): RuleMonth {
        val (need, want) = items.partition { bucketOf(it) == Bucket.NEED }
        fun lines(list: List<SpendItem>) = list.groupBy { it.categoryName }
            .map { (name, l) -> SplitLine(name, l.sumOf { it.cents }, l.size) }
            .sortedByDescending { it.cents }
        return RuleMonth(
            incomeCents = incomeCents,
            needCents = need.sumOf { it.cents }, wantCents = want.sumOf { it.cents },
            savingsCents = savingsCents,
            needLines = lines(need), wantLines = lines(want)
        )
    }

    /** Necessità e desideri devono stare sotto la loro quota (OVER se la superano), il risparmio sopra (SHORT se è sotto). */
    fun spendStatus(m: RuleMonth, spentCents: Long, percent: Int): RuleStatus = when {
        m.incomeCents <= 0 -> RuleStatus.NO_INCOME
        spentCents > target(m.incomeCents, percent) -> RuleStatus.OVER
        else -> RuleStatus.OK
    }

    fun savingsStatus(m: RuleMonth): RuleStatus = when {
        m.incomeCents <= 0 -> RuleStatus.NO_INCOME
        m.savingsCents < target(m.incomeCents, SAVINGS_PERCENT) -> RuleStatus.SHORT
        else -> RuleStatus.OK
    }
}
