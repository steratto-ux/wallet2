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
    val autoDebitSinceEpochDay: Long? = null // l'addebito automatico parte da questo giorno
)

/** Categoria e sotto-categoria nella stessa tabella: la sotto-categoria ha parentId valorizzato. */
@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null
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
    val transferGroupId: String? = null      // lega le due gambe di un trasferimento
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
