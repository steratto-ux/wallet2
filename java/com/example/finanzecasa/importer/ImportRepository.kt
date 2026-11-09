package com.example.finanzecasa.importer

import androidx.room.withTransaction
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.toMillis
import java.util.UUID

data class ImportReport(
    val transactions: Int,
    val transfers: Int,
    val duplicates: Int,
    val newAccounts: List<String>,
    val recurringCandidates: List<ImportedTx>,
    val errors: List<String>
) {
    fun summary(): String = buildString {
        append("Importate $transactions transazioni e $transfers trasferimenti")
        if (duplicates > 0) append(", $duplicates già presenti (saltate)")
        if (newAccounts.isNotEmpty()) append("\nNuovi account: ${newAccounts.joinToString()}")
        if (recurringCandidates.isNotEmpty()) append("\nRicorrenze trovate: ${recurringCandidates.size}")
        if (errors.isNotEmpty()) append("\nErrori: ${errors.size} (prima: ${errors.first()})")
    }
}

class ImportRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun import(csvText: String, defaultAccountName: String = "Portafoglio"): ImportReport {
        val parsed = CashewCsvParser.parse(csvText)
        return db.withTransaction {
            val accountIds = mutableMapOf<String, Long>()
            val newAccounts = mutableListOf<String>()
            val categoryIds = mutableMapOf<Pair<String, Long?>, Long>()

            suspend fun accountId(name: String): Long = accountIds.getOrPut(name) {
                dao.accountByName(name)?.id ?: dao.insertAccount(
                    AccountEntity(name = name, type = guessType(name))
                ).also { newAccounts += name }
            }

            suspend fun categoryId(name: String, parentId: Long?): Long =
                categoryIds.getOrPut(name to parentId) {
                    dao.categoryByName(name, parentId)?.id
                        ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId))
                }

            var tx = 0
            var tr = 0
            var dup = 0
            val recurring = mutableListOf<ImportedTx>()

            for (item in parsed.items) {
                when (item) {
                    is ImportedTx -> {
                        val accountName = item.account ?: defaultAccountName
                        val accId = accountId(accountName)
                        if (item.note == CashewCsvExporter.INITIAL_NOTE) {
                            // Riga di backup con il valore iniziale: vale solo per i conti creati da questo import
                            // (se il conto c'era già, il suo valore iniziale non si tocca).
                            if (accountName in newAccounts) {
                                dao.accountById(accId)?.let {
                                    dao.updateAccount(it.copy(initialBalanceCents = it.initialBalanceCents + item.amountCents))
                                }
                            }
                            continue
                        }
                        val millis = item.date.toMillis()
                        if (dao.countSame(accId, millis, item.amountCents, item.title) > 0) { dup++; continue }
                        val leaf = item.category?.let { parent ->
                            val parentId = categoryId(parent, null)
                            item.subcategory?.let { categoryId(it, parentId) } ?: parentId
                        }
                        dao.insertTransaction(
                            TransactionEntity(
                                accountId = accId, categoryId = leaf, title = item.title, note = item.note,
                                amountCents = item.amountCents, dateMillis = millis
                            )
                        )
                        tx++
                        if (item.recurringType != null) recurring += item
                    }
                    is ImportedTransfer -> {
                        val fromId = accountId(item.fromAccount)
                        val toId = accountId(item.toAccount)
                        val millis = item.date.toMillis()
                        if (dao.countSame(fromId, millis, -item.amountCents, item.title) > 0) { dup++; continue }
                        val group = UUID.randomUUID().toString()
                        val note = "Saldo trasferito\n${item.fromAccount} → ${item.toAccount}"
                        dao.insertTransaction(
                            TransactionEntity(
                                accountId = fromId, title = item.title, note = note,
                                amountCents = -item.amountCents, dateMillis = millis,
                                kind = TxKind.TRANSFER, transferGroupId = group
                            )
                        )
                        dao.insertTransaction(
                            TransactionEntity(
                                accountId = toId, title = item.title, note = note,
                                amountCents = item.amountCents, dateMillis = millis,
                                kind = TxKind.TRANSFER, transferGroupId = group
                            )
                        )
                        tr++
                    }
                }
            }
            ImportReport(tx, tr, dup, newAccounts, recurring, parsed.errors)
        }
    }

    /** Euristica sul nome: l'utente potrà correggere il tipo dalla schermata account. */
    private fun guessType(name: String): AccountType {
        val n = name.lowercase()
        return when {
            listOf("amex", "visa", "mastercard", "carta", "credit").any { n.contains(it) } -> AccountType.CREDIT_CARD
            listOf("portafoglio", "contanti", "cash", "wallet").any { n.contains(it) } -> AccountType.CASH
            else -> AccountType.BANK
        }
    }
}
