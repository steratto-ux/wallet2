package com.example.finanzecasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.ui.AccountFormScreen
import com.example.finanzecasa.ui.CategoriesScreen
import com.example.finanzecasa.ui.HomeScreen
import com.example.finanzecasa.ui.LoanFormScreen
import com.example.finanzecasa.ui.LoansScreen
import com.example.finanzecasa.ui.NewTransactionScreen
import com.example.finanzecasa.ui.SubscriptionFormScreen
import com.example.finanzecasa.ui.SubscriptionsScreen
import com.example.finanzecasa.ui.TransactionsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FinanceApp
        // Recupera addebiti/abbonamenti/rate scaduti anche se il job giornaliero non è ancora girato.
        lifecycleScope.launch(Dispatchers.IO) { DueItemsProcessor(app.db).run(LocalDate.now()) }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

/** Navigazione semplice a stato: una "rotta" e, per i moduli, l'id dell'elemento in modifica (null = nuovo). */
@Composable
private fun AppRoot() {
    var route by rememberSaveable { mutableStateOf("home") }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editReturn by rememberSaveable { mutableStateOf("home") } // dove tornare dopo aver modificato una transazione

    val back: () -> Unit = {
        route = when (route) {
            "subscriptionForm" -> "subscriptions"
            "loanForm" -> "loans"
            "editTransaction" -> editReturn
            else -> "home"
        }
    }
    if (route != "home") BackHandler(onBack = back)

    when (route) {
        "newTransaction" -> NewTransactionScreen(onClose = back)
        "editTransaction" -> NewTransactionScreen(transactionId = editId, onClose = back)
        "transactions" -> TransactionsScreen(
            onBack = back,
            onOpen = { editId = it; editReturn = "transactions"; route = "editTransaction" }
        )
        "account" -> AccountFormScreen(accountId = editId, onClose = back)
        "categories" -> CategoriesScreen(onBack = back)
        "subscriptions" -> SubscriptionsScreen(onBack = back, onOpenForm = { editId = it; route = "subscriptionForm" })
        "subscriptionForm" -> SubscriptionFormScreen(id = editId, onClose = back)
        "loans" -> LoansScreen(onBack = back, onOpenForm = { editId = it; route = "loanForm" })
        "loanForm" -> LoanFormScreen(id = editId, onClose = back)
        else -> HomeScreen(
            onAddTransaction = { route = "newTransaction" },
            onNewAccount = { editId = null; route = "account" },
            onEditAccount = { editId = it; route = "account" },
            onOpen = { route = it },
            onOpenTransaction = { editId = it; editReturn = "home"; route = "editTransaction" }
        )
    }
}
