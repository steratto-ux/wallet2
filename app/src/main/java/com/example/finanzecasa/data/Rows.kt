package com.example.finanzecasa.data

/** Riga "account con saldo": il saldo è la somma delle sue transazioni (come in Cashew). */
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: AccountType,
    val balanceCents: Long
)

/** Riga dell'elenco ultime transazioni. I trasferimenti compaiono una sola volta (gamba in uscita). */
data class TransactionRow(
    val id: Long,
    val title: String,
    val amountCents: Long,
    val dateMillis: Long,
    val kind: TxKind,
    val accountName: String,
    val counterpartName: String?,     // solo trasferimenti: account di destinazione
    val categoryName: String?,
    val parentCategoryName: String?
)
