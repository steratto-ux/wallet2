package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.data.SubscriptionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/** Una spesa (importo positivo) usata dalle analisi. [categoryName] è quello della categoria principale. */
data class SpendItem(
    val title: String,
    val categoryId: Long?,
    val categoryName: String,
    val cents: Long,
    val date: LocalDate,
    /** Sotto-categoria, se la spesa ne ha una (per la regola 50/30/20). */
    val subCategoryId: Long? = null
)

/** Chiave per riconoscere la "stessa" spesa: solo lettere, minuscole ("Netflix 11/2026" e "netflix" coincidono). */
object TitleKey {
    private val notLetters = Regex("[^\\p{L}]+")
    fun of(title: String): String = notLetters.replace(title.lowercase(Locale.ROOT), " ").trim()
}

internal fun median(values: List<Long>): Long {
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

/** Una spesa fissa: configurata (abbonamento, rata, ricorrente) oppure riconosciuta perché si ripete ogni mese. */
data class FixedItem(
    val key: String,
    val title: String,
    val typicalCents: Long,
    val typicalDay: Int,
    val monthsSeen: Int,
    val configured: Boolean
)

object FixedSpendDetector {
    /**
     * Una spesa è fissa se è configurata (abbonamenti, rate, ricorrenti) oppure se compare in almeno [minMonths] mesi,
     * al massimo [maxPerMonth] volte al mese e con un totale mensile simile (entro [tolerance] dalla mediana).
     */
    fun detect(
        items: List<SpendItem>,
        configuredKeys: Set<String>,
        minMonths: Int = 3,
        tolerance: Double = 0.2,
        maxPerMonth: Int = 2
    ): List<FixedItem> {
        val result = mutableListOf<FixedItem>()
        val groups = items.filter { TitleKey.of(it.title).isNotEmpty() }.groupBy { TitleKey.of(it.title) }
        for ((key, group) in groups) {
            val byMonth = group.groupBy { YearMonth.from(it.date) }
            val monthTotals = byMonth.values.map { list -> list.sumOf { it.cents } }
            val med = median(monthTotals)
            val configured = key in configuredKeys
            val stable = monthTotals.all { abs(it - med) <= tolerance * med }
            val periodic = byMonth.size >= minMonths && stable && byMonth.values.all { it.size <= maxPerMonth }
            if (!configured && !periodic) continue
            val latest = group.maxByOrNull { it.date } ?: continue
            val days = byMonth.values.map { list -> list.minOf { it.date.dayOfMonth }.toLong() }
            result += FixedItem(key, latest.title, med, median(days).toInt(), byMonth.size, configured)
        }
        return result.sortedByDescending { it.typicalCents }
    }

    /** Chiavi da considerare "fisse": quelle riconosciute più quelle configurate. */
    fun fixedKeys(detected: List<FixedItem>, configuredKeys: Set<String>): Set<String> =
        detected.map { it.key }.toSet() + configuredKeys.filter { it.isNotEmpty() }
}

data class SplitLine(val label: String, val cents: Long, val count: Int)

data class FixedVariableSplit(
    val fixedCents: Long,
    val variableCents: Long,
    val fixedLines: List<SplitLine>,
    val variableLines: List<SplitLine>
) {
    val totalCents: Long get() = fixedCents + variableCents
    /** Quota delle spese fisse sul totale (0-100). */
    val fixedPercent: Int get() = if (totalCents <= 0) 0 else (fixedCents * 100.0 / totalCents).roundToLong().toInt()
}

object SpendSplit {
    /** Divide le spese del mese in fisse (per voce) e variabili (per categoria). */
    fun split(monthItems: List<SpendItem>, fixedKeys: Set<String>): FixedVariableSplit {
        val (fixed, variable) = monthItems.partition { TitleKey.of(it.title) in fixedKeys }
        val fixedLines = fixed.groupBy { TitleKey.of(it.title) }.map { (_, list) ->
            SplitLine(list.maxByOrNull { it.date }?.title ?: "", list.sumOf { it.cents }, list.size)
        }.sortedByDescending { it.cents }
        val variableLines = variable.groupBy { it.categoryName }.map { (name, list) ->
            SplitLine(name, list.sumOf { it.cents }, list.size)
        }.sortedByDescending { it.cents }
        return FixedVariableSplit(fixed.sumOf { it.cents }, variable.sumOf { it.cents }, fixedLines, variableLines)
    }
}

/** Un'uscita o entrata prevista (importo con segno). */
/** Una voce attesa fino a fine mese. [ignoreKey] c'è solo per quelle stimate dallo storico, che l'utente può escludere. */
data class Upcoming(val label: String, val date: LocalDate, val cents: Long, val ignoreKey: String? = null)

data class ForecastResult(
    val currentCents: Long,
    val upcoming: List<Upcoming>,
    val upcomingCents: Long,
    val variableSoFarCents: Long,
    val dailyVariableCents: Long,
    val remainingDays: Int,
    val variableProjectionCents: Long,
    val projectedEndCents: Long
)

object ForecastMath {
    /**
     * Spesa variabile giornaliera attesa. All'inizio del mese si fida di più della media dei mesi scorsi
     * ([baselineDaily]); dal decimo giorno conta solo quanto speso finora.
     */
    fun dailyVariable(soFarCents: Long, daysElapsed: Int, baselineDaily: Long?): Long {
        val observed = soFarCents.toDouble() / daysElapsed.coerceAtLeast(1)
        if (baselineDaily == null) return observed.roundToLong()
        val weight = min(1.0, daysElapsed / 10.0)
        return (weight * observed + (1 - weight) * baselineDaily).roundToLong()
    }

    /** Scadenze da abbonamenti, rate e ricorrenti attive da oggi a fine mese. */
    fun configuredUpcoming(
        subs: List<SubscriptionEntity>,
        loans: List<LoanEntity>,
        recurring: List<RecurringEntity>,
        today: LocalDate
    ): List<Upcoming> {
        val end = today.withDayOfMonth(today.lengthOfMonth())
        fun inRange(epoch: Long): Boolean = LocalDate.ofEpochDay(epoch).let { !it.isBefore(today) && !it.isAfter(end) }
        return subs.filter { it.active && inRange(it.nextDueEpochDay) }
            .map { Upcoming(it.name, LocalDate.ofEpochDay(it.nextDueEpochDay), -it.amountCents) } +
            loans.filter { it.remainingInstallments > 0 && inRange(it.nextDueEpochDay) }
                .map { Upcoming(it.name, LocalDate.ofEpochDay(it.nextDueEpochDay), -it.installmentCents) } +
            recurring.filter { it.active }.flatMap { r ->
                // Le settimanali possono cadere più volte nel mese: si contano tutte quelle che restano
                val dates = generateSequence(LocalDate.ofEpochDay(r.nextDueEpochDay)) {
                    if (r.cadence == Cadence.WEEKLY) it.plusWeeks(1) else null
                }.takeWhile { !it.isAfter(end) }.filter { !it.isBefore(today) }
                dates.map { Upcoming(r.title, it, r.amountCents) }.toList()
            }
    }

    /**
     * Spese che si ripetono ogni mese, non configurate e non ancora comparse questo mese, il cui giorno abituale
     * non è ancora passato (ad esempio un affitto pagato sempre il 5).
     */
    fun patternUpcoming(fixed: List<FixedItem>, seenThisMonth: Set<String>, today: LocalDate): List<Upcoming> =
        fixed.filter { !it.configured && it.key !in seenThisMonth && it.typicalDay > today.dayOfMonth }
            .map { f ->
                val day = min(f.typicalDay, today.lengthOfMonth())
                Upcoming(f.title, today.withDayOfMonth(day), -f.typicalCents, f.key)
            }

    fun forecast(
        currentCents: Long,
        upcoming: List<Upcoming>,
        variableSoFarCents: Long,
        today: LocalDate,
        baselineDailyCents: Long?
    ): ForecastResult {
        val elapsed = today.dayOfMonth
        val remaining = today.lengthOfMonth() - elapsed
        val daily = dailyVariable(variableSoFarCents, elapsed, baselineDailyCents)
        val projection = daily * remaining
        val upcomingSum = upcoming.sumOf { it.cents }
        return ForecastResult(
            currentCents = currentCents,
            upcoming = upcoming.sortedBy { it.date },
            upcomingCents = upcomingSum,
            variableSoFarCents = variableSoFarCents,
            dailyVariableCents = daily,
            remainingDays = remaining,
            variableProjectionCents = projection,
            projectedEndCents = currentCents + upcomingSum - projection
        )
    }

    /** Spesa variabile media al giorno nei mesi chiusi indicati; null se non ci sono dati. */
    fun baselineDaily(items: List<SpendItem>, closedMonths: List<YearMonth>, fixedKeys: Set<String>): Long? {
        if (closedMonths.isEmpty()) return null
        val inMonths = items.filter { YearMonth.from(it.date) in closedMonths }
        if (inMonths.isEmpty()) return null
        val variable = inMonths.filter { TitleKey.of(it.title) !in fixedKeys }.sumOf { it.cents }
        val days = closedMonths.sumOf { it.lengthOfMonth() }
        return variable / days
    }
}

data class CategoryAvg(val categoryId: Long?, val name: String, val monthlyCents: Long)

/** Media mensile su alcuni mesi chiusi: entrate, spese fisse e spese variabili per categoria. */
data class Baseline(
    val incomeCents: Long,
    val fixedCents: Long,
    val variable: List<CategoryAvg>,
    val months: Int
) {
    val variableCents: Long get() = variable.sumOf { it.monthlyCents }
    val savingsCents: Long get() = incomeCents - fixedCents - variableCents
}

data class Cut(val name: String, val categoryId: Long?, val monthlyCents: Long, val cutCents: Long) {
    val percent: Int get() = if (monthlyCents <= 0) 0 else (cutCents * 100.0 / monthlyCents).roundToLong().toInt()
}

data class CutPlan(val cuts: List<Cut>, val achievedCents: Long, val shortfallCents: Long)

object SavingsPlanner {
    fun baseline(
        items: List<SpendItem>,
        incomeByMonth: Map<YearMonth, Long>,
        months: List<YearMonth>,
        fixedKeys: Set<String>
    ): Baseline {
        val n = months.size.coerceAtLeast(1)
        val inMonths = items.filter { YearMonth.from(it.date) in months }
        val (fixed, variable) = inMonths.partition { TitleKey.of(it.title) in fixedKeys }
        val categories = variable.groupBy { it.categoryName }.map { (name, list) ->
            CategoryAvg(list.firstOrNull { it.categoryId != null }?.categoryId, name, list.sumOf { it.cents } / n)
        }.filter { it.monthlyCents > 0 }.sortedByDescending { it.monthlyCents }
        val income = months.sumOf { incomeByMonth[it] ?: 0L } / n
        return Baseline(income, fixed.sumOf { it.cents } / n, categories, months.size)
    }

    /**
     * Quanto tagliare in ogni categoria variabile per recuperare [gapCents] al mese: tagli proporzionali al peso
     * di ogni categoria, ognuno al massimo [maxFraction] della sua spesa. Se non basta, [CutPlan.shortfallCents] lo dice.
     */
    fun suggestCuts(variable: List<CategoryAvg>, gapCents: Long, maxFraction: Double = 0.5): CutPlan {
        if (gapCents <= 0 || variable.isEmpty()) return CutPlan(emptyList(), 0, maxOf(gapCents, 0))
        val n = variable.size
        val capacity = LongArray(n) { (variable[it].monthlyCents * maxFraction).toLong() }
        val cut = LongArray(n)
        var remaining = gapCents
        for (round in 0..n) {
            if (remaining <= 0) break
            val active = (0 until n).filter { cut[it] < capacity[it] }
            if (active.isEmpty()) break
            val weight = active.sumOf { variable[it].monthlyCents }
            if (weight <= 0) break
            var distributed = 0L
            for (i in active) {
                val share = (remaining.toDouble() * variable[i].monthlyCents / weight).toLong()
                val add = min(share, capacity[i] - cut[i])
                cut[i] += add
                distributed += add
            }
            remaining -= distributed
            if (distributed == 0L) break
        }
        // Gli ultimi centesimi persi negli arrotondamenti
        var i = 0
        while (remaining > 0 && i < n) {
            if (cut[i] < capacity[i]) { cut[i]++; remaining-- } else i++
        }
        val cuts = (0 until n).filter { cut[it] > 0 }.map {
            Cut(variable[it].name, variable[it].categoryId, variable[it].monthlyCents, cut[it])
        }
        return CutPlan(cuts, gapCents - remaining, remaining)
    }

    /** Risparmio mensile se ogni categoria variabile scende della percentuale indicata (stessa posizione della lista). */
    fun savingsWithCuts(b: Baseline, percents: List<Int>): Long =
        b.savingsCents + b.variable.indices.sumOf { b.variable[it].monthlyCents * (percents.getOrNull(it) ?: 0) / 100 }
}

data class RepeatedSpend(
    val title: String,
    val count: Int,
    val totalCents: Long,
    val averageCents: Long,
    val perMonthCents: Long,
    val perYearCents: Long
)

object RepeatedSpends {
    /**
     * Piccole spese che tornano spesso: almeno [minCount] volte, di importo medio non oltre [maxAvgCents],
     * escluse le spese fisse. [spanMonths] è la durata del periodo analizzato, per stimare mese e anno.
     */
    fun find(
        items: List<SpendItem>,
        fixedKeys: Set<String>,
        spanMonths: Double,
        minCount: Int = 4,
        maxAvgCents: Long = 3_000
    ): List<RepeatedSpend> {
        val span = spanMonths.coerceAtLeast(1.0)
        return items.filter { TitleKey.of(it.title).let { k -> k.isNotEmpty() && k !in fixedKeys } }
            .groupBy { TitleKey.of(it.title) }
            .mapNotNull { (_, list) ->
                val total = list.sumOf { it.cents }
                val avg = total / list.size
                if (list.size < minCount || avg > maxAvgCents) return@mapNotNull null
                val perMonth = (total / span).roundToLong()
                RepeatedSpend(
                    title = list.maxByOrNull { it.date }?.title ?: "",
                    count = list.size, totalCents = total, averageCents = avg,
                    perMonthCents = perMonth, perYearCents = perMonth * 12
                )
            }
            .sortedByDescending { it.totalCents }
    }
}
