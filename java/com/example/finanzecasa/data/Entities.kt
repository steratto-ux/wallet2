package com.example.finanzecasa.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType { BANK, CASH, CREDIT_CARD }
enum class TxKind { NORMAL, TRANSFER }
enum class Cadence { MONTHLY, YEARLY }

/** Importi sempre in centesimi (Long): niente errori di virgola mobile. */
@Entity(tableName = "accounts", indices = [Index(value = ["name"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String = "EUR",
    /** Valore iniziale: entra nel saldo (e quindi nel patrimonio netto). Può essere negativo. */
    @ColumnInfo(defaultValue = "0") val initialBalanceCents: Long = 0,
    // Solo carte di credito:
    val cycleStartDay: Int? = null,          // giorno di inizio periodo (es. 12)
    val debitDay: Int? = null,               // giorno di addebito sul conto (es. 24)
    val linkedAccountId: Long? = null,       // conto corrente collegato
    val autoDebitSinceEpochDay: Long? = null, // l'addebito automatico parte da questo giorno
    // Aggiunti nella v4:
    val sortOrder: Int = 0,                  // posizione scelta dall'utente (0 = ordine automatico)
    val hidden: Boolean = false,             // nascosto dalla schermata principale (il saldo conta ancora)
    val colorKey: String? = null,            // colore scelto; null = colore del tipo di account
    val iconKey: String? = null              // icona scelta; null = icona del tipo di account
)

/** Categoria e sotto-categoria nella stessa tabella: la sotto-categoria ha parentId valorizzato. */
@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    /** Chiave dell'icona scelta; null = icona automatica in base al nome. */
    val icon: String? = null,
    /** Aggiunto nella v5, per la regola 50/30/20: 0 = automatico (dal nome), 1 = necessità, 2 = desiderio. */
    val budgetBucket: Int = 0
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("dateMillis"), Index("categoryId")],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class, parentColumns = ["id"],
        childColumns = ["accountId"], onDelete = ForeignKey.CASCADE
    )]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long? = null,            // categoria "foglia" (sotto-categoria se presente)
    val title: String,
    val note: String = "",
    val amountCents: Long,                   // negativo = spesa, positivo = entrata
    val dateMillis: Long,
    val kind: TxKind = TxKind.NORMAL,
    val transferGroupId: String? = null,     // lega le due gambe di un trasferimento
    // Aggiunti nella v4:
    val tags: String = "",                   // etichette separate da virgola (es. "vacanza,regalo")
    val receiptPath: String? = null,         // nome del file della foto dello scontrino (cartella receipts/)
    // Aggiunto nella v5:
    val isSavings: Boolean = false           // trasferimento che conta come risparmio (regola 50/30/20)
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,                   // positivo: importo della spesa
    val cadence: Cadence,
    val anchorDay: Int,                      // giorno del mese di scadenza
    val nextDueEpochDay: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val active: Boolean = true
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val principalCents: Long,
    val annualRatePercent: Double,
    val installmentCents: Long,
    val dueDay: Int,
    val nextDueEpochDay: Long,
    val remainingInstallments: Int,
    val accountId: Long,
    val categoryId: Long? = null
)

/** Registro degli addebiti carta già generati: garantisce che ogni periodo sia addebitato una sola volta. */
@Entity(
    tableName = "card_statements",
    indices = [Index(value = ["cardAccountId", "periodEndEpochDay"], unique = true)]
)
data class CardStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardAccountId: Long,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val debitEpochDay: Long,
    val amountCents: Long
)

/** Budget mensile con un nome e un importo; le categorie incluse stanno in [BudgetCategoryEntity]. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long
)

/** Categorie incluse in un budget (una categoria principale include anche le sue sotto-categorie). */
@Entity(
    tableName = "budget_categories",
    primaryKeys = ["budgetId", "categoryId"],
    indices = [Index("categoryId")]
)
data class BudgetCategoryEntity(
    val budgetId: Long,
    val categoryId: Long
)

/** Transazione che si ripete (stipendio, affitto, bollette...). L'importo ha il segno: positivo = entrata. */
@Entity(tableName = "recurring_transactions")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    val cadence: Cadence,
    val anchorDay: Int,
    val nextDueEpochDay: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val active: Boolean = true
)

/**
 * Obiettivo di risparmio. L'avanzamento è la somma di: importo versato a mano, saldo dell'account collegato
 * e movimenti netti della categoria collegata dalla data di creazione.
 */
@Entity(tableName = "savings_goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val manualCents: Long = 0,
    val deadlineEpochDay: Long? = null,
    val createdEpochDay: Long
)
