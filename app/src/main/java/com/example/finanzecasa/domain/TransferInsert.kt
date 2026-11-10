package com.example.finanzecasa.domain

import com.example.finanzecasa.data.FinanceDao
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import java.util.UUID

/**
 * Crea le due gambe di un trasferimento (uscita dal conto di partenza, entrata in quello di arrivo).
 * Restituisce false se uno dei due account non esiste più.
 */
suspend fun insertTransferPair(
    dao: FinanceDao, fromId: Long, toId: Long, cents: Long, title: String, millis: Long,
    isSavings: Boolean, isProvision: Boolean
): Boolean {
    val from = dao.accountById(fromId) ?: return false
    val to = dao.accountById(toId) ?: return false
    val group = UUID.randomUUID().toString()
    val note = "Saldo trasferito\n${from.name} → ${to.name}"
    val label = title.ifBlank { "Trasferimento" }
    dao.insertTransaction(
        TransactionEntity(
            accountId = from.id, title = label, note = note, amountCents = -cents, dateMillis = millis,
            kind = TxKind.TRANSFER, transferGroupId = group, isSavings = isSavings, isProvision = isProvision
        )
    )
    dao.insertTransaction(
        TransactionEntity(
            accountId = to.id, title = label, note = note, amountCents = cents, dateMillis = millis,
            kind = TxKind.TRANSFER, transferGroupId = group, isSavings = isSavings, isProvision = isProvision
        )
    )
    return true
}

/** Inserisce la scadenza di una ricorrente: transazione normale oppure trasferimento. */
suspend fun insertRecurringOccurrence(
    dao: FinanceDao, r: com.example.finanzecasa.data.RecurringEntity, millis: Long
) {
    val to = r.toAccountId
    if (to != null) {
        insertTransferPair(
            dao, r.accountId, to, kotlin.math.abs(r.amountCents), r.title, millis,
            isSavings = r.transferFlag == 1, isProvision = r.transferFlag == 2
        )
    } else {
        dao.insertTransaction(
            TransactionEntity(
                accountId = r.accountId, categoryId = r.categoryId, title = r.title,
                amountCents = r.amountCents, dateMillis = millis
            )
        )
    }
}
