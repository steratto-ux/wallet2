package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.util.UUID

enum class TxType { EXPENSE, INCOME, TRANSFER }

class NewTransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp
    private val dao = app.db.dao()

    val accounts: StateFlow<List<AccountEntity>> =
        dao.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Crea la categoria (o sotto-categoria se parentId != null) e restituisce l'id; riusa quella esistente. */
    suspend fun createCategory(name: String, parentId: Long?): Long =
        dao.categoryByName(name, parentId)?.id ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId))

    /**
     * Salva la transazione. Se [dateTime] è nel futuro viene salvata comunque: la schermata principale
     * la conteggia solo quando arriva quel momento.
     */
    suspend fun save(
        type: TxType,
        title: String,
        amountCents: Long,          // sempre positivo
        accountId: Long,            // account di riferimento (per i trasferimenti: quello di partenza)
        toAccountId: Long?,         // solo trasferimenti
        categoryId: Long?,          // ignorata nei trasferimenti
        dateTime: LocalDateTime
    ) {
        val millis = dateTime.toMillis()
        when (type) {
            TxType.EXPENSE -> dao.insertTransaction(
                TransactionEntity(
                    accountId = accountId, categoryId = categoryId, title = title,
                    amountCents = -amountCents, dateMillis = millis
                )
            )
            TxType.INCOME -> dao.insertTransaction(
                TransactionEntity(
                    accountId = accountId, categoryId = categoryId, title = title,
                    amountCents = amountCents, dateMillis = millis
                )
            )
            TxType.TRANSFER -> app.db.withTransaction {
                val from = dao.accountById(accountId)!!
                val to = dao.accountById(toAccountId!!)!!
                val group = UUID.randomUUID().toString()
                val note = "Saldo trasferito\n${from.name} → ${to.name}"
                val label = title.ifBlank { "Trasferimento" }
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = from.id, title = label, note = note, amountCents = -amountCents,
                        dateMillis = millis, kind = TxKind.TRANSFER, transferGroupId = group
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = to.id, title = label, note = note, amountCents = amountCents,
                        dateMillis = millis, kind = TxKind.TRANSFER, transferGroupId = group
                    )
                )
            }
        }
    }
}
