package com.example.finanzecasa.domain

import java.time.LocalDate
import java.time.YearMonth

/** Un avviso da notificare. [key] identifica in modo univoco l'evento: ogni chiave viene notificata una sola volta. */
data class Alert(val key: String, val title: String, val text: String)

data class BudgetInput(val id: Long, val name: String, val amountCents: Long, val spentCents: Long)

/** Una scadenza vicina: abbonamento, rata, ricorrente o addebito carta. */
data class DueInput(val kind: String, val id: Long, val name: String, val amountCents: Long?, val date: LocalDate)

/** Decide quali notifiche mandare. Logica pura, senza Android. */
object AlertPlanner {
    /** Soglie di avviso dei budget, in percentuale. */
    const val WARN_PERCENT = 80
    const val OVER_PERCENT = 100

    fun budgetAlerts(budgets: List<BudgetInput>, month: YearMonth, fmt: (Long) -> String): List<Alert> =
        budgets.mapNotNull { b ->
            if (b.amountCents <= 0) return@mapNotNull null
            val percent = b.spentCents * 100 / b.amountCents
            when {
                percent >= OVER_PERCENT -> Alert(
                    "b|${b.id}|$month|$OVER_PERCENT",
                    "Budget superato",
                    "${b.name}: speso ${fmt(b.spentCents)} su ${fmt(b.amountCents)}."
                )
                percent >= WARN_PERCENT -> Alert(
                    "b|${b.id}|$month|$WARN_PERCENT",
                    "Budget quasi esaurito",
                    "${b.name}: hai usato l'$percent% (${fmt(b.spentCents)} su ${fmt(b.amountCents)})."
                )
                else -> null
            }
        }

    /** Scadenze di oggi o di domani. */
    fun dueAlerts(dues: List<DueInput>, today: LocalDate, fmt: (Long) -> String): List<Alert> =
        dues.filter { it.date == today || it.date == today.plusDays(1) }.map { d ->
            val when_ = if (d.date == today) "oggi" else "domani"
            val amount = d.amountCents?.let { " (${fmt(it)})" } ?: ""
            val (title, text) = when (d.kind) {
                "sub" -> "Abbonamento in scadenza" to "${d.name}$amount scade $when_."
                "loan" -> "Rata del prestito" to "La rata di ${d.name}$amount scade $when_."
                "rec" -> "Transazione ricorrente" to "${d.name}$amount è prevista $when_."
                "card" -> "Addebito carta" to "${d.name}: $when_ viene addebitato il saldo sul conto."
                else -> "Scadenza" to "${d.name}$amount: $when_."
            }
            Alert("d|${d.kind}|${d.id}|${d.date.toEpochDay()}", title, text)
        }

    /** Toglie dalle chiavi già inviate quelle vecchie (mesi passati, scadenze di più di una settimana fa). */
    fun prune(keys: Set<String>, today: LocalDate): Set<String> {
        val month = YearMonth.from(today).toString()
        return keys.filter { k ->
            val parts = k.split('|')
            when (parts.firstOrNull()) {
                "b" -> parts.getOrNull(2) == month
                "d" -> (parts.getOrNull(3)?.toLongOrNull() ?: 0L) >= today.toEpochDay() - 7
                else -> false
            }
        }.toSet()
    }
}
