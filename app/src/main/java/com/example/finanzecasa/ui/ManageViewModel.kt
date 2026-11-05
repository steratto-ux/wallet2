package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.domain.DueItemsProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Gestione di account, categorie, abbonamenti e prestiti (creazione, modifica, eliminazione). */
class ManageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp
    private val dao = app.db.dao()

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val accounts: StateFlow<List<AccountEntity>> = dao.observeAccounts().asState(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = dao.observeCategories().asState(emptyList())
    val subscriptions: StateFlow<List<SubscriptionEntity>> = dao.observeSubscriptions().asState(emptyList())
    val loans: StateFlow<List<LoanEntity>> = dao.observeLoans().asState(emptyList())

    // --- Account
    suspend fun accountById(id: Long) = dao.accountById(id)

    /** Crea (id == null) o aggiorna un account. Restituisce un messaggio d'errore oppure null. */
    suspend fun saveAccount(
        id: Long?, name: String, type: AccountType, initialCents: Long,
        cycleStartDay: Int?, debitDay: Int?, linkedAccountId: Long?
    ): String? {
        val clash = dao.accountByName(name)
        if (clash != null && clash.id != id) return "Esiste già un account con questo nome."
        // L'addebito automatico è attivo solo se la carta ha periodo, giorno di addebito e conto collegato.
        val auto = type == AccountType.CREDIT_CARD && cycleStartDay != null && debitDay != null && linkedAccountId != null
        val today = LocalDate.now().toEpochDay()
        if (id == null) {
            dao.insertAccount(
                AccountEntity(
                    name = name, type = type, initialBalanceCents = initialCents,
                    cycleStartDay = cycleStartDay.takeIf { auto }, debitDay = debitDay.takeIf { auto },
                    linkedAccountId = linkedAccountId.takeIf { auto },
                    autoDebitSinceEpochDay = today.takeIf { auto }
                )
            )
        } else {
            val old = dao.accountById(id) ?: return "Account non trovato."
            dao.updateAccount(
                old.copy(
                    name = name, type = type, initialBalanceCents = initialCents,
                    cycleStartDay = cycleStartDay.takeIf { auto }, debitDay = debitDay.takeIf { auto },
                    linkedAccountId = linkedAccountId.takeIf { auto },
                    // Chi configura la carta la prima volta parte da oggi: lo storico importato non viene toccato.
                    autoDebitSinceEpochDay = if (auto) (old.autoDebitSinceEpochDay ?: today) else null
                )
            )
        }
        return null
    }

    // --- Categorie
    suspend fun createCategory(name: String, parentId: Long?): Long =
        dao.categoryByName(name, parentId)?.id ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId))

    suspend fun renameCategory(id: Long, name: String): String? {
        val old = dao.categoryById(id) ?: return "Categoria non trovata."
        val clash = dao.categoryByName(name, old.parentId)
        if (clash != null && clash.id != id) return "Esiste già una categoria con questo nome."
        dao.updateCategory(old.copy(name = name))
        return null
    }

    /** Elimina la categoria e le sue sotto-categorie; le transazioni restano, senza categoria. */
    suspend fun deleteCategory(id: Long) = app.db.withTransaction {
        dao.clearCategoryInTransactions(id)
        dao.clearCategoryInSubscriptions(id)
        dao.clearCategoryInLoans(id)
        dao.deleteCategoryTree(id)
    }

    // --- Abbonamenti
    suspend fun subscriptionById(id: Long) = dao.subscriptionById(id)

    suspend fun saveSubscription(s: SubscriptionEntity) {
        if (s.id == 0L) dao.insertSubscription(s) else dao.updateSubscription(s)
        DueItemsProcessor(app.db).run(LocalDate.now()) // registra subito le scadenze già arrivate
    }

    suspend fun deleteSubscription(id: Long) = dao.deleteSubscription(id)

    // --- Prestiti
    suspend fun loanById(id: Long) = dao.loanById(id)

    suspend fun saveLoan(l: LoanEntity) {
        if (l.id == 0L) dao.insertLoan(l) else dao.updateLoan(l)
        DueItemsProcessor(app.db).run(LocalDate.now())
    }

    suspend fun deleteLoan(id: Long) = dao.deleteLoan(id)
}
