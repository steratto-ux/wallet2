package com.example.finanzecasa.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.TitleSuggestion
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.Tags
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

enum class TxType { EXPENSE, INCOME, TRANSFER }

/** Dati di una transazione esistente, nella forma usata dal modulo di inserimento. */
data class LoadedTransaction(
    val type: TxType,
    val title: String,
    val amountCents: Long,        // positivo
    val accountId: Long,          // per i trasferimenti: account di partenza
    val toAccountId: Long?,
    val categoryId: Long?,
    val dateTime: LocalDateTime,
    val note: String = "",
    val tags: String = "",
    val receiptPath: String? = null,
    val isSavings: Boolean = false
)

class NewTransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp
    private val dao = app.db.dao()

    val accounts: StateFlow<List<AccountEntity>> =
        dao.observeAccountsOrdered().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tag già usati, per proporli mentre si scrive. */
    val knownTags: StateFlow<List<String>> =
        dao.observeTagColumns().map { Tags.distinct(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Titoli già usati che corrispondono a quanto digitato (prima quelli che iniziano con il testo, poi gli altri),
     * ognuno con account e categoria dell'ultima transazione inserita con quel titolo.
     */
    suspend fun suggestTitles(text: String, type: TxType): List<TitleSuggestion> {
        val q = text.trim()
        if (q.isEmpty() || type == TxType.TRANSFER) return emptyList()
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val rows = dao.titleSuggestions(escaped, if (type == TxType.EXPENSE) 1 else 0)
        val unique = rows.distinctBy { it.title.lowercase() } // la prima riga di ogni titolo è la più recente
        val lower = q.lowercase()
        val (starting, others) = unique.partition { it.title.lowercase().startsWith(lower) }
        return (starting + others).take(6)
    }

    /** Salva la foto scelta dalla galleria o scattata; restituisce il nome del file oppure null. */
    suspend fun importReceiptPhoto(uri: Uri): String? =
        withContext(Dispatchers.IO) { importReceipt(getApplication<Application>(), uri) }

    fun discardReceipt(name: String?) = deleteReceiptFile(getApplication<Application>(), name)

    /** Crea la categoria (o sotto-categoria se parentId != null) e restituisce l'id; riusa quella esistente. */
    suspend fun createCategory(name: String, parentId: Long?, icon: String?): Long =
        dao.categoryByName(name, parentId)?.id
            ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId, icon = icon))

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
        dateTime: LocalDateTime,
        note: String = "",          // ignorata nei trasferimenti (hanno la nota "Saldo trasferito A → B")
        tags: String = "",          // ignorati nei trasferimenti
        receiptPath: String? = null, // ignorata nei trasferimenti
        isSavings: Boolean = false   // solo trasferimenti: conta come risparmio nella regola 50/30/20
    ) {
        val cleanTags = Tags.normalize(tags)
        val millis = dateTime.toMillis()
        when (type) {
            TxType.EXPENSE -> dao.insertTransaction(
                TransactionEntity(
                    accountId = accountId, categoryId = categoryId, title = title, note = note,
                    amountCents = -amountCents, dateMillis = millis, tags = cleanTags, receiptPath = receiptPath
                )
            )
            TxType.INCOME -> dao.insertTransaction(
                TransactionEntity(
                    accountId = accountId, categoryId = categoryId, title = title, note = note,
                    amountCents = amountCents, dateMillis = millis, tags = cleanTags, receiptPath = receiptPath
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
                        dateMillis = millis, kind = TxKind.TRANSFER, transferGroupId = group, isSavings = isSavings
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        accountId = to.id, title = label, note = note, amountCents = amountCents,
                        dateMillis = millis, kind = TxKind.TRANSFER, transferGroupId = group, isSavings = isSavings
                    )
                )
            }
        }
    }

    /** Carica una transazione esistente; per un trasferimento ricompone le due gambe in un'unica operazione. */
    suspend fun load(id: Long): LoadedTransaction? {
        val t = dao.transactionById(id) ?: return null
        val dt = Instant.ofEpochMilli(t.dateMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val group = t.transferGroupId
        if (t.kind == TxKind.TRANSFER && group != null) {
            val legs = dao.transferLegs(group)
            val out = legs.firstOrNull { it.amountCents < 0 } ?: t
            val into = legs.firstOrNull { it.amountCents > 0 }
            return LoadedTransaction(
                TxType.TRANSFER, out.title, abs(out.amountCents), out.accountId, into?.accountId, null, dt,
                isSavings = out.isSavings
            )
        }
        return LoadedTransaction(
            if (t.amountCents < 0) TxType.EXPENSE else TxType.INCOME,
            t.title, abs(t.amountCents), t.accountId, null, t.categoryId, dt, t.note, t.tags, t.receiptPath
        )
    }

    /** Elimina le righe dal database (per un trasferimento entrambe le gambe). Restituisce la foto collegata. */
    private suspend fun deleteRows(id: Long): String? {
        val t = dao.transactionById(id) ?: return null
        val group = t.transferGroupId
        if (group != null) dao.deleteTransferGroup(group) else dao.deleteTransaction(id)
        return t.receiptPath
    }

    /** Elimina la transazione e la foto dello scontrino; per un trasferimento elimina entrambe le gambe. */
    suspend fun delete(id: Long) {
        val receipt = app.db.withTransaction { deleteRows(id) }
        deleteReceiptFile(app, receipt)
    }

    /** Modifica = elimina la vecchia e salva la nuova (vale anche se cambia il tipo). */
    suspend fun update(
        id: Long, type: TxType, title: String, amountCents: Long,
        accountId: Long, toAccountId: Long?, categoryId: Long?, dateTime: LocalDateTime, note: String = "",
        tags: String = "", receiptPath: String? = null, isSavings: Boolean = false
    ) {
        val oldReceipt = app.db.withTransaction {
            val old = deleteRows(id)
            save(type, title, amountCents, accountId, toAccountId, categoryId, dateTime, note, tags, receiptPath, isSavings)
            old
        }
        // La vecchia foto va tolta solo se non è più usata (sostituita, rimossa o tipo cambiato in trasferimento)
        val stillUsed = type != TxType.TRANSFER && receiptPath == oldReceipt
        if (!stillUsed) deleteReceiptFile(app, oldReceipt)
    }
}
