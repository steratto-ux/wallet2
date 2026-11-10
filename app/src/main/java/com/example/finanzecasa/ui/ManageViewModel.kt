package com.example.finanzecasa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.data.AccountEntity
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.BudgetCategoryEntity
import com.example.finanzecasa.data.BudgetEntity
import com.example.finanzecasa.data.CategoryEntity
import com.example.finanzecasa.data.LoanEntity
import com.example.finanzecasa.data.RecurringEntity
import com.example.finanzecasa.domain.AccountOrder
import com.example.finanzecasa.data.SubscriptionEntity
import com.example.finanzecasa.data.TransactionEntity
import com.example.finanzecasa.data.TxKind
import com.example.finanzecasa.domain.RecurringSchedule
import com.example.finanzecasa.domain.insertRecurringOccurrence
import com.example.finanzecasa.domain.TitleKey
import com.example.finanzecasa.domain.BudgetHistory
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.domain.MonthSpend
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    val recurring: StateFlow<List<RecurringEntity>> = dao.observeRecurring().asState(emptyList())

    /** Account nell'ordine scelto dall'utente (lo stesso della schermata principale). */
    val orderedAccounts: StateFlow<List<AccountEntity>> = dao.observeAccountsOrdered().asState(emptyList())

    // --- Ordine e visibilità degli account
    suspend fun moveAccount(id: Long, delta: Int) = app.db.withTransaction {
        val ids = dao.accountsOrdered().map { it.id }
        AccountOrder.move(ids, id, delta).forEachIndexed { index, accountId ->
            dao.setAccountSortOrder(accountId, index + 1)
        }
    }

    suspend fun setAccountHidden(id: Long, hidden: Boolean) = dao.setAccountHidden(id, hidden)

    // --- Transazioni ricorrenti
    suspend fun recurringById(id: Long) = dao.recurringById(id)

    suspend fun saveRecurring(r: RecurringEntity) {
        if (r.id == 0L) dao.insertRecurring(r) else dao.updateRecurring(r)
        DueItemsProcessor(app.db).run(LocalDate.now()) // registra subito le scadenze già trascorse
    }

    suspend fun deleteRecurring(id: Long) {
        // Lo storico resta: senza questo la previsione continuerebbe a stimare la voce dallo storico
        dao.recurringById(id)?.let { AppSettings(app).addForecastIgnored(TitleKey.of(it.title)) }
        dao.deleteRecurring(id)
    }

    /**
     * Registra subito la prossima scadenza di una ricorrente, anche prima della data prevista
     * (ad esempio lo stipendio accreditato il 28 invece del 30): la transazione ha la data di oggi
     * e la scadenza passa a quella successiva.
     */
    suspend fun registerRecurringNow(id: Long): Boolean {
        val done = app.db.withTransaction {
            val r = dao.recurringById(id) ?: return@withTransaction false
            insertRecurringOccurrence(dao, r, LocalDate.now().atStartOfDay().toMillis())
            val next = RecurringSchedule.following(LocalDate.ofEpochDay(r.nextDueEpochDay), r.cadence, r.anchorDay)
            dao.updateRecurring(r.copy(nextDueEpochDay = next.toEpochDay()))
            true
        }
        if (done) DueItemsProcessor(app.db).run(LocalDate.now()) // se c'erano altre scadenze già trascorse
        return done
    }

    // --- Account
    suspend fun accountById(id: Long) = dao.accountById(id)

    /** Crea (id == null) o aggiorna un account. Restituisce un messaggio d'errore oppure null. */
    suspend fun saveAccount(
        id: Long?, name: String, type: AccountType, initialCents: Long,
        cycleStartDay: Int?, debitDay: Int?, linkedAccountId: Long?,
        colorKey: String? = null, iconKey: String? = null
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
                    autoDebitSinceEpochDay = today.takeIf { auto },
                    // Se l'utente ha già scelto un ordine, il nuovo account va in fondo; altrimenti resta l'ordine automatico.
                    sortOrder = dao.maxAccountSortOrder().let { if (it > 0) it + 1 else 0 },
                    colorKey = colorKey, iconKey = iconKey
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
                    autoDebitSinceEpochDay = if (auto) (old.autoDebitSinceEpochDay ?: today) else null,
                    colorKey = colorKey, iconKey = iconKey
                )
            )
        }
        return null
    }

    /** Saldo attuale dell'account: valore iniziale più le transazioni già avvenute (le future non contano). */
    suspend fun currentBalanceCents(id: Long): Long? {
        val a = dao.accountById(id) ?: return null
        return a.initialBalanceCents + dao.sumForAccount(id, System.currentTimeMillis() + 60_000)
    }

    /**
     * Porta il saldo attuale a [targetCents] senza creare transazioni: si modifica il valore iniziale dell'account
     * della differenza. Restituisce il nuovo valore iniziale.
     */
    suspend fun correctBalance(id: Long, targetCents: Long): Long? = app.db.withTransaction {
        val a = dao.accountById(id) ?: return@withTransaction null
        val current = a.initialBalanceCents + dao.sumForAccount(id, System.currentTimeMillis() + 60_000)
        val newInitial = a.initialBalanceCents + (targetCents - current)
        dao.updateAccount(a.copy(initialBalanceCents = newInitial))
        newInitial
    }

    /**
     * Aggiorna a mano il valore di un conto di investimento: registra la differenza come "aggiornamento di valore",
     * che cambia il saldo (e il patrimonio) ma non conta né come entrata né come spesa.
     */
    suspend fun updateInvestmentValue(id: Long, targetCents: Long): Boolean = app.db.withTransaction {
        val a = dao.accountById(id) ?: return@withTransaction false
        val now = System.currentTimeMillis()
        val current = a.initialBalanceCents + dao.sumForAccount(id, now + 60_000)
        val diff = targetCents - current
        if (diff != 0L) {
            dao.insertTransaction(
                TransactionEntity(
                    accountId = id, title = "Aggiornamento valore", amountCents = diff,
                    dateMillis = now, kind = TxKind.VALUATION
                )
            )
        }
        true
    }

    /**
     * Elimina l'account con le sue transazioni, i suoi abbonamenti e prestiti. I trasferimenti con altri account
     * restano sull'altro account come normali entrate/uscite (il suo saldo non cambia).
     */
    suspend fun deleteAccount(id: Long) = app.db.withTransaction {
        dao.detachTransfersOf(id)
        dao.deleteTransactionsOfAccount(id)
        dao.deleteSubscriptionsOfAccount(id)
        dao.deleteLoansOfAccount(id)
        dao.deleteRecurringOfAccount(id)
        dao.unlinkGoalsFromAccount(id)
        dao.deleteStatementsOfCard(id)
        dao.unlinkCardsFrom(id)
        dao.deleteAccountRow(id)
    }

    // --- Categorie
    suspend fun createCategory(name: String, parentId: Long?, icon: String?): Long =
        dao.categoryByName(name, parentId)?.id
            ?: dao.insertCategory(CategoryEntity(name = name, parentId = parentId, icon = icon))

    /** Cambia nome, icona (icon == null = automatica) e, se indicato, il tipo per la regola 50/30/20. Restituisce un messaggio d'errore oppure null. */
    suspend fun saveCategory(id: Long, name: String, icon: String?, bucket: Int? = null): String? {
        val old = dao.categoryById(id) ?: return "Categoria non trovata."
        val clash = dao.categoryByName(name, old.parentId)
        if (clash != null && clash.id != id) return "Esiste già una categoria con questo nome."
        dao.updateCategory(old.copy(name = name, icon = icon, budgetBucket = bucket ?: old.budgetBucket))
        return null
    }

    /** Elimina la categoria e le sue sotto-categorie; le transazioni restano, senza categoria. */
    suspend fun deleteCategory(id: Long) = app.db.withTransaction {
        dao.clearCategoryInTransactions(id)
        dao.clearCategoryInSubscriptions(id)
        dao.clearCategoryInLoans(id)
        dao.clearCategoryInBudgets(id)
        dao.clearCategoryInRecurring(id)
        dao.clearCategoryInGoals(id)
        dao.deleteCategoryTree(id)
    }

    // --- Budget (mensili, su una o più categorie)
    @OptIn(ExperimentalCoroutinesApi::class)
    val budgets: StateFlow<List<BudgetCardData>> =
        minuteClock().flatMapLatest { now -> budgetCards(dao, now) }.asState(emptyList())

    suspend fun budgetById(id: Long) = dao.budgetById(id)

    suspend fun budgetCategoryIds(id: Long): List<Long> = dao.budgetCategoryIds(id)

    /** Crea (id == null) o aggiorna un budget con le sue categorie. */
    suspend fun saveBudget(id: Long?, name: String, amountCents: Long, categoryIds: Set<Long>) {
        app.db.withTransaction {
            val budgetId = if (id == null) {
                dao.insertBudget(BudgetEntity(name = name, amountCents = amountCents))
            } else {
                dao.updateBudget(BudgetEntity(id = id, name = name, amountCents = amountCents))
                dao.deleteBudgetLinks(id)
                id
            }
            dao.insertBudgetLinks(categoryIds.map { BudgetCategoryEntity(budgetId, it) })
        }
    }

    /** Spesa mese per mese (ultimi 12 mesi, quello in corso compreso) di un budget. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun budgetHistory(id: Long): Flow<List<MonthSpend>> = minuteClock().flatMapLatest { now ->
        val months = BudgetHistory.lastMonths(YearMonth.from(now), 12)
        val from = months.first().atDay(1).atStartOfDay().toMillis()
        val to = now.plusMinutes(1).toMillis()
        dao.observeBudgetSpentPoints(id, from, to).map { points ->
            val zone = ZoneId.systemDefault()
            BudgetHistory.monthly(
                points.map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() to it.amountCents },
                months
            )
        }
    }

    suspend fun deleteBudget(id: Long) {
        app.db.withTransaction {
            dao.deleteBudgetLinks(id)
            dao.deleteBudgetRow(id)
        }
    }

    // --- Abbonamenti
    suspend fun subscriptionById(id: Long) = dao.subscriptionById(id)

    suspend fun saveSubscription(s: SubscriptionEntity) {
        if (s.id == 0L) dao.insertSubscription(s) else dao.updateSubscription(s)
        DueItemsProcessor(app.db).run(LocalDate.now()) // registra subito le scadenze già arrivate
    }

    /** Segna (o toglie) un abbonamento come "da rivedere": deciderà l'utente se tenerlo. */
    suspend fun setSubscriptionReview(id: Long, toReview: Boolean) {
        dao.subscriptionById(id)?.let { dao.updateSubscription(it.copy(toReview = toReview)) }
    }

    suspend fun deleteSubscription(id: Long) {
        dao.subscriptionById(id)?.let { AppSettings(app).addForecastIgnored(TitleKey.of(it.name)) }
        dao.deleteSubscription(id)
    }

    // --- Prestiti
    suspend fun loanById(id: Long) = dao.loanById(id)

    suspend fun saveLoan(l: LoanEntity) {
        if (l.id == 0L) dao.insertLoan(l) else dao.updateLoan(l)
        DueItemsProcessor(app.db).run(LocalDate.now())
    }

    suspend fun deleteLoan(id: Long) {
        dao.loanById(id)?.let { AppSettings(app).addForecastIgnored(TitleKey.of(it.name)) }
        dao.deleteLoan(id)
    }
}
