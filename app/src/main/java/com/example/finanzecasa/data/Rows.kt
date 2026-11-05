package com.example.finanzecasa.data

/** Riga "account con saldo": il saldo è il valore iniziale più la somma delle sue transazioni. */
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: AccountType,
    val balanceCents: Long
)

/** Riga dell'elenco transazioni. I trasferimenti compaiono una sola volta (gamba in uscita). */
data class TransactionRow(
    val id: Long,
    val title: String,
    val amountCents: Long,
    val dateMillis: Long,
    val kind: TxKind,
    val accountName: String,
    val counterpartName: String?,     // solo trasferimenti: account di destinazione
    val categoryName: String?,
    val categoryIcon: String?,
    val parentCategoryName: String?,
    val parentCategoryIcon: String?
)

/** Movimento minimo (data e importo) usato per costruire la serie del saldo. */
data class TxPoint(
    val dateMillis: Long,
    val amountCents: Long
)

/** Speso del mese corrente per un budget (solo uscite delle sue categorie). */
data class BudgetSpent(
    val budgetId: Long,
    val spentCents: Long
)
