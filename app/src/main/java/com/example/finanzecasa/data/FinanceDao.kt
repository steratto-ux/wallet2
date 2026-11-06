package com.example.finanzecasa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // --- Account
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun accountByName(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun accountById(id: Long): AccountEntity?

    @Insert
    suspend fun insertAccount(a: AccountEntity): Long

    @Query(
        "SELECT * FROM accounts WHERE type = 'CREDIT_CARD' AND cycleStartDay IS NOT NULL " +
            "AND debitDay IS NOT NULL AND linkedAccountId IS NOT NULL AND autoDebitSinceEpochDay IS NOT NULL"
    )
    suspend fun configuredCards(): List<AccountEntity>

    // --- Categorie
    @Query(
        "SELECT * FROM categories WHERE name = :name AND " +
            "((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) LIMIT 1"
    )
    suspend fun categoryByName(name: String, parentId: Long?): CategoryEntity?

    @Insert
    suspend fun insertCategory(c: CategoryEntity): Long

    // --- Transazioni
    @Insert
    suspend fun insertTransaction(t: TransactionEntity): Long

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND dateMillis = :dateMillis " +
            "AND amountCents = :amountCents AND title = :title"
    )
    suspend fun countSame(accountId: Long, dateMillis: Long, amountCents: Long, title: String): Int

    /** Somma delle sole transazioni "normali" (esclude i trasferimenti) nel periodo. */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE accountId = :accountId " +
            "AND kind = 'NORMAL' AND dateMillis BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun sumNormal(accountId: Long, fromMillis: Long, toMillis: Long): Long

    // --- Estratti carta
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStatement(s: CardStatementEntity): Long

    @Query("SELECT MAX(debitEpochDay) FROM card_statements WHERE cardAccountId = :cardId")
    suspend fun lastDebitEpochDay(cardId: Long): Long?

    // --- Abbonamenti e prestiti
    @Query("SELECT * FROM subscriptions WHERE active = 1 AND nextDueEpochDay <= :epochDay")
    suspend fun dueSubscriptions(epochDay: Long): List<SubscriptionEntity>

    @Update
    suspend fun updateSubscription(s: SubscriptionEntity)

    @Query("SELECT * FROM loans WHERE remainingInstallments > 0 AND nextDueEpochDay <= :epochDay")
    suspend fun dueLoans(epochDay: Long): List<LoanEntity>

    @Update
    suspend fun updateLoan(l: LoanEntity)

    // --- Schermata principale
    @Query(
        """SELECT a.id AS id, a.name AS name, a.type AS type, a.initialBalanceCents + COALESCE(SUM(t.amountCents), 0) AS balanceCents,
                  a.colorKey AS colorKey, a.iconKey AS iconKey, a.hidden AS hidden
           FROM accounts a LEFT JOIN transactions t ON t.accountId = a.id AND t.dateMillis < :toMillis
           GROUP BY a.id
           ORDER BY CASE WHEN a.sortOrder > 0 THEN 0 ELSE 1 END, a.sortOrder,
                    CASE a.type WHEN 'BANK' THEN 0 WHEN 'CASH' THEN 1 ELSE 2 END, a.name"""
    )
    fun observeBalances(toMillis: Long): Flow<List<AccountBalance>>

    /** Spese del periodo (positivo). I trasferimenti non contano. */
    @Query(
        "SELECT COALESCE(SUM(-amountCents), 0) FROM transactions WHERE kind = 'NORMAL' AND amountCents < 0 " +
            "AND dateMillis >= :fromMillis AND dateMillis < :toMillis"
    )
    fun observeExpenses(fromMillis: Long, toMillis: Long): Flow<Long>

    /** Entrate del periodo. I trasferimenti non contano. */
    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE kind = 'NORMAL' AND amountCents > 0 " +
            "AND dateMillis >= :fromMillis AND dateMillis < :toMillis"
    )
    fun observeIncome(fromMillis: Long, toMillis: Long): Flow<Long>

    @Query(
        """SELECT t.id AS id, t.title AS title, t.amountCents AS amountCents, t.dateMillis AS dateMillis,
                  t.kind AS kind, a.name AS accountName,
                  (SELECT a2.name FROM transactions t2 JOIN accounts a2 ON a2.id = t2.accountId
                   WHERE t2.transferGroupId = t.transferGroupId AND t2.id <> t.id LIMIT 1) AS counterpartName,
                  c.name AS categoryName, c.icon AS categoryIcon,
                  p.name AS parentCategoryName, p.icon AS parentCategoryIcon,
                  t.tags AS tags, t.receiptPath AS receiptPath
           FROM transactions t
           JOIN accounts a ON a.id = t.accountId
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           WHERE t.dateMillis < :toMillis AND (t.kind = 'NORMAL' OR t.amountCents < 0)
           ORDER BY t.dateMillis DESC, t.id DESC
           LIMIT :limit"""
    )
    fun observeRecent(toMillis: Long, limit: Int): Flow<List<TransactionRow>>

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    // --- Gestione account / categorie / abbonamenti / prestiti
    @Update
    suspend fun updateAccount(a: AccountEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun categoryById(id: Long): CategoryEntity?

    @Update
    suspend fun updateCategory(c: CategoryEntity)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInTransactions(id: Long)

    @Query("UPDATE subscriptions SET categoryId = NULL WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInSubscriptions(id: Long)

    @Query("UPDATE loans SET categoryId = NULL WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInLoans(id: Long)

    @Query("DELETE FROM categories WHERE id = :id OR parentId = :id")
    suspend fun deleteCategoryTree(id: Long)

    @Query("SELECT * FROM subscriptions ORDER BY name")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun subscriptionById(id: Long): SubscriptionEntity?

    @Insert
    suspend fun insertSubscription(s: SubscriptionEntity): Long

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: Long)

    @Query("SELECT * FROM loans ORDER BY name")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun loanById(id: Long): LoanEntity?

    @Insert
    suspend fun insertLoan(l: LoanEntity): Long

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    // --- Elenco completo, modifica ed eliminazione transazioni
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun transferLegs(groupId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE transferGroupId = :groupId")
    suspend fun deleteTransferGroup(groupId: String)

    /**
     * Tutte le transazioni (anche future), filtrabili per account (una delle due gambe), testo (titolo, nota,
     * categoria) e intervallo di date ([fromMillis] incluso, [toMillis] escluso; null = senza limite).
     */
    @Query(
        """SELECT t.id AS id, t.title AS title, t.amountCents AS amountCents, t.dateMillis AS dateMillis,
                  t.kind AS kind, a.name AS accountName,
                  (SELECT a2.name FROM transactions t2 JOIN accounts a2 ON a2.id = t2.accountId
                   WHERE t2.transferGroupId = t.transferGroupId AND t2.id <> t.id LIMIT 1) AS counterpartName,
                  c.name AS categoryName, c.icon AS categoryIcon,
                  p.name AS parentCategoryName, p.icon AS parentCategoryIcon,
                  t.tags AS tags, t.receiptPath AS receiptPath
           FROM transactions t
           JOIN accounts a ON a.id = t.accountId
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           WHERE (t.kind = 'NORMAL' OR t.amountCents < 0)
             AND (:accountId IS NULL OR t.accountId = :accountId OR EXISTS (
                   SELECT 1 FROM transactions t3
                   WHERE t3.transferGroupId = t.transferGroupId AND t3.accountId = :accountId))
             AND (:query = '' OR t.title LIKE '%' || :query || '%' OR t.note LIKE '%' || :query || '%'
                  OR c.name LIKE '%' || :query || '%' OR p.name LIKE '%' || :query || '%'
                  OR t.tags LIKE '%' || :query || '%')
             AND (:fromMillis IS NULL OR t.dateMillis >= :fromMillis)
             AND (:toMillis IS NULL OR t.dateMillis < :toMillis)
           ORDER BY t.dateMillis DESC, t.id DESC
           LIMIT :limit"""
    )
    fun observeAll(accountId: Long?, query: String, fromMillis: Long?, toMillis: Long?, limit: Int): Flow<List<TransactionRow>>

    // --- Grafico del saldo
    @Query("SELECT COALESCE(SUM(initialBalanceCents), 0) FROM accounts")
    fun observeInitialTotal(): Flow<Long>

    /** Somma di tutte le transazioni prima di un istante (i trasferimenti si annullano a livello totale). */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE dateMillis < :toMillis")
    fun observeSumBefore(toMillis: Long): Flow<Long>

    @Query("SELECT dateMillis, amountCents FROM transactions WHERE dateMillis >= :fromMillis AND dateMillis < :toMillis")
    fun observeTxPoints(fromMillis: Long, toMillis: Long): Flow<List<TxPoint>>

    // --- Budget
    @Query("SELECT * FROM budgets ORDER BY name")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget_categories")
    fun observeBudgetLinks(): Flow<List<BudgetCategoryEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun budgetById(id: Long): BudgetEntity?

    @Query("SELECT categoryId FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun budgetCategoryIds(budgetId: Long): List<Long>

    @Insert
    suspend fun insertBudget(b: BudgetEntity): Long

    @Update
    suspend fun updateBudget(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetRow(id: Long)

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun deleteBudgetLinks(budgetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLinks(links: List<BudgetCategoryEntity>)

    @Query("DELETE FROM budget_categories WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInBudgets(id: Long)

    /**
     * Speso per budget nel periodo: solo uscite normali delle categorie del budget
     * (una categoria principale include le sue sotto-categorie; nessun doppio conteggio).
     */
    @Query(
        """SELECT b.id AS budgetId, COALESCE((
               SELECT SUM(-t.amountCents) FROM transactions t
               WHERE t.kind = 'NORMAL' AND t.amountCents < 0
                 AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
                 AND t.categoryId IN (
                     SELECT bc.categoryId FROM budget_categories bc WHERE bc.budgetId = b.id
                     UNION
                     SELECT c.id FROM categories c
                       JOIN budget_categories bc2 ON bc2.categoryId = c.parentId
                     WHERE bc2.budgetId = b.id)
           ), 0) AS spentCents
           FROM budgets b"""
    )
    fun observeBudgetSpending(fromMillis: Long, toMillis: Long): Flow<List<BudgetSpent>>

    // --- Correzione saldo ed eliminazione account
    /** Somma delle transazioni di un account fino a [toMillis] (esclusa). Il saldo è il valore iniziale più questa somma. */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE accountId = :accountId AND dateMillis < :toMillis")
    suspend fun sumForAccount(accountId: Long, toMillis: Long): Long

    /** L'altra gamba di un trasferimento con l'account eliminato diventa una normale entrata/uscita: il suo saldo non cambia. */
    @Query(
        """UPDATE transactions SET kind = 'NORMAL', transferGroupId = NULL
           WHERE accountId <> :accountId AND transferGroupId IN (
               SELECT transferGroupId FROM transactions WHERE accountId = :accountId AND transferGroupId IS NOT NULL)"""
    )
    suspend fun detachTransfersOf(accountId: Long)

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteTransactionsOfAccount(accountId: Long)

    @Query("DELETE FROM subscriptions WHERE accountId = :accountId")
    suspend fun deleteSubscriptionsOfAccount(accountId: Long)

    @Query("DELETE FROM loans WHERE accountId = :accountId")
    suspend fun deleteLoansOfAccount(accountId: Long)

    @Query("DELETE FROM card_statements WHERE cardAccountId = :accountId")
    suspend fun deleteStatementsOfCard(accountId: Long)

    /** Le carte che addebitavano sull'account eliminato perdono l'addebito automatico. */
    @Query(
        "UPDATE accounts SET linkedAccountId = NULL, cycleStartDay = NULL, debitDay = NULL, autoDebitSinceEpochDay = NULL " +
            "WHERE linkedAccountId = :accountId"
    )
    suspend fun unlinkCardsFrom(accountId: Long)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountRow(accountId: Long)

    // --- Esportazione CSV
    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun allAccounts(): List<AccountEntity>

    @Query(
        """SELECT a.name AS accountName, t.amountCents AS amountCents, t.title AS title, t.note AS note,
                  t.dateMillis AS dateMillis, t.kind AS kind,
                  c.name AS categoryName, p.name AS parentCategoryName
           FROM transactions t
           JOIN accounts a ON a.id = t.accountId
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           ORDER BY t.dateMillis DESC, t.id DESC"""
    )
    suspend fun exportRows(): List<ExportRow>

    // --- Spese per categoria (grafico a ciambella)
    /** Uscite normali del periodo raggruppate per categoria principale (le sotto-categorie confluiscono nella principale). */
    @Query(
        """SELECT COALESCE(p.name, c.name, 'Senza categoria') AS name,
                  CASE WHEN p.id IS NOT NULL THEN p.icon ELSE c.icon END AS icon,
                  SUM(-t.amountCents) AS totalCents,
                  COALESCE(p.id, c.id) AS categoryId
           FROM transactions t
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           WHERE t.kind = 'NORMAL' AND t.amountCents < 0 AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
           GROUP BY COALESCE(p.id, c.id)
           ORDER BY totalCents DESC"""
    )
    fun observeCategorySpend(fromMillis: Long, toMillis: Long): Flow<List<CategorySpend>>

    // --- Storico budget
    /** Uscite (importo positivo) che rientrano in un budget, per costruire la spesa mese per mese. */
    @Query(
        """SELECT t.dateMillis AS dateMillis, -t.amountCents AS amountCents FROM transactions t
           WHERE t.kind = 'NORMAL' AND t.amountCents < 0
             AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
             AND t.categoryId IN (
                 SELECT bc.categoryId FROM budget_categories bc WHERE bc.budgetId = :budgetId
                 UNION
                 SELECT c.id FROM categories c
                   JOIN budget_categories bc2 ON bc2.categoryId = c.parentId
                 WHERE bc2.budgetId = :budgetId)"""
    )
    fun observeBudgetSpentPoints(budgetId: Long, fromMillis: Long, toMillis: Long): Flow<List<TxPoint>>

    // --- Ordine, visibilità e personalizzazione degli account
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts")
    suspend fun maxAccountSortOrder(): Int

    @Query("UPDATE accounts SET sortOrder = :order WHERE id = :id")
    suspend fun setAccountSortOrder(id: Long, order: Int)

    @Query("UPDATE accounts SET hidden = :hidden WHERE id = :id")
    suspend fun setAccountHidden(id: Long, hidden: Boolean)

    // --- Transazioni ricorrenti
    @Query("SELECT * FROM recurring_transactions ORDER BY title")
    fun observeRecurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun recurringById(id: Long): RecurringEntity?

    @Insert
    suspend fun insertRecurring(r: RecurringEntity): Long

    @Update
    suspend fun updateRecurring(r: RecurringEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM recurring_transactions WHERE active = 1 AND nextDueEpochDay <= :epochDay")
    suspend fun dueRecurring(epochDay: Long): List<RecurringEntity>

    @Query("SELECT * FROM recurring_transactions WHERE active = 1")
    suspend fun activeRecurring(): List<RecurringEntity>

    @Query("DELETE FROM recurring_transactions WHERE accountId = :accountId")
    suspend fun deleteRecurringOfAccount(accountId: Long)

    @Query("UPDATE recurring_transactions SET categoryId = NULL WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInRecurring(id: Long)

    // --- Obiettivi di risparmio
    @Query("SELECT * FROM savings_goals ORDER BY name")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun goalById(id: Long): GoalEntity?

    @Insert
    suspend fun insertGoal(g: GoalEntity): Long

    @Update
    suspend fun updateGoal(g: GoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("UPDATE savings_goals SET accountId = NULL WHERE accountId = :accountId")
    suspend fun unlinkGoalsFromAccount(accountId: Long)

    @Query("UPDATE savings_goals SET categoryId = NULL WHERE categoryId = :id OR categoryId IN (SELECT id FROM categories WHERE parentId = :id)")
    suspend fun clearCategoryInGoals(id: Long)

    /** Movimento netto (entrate meno uscite) di una categoria e delle sue sotto-categorie in un periodo. */
    @Query(
        """SELECT COALESCE(SUM(t.amountCents), 0) FROM transactions t
           WHERE t.kind = 'NORMAL' AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
             AND (t.categoryId = :categoryId OR t.categoryId IN (SELECT id FROM categories WHERE parentId = :categoryId))"""
    )
    fun observeCategoryNet(categoryId: Long, fromMillis: Long, toMillis: Long): Flow<Long>

    // --- Tag
    @Query("SELECT tags FROM transactions WHERE tags <> ''")
    fun observeTagColumns(): Flow<List<String>>

    // --- Report
    /** Entrate normali del periodo raggruppate per categoria principale. */
    @Query(
        """SELECT COALESCE(p.name, c.name, 'Senza categoria') AS name,
                  CASE WHEN p.id IS NOT NULL THEN p.icon ELSE c.icon END AS icon,
                  SUM(t.amountCents) AS totalCents,
                  COALESCE(p.id, c.id) AS categoryId
           FROM transactions t
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           WHERE t.kind = 'NORMAL' AND t.amountCents > 0 AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
           GROUP BY COALESCE(p.id, c.id)
           ORDER BY totalCents DESC"""
    )
    fun observeCategoryIncome(fromMillis: Long, toMillis: Long): Flow<List<CategorySpend>>

    /**
     * Dettaglio per sotto-categoria di una categoria principale. [income] = 1 per le entrate, 0 per le uscite.
     * Le transazioni assegnate direttamente alla principale compaiono come "Senza sotto-categoria".
     */
    @Query(
        """SELECT CASE WHEN c.id = :parentId THEN 'Senza sotto-categoria' ELSE c.name END AS name,
                  c.icon AS icon,
                  SUM(ABS(t.amountCents)) AS totalCents,
                  c.id AS categoryId
           FROM transactions t
           JOIN categories c ON c.id = t.categoryId
           WHERE t.kind = 'NORMAL'
             AND ((:income = 1 AND t.amountCents > 0) OR (:income = 0 AND t.amountCents < 0))
             AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
             AND (c.id = :parentId OR c.parentId = :parentId)
           GROUP BY c.id
           ORDER BY totalCents DESC"""
    )
    fun observeSubcategoryTotals(parentId: Long, income: Int, fromMillis: Long, toMillis: Long): Flow<List<CategorySpend>>

    // --- Utilità per notifiche, widget e backup (letture immediate)
    @Query("SELECT * FROM loans WHERE remainingInstallments > 0")
    suspend fun activeLoans(): List<LoanEntity>

    @Query("SELECT * FROM subscriptions WHERE active = 1")
    suspend fun activeSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT * FROM budgets ORDER BY name")
    suspend fun budgetsOnce(): List<BudgetEntity>

    @Query(
        """SELECT * FROM accounts
           ORDER BY CASE WHEN sortOrder > 0 THEN 0 ELSE 1 END, sortOrder,
                    CASE type WHEN 'BANK' THEN 0 WHEN 'CASH' THEN 1 ELSE 2 END, name"""
    )
    fun observeAccountsOrdered(): Flow<List<AccountEntity>>

    @Query(
        """SELECT * FROM accounts
           ORDER BY CASE WHEN sortOrder > 0 THEN 0 ELSE 1 END, sortOrder,
                    CASE type WHEN 'BANK' THEN 0 WHEN 'CASH' THEN 1 ELSE 2 END, name"""
    )
    suspend fun accountsOrdered(): List<AccountEntity>

    // --- Analisi
    /** Tutte le uscite normali del periodo, una riga per transazione. */
    @Query(
        """SELECT t.title AS title,
                  COALESCE(p.id, c.id) AS categoryId,
                  COALESCE(p.name, c.name, 'Senza categoria') AS categoryName,
                  -t.amountCents AS cents,
                  t.dateMillis AS dateMillis
           FROM transactions t
           LEFT JOIN categories c ON c.id = t.categoryId
           LEFT JOIN categories p ON p.id = c.parentId
           WHERE t.kind = 'NORMAL' AND t.amountCents < 0 AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis"""
    )
    fun observeSpendRows(fromMillis: Long, toMillis: Long): Flow<List<SpendRow>>

    /** Entrate normali del periodo, un totale per mese. */
    @Query(
        """SELECT strftime('%Y-%m', t.dateMillis / 1000, 'unixepoch', 'localtime') AS month,
                  SUM(t.amountCents) AS totalCents
           FROM transactions t
           WHERE t.kind = 'NORMAL' AND t.amountCents > 0 AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis
           GROUP BY month"""
    )
    fun observeIncomeByMonth(fromMillis: Long, toMillis: Long): Flow<List<MonthTotal>>

    /** Movimento netto delle transazioni normali in un periodo (serve per quelle già programmate nel futuro). */
    @Query(
        """SELECT COALESCE(SUM(amountCents), 0) FROM transactions
           WHERE kind = 'NORMAL' AND dateMillis >= :fromMillis AND dateMillis < :toMillis"""
    )
    fun observeNetBetween(fromMillis: Long, toMillis: Long): Flow<Long>
}
