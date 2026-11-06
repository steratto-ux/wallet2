package com.example.finanzecasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.ui.AccountFormScreen
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.AppTheme
import com.example.finanzecasa.ui.BudgetHistoryScreen
import com.example.finanzecasa.ui.BudgetFormScreen
import com.example.finanzecasa.ui.BudgetsScreen
import com.example.finanzecasa.ui.CategoriesScreen
import com.example.finanzecasa.ui.HomeScreen
import com.example.finanzecasa.ui.LoanFormScreen
import com.example.finanzecasa.ui.LoansScreen
import com.example.finanzecasa.ui.NewTransactionScreen
import com.example.finanzecasa.ui.SettingsScreen
import com.example.finanzecasa.ui.SubscriptionFormScreen
import com.example.finanzecasa.ui.SubscriptionsScreen
import com.example.finanzecasa.ui.ThemeMode
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
        val settings = AppSettings(this)
        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode()) }
            AppTheme(themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(themeMode) { mode -> themeMode = mode; settings.setThemeMode(mode) }
                }
            }
        }
    }
}

/** Navigazione semplice a stato: una "rotta" e, per i moduli, l'id dell'elemento in modifica (null = nuovo). */
@Composable
private fun AppRoot(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    var route by rememberSaveable { mutableStateOf("home") }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnTo by rememberSaveable { mutableStateOf("home") } // dove tornare dopo aver modificato transazione o budget

    val back: () -> Unit = {
        route = when (route) {
            "subscriptionForm" -> "subscriptions"
            "loanForm" -> "loans"
            "editTransaction", "budgetForm", "budgetHistory" -> returnTo
            else -> "home"
        }
    }
    if (route != "home") BackHandler(onBack = back)

    when (route) {
        "newTransaction" -> NewTransactionScreen(onClose = back)
        "editTransaction" -> NewTransactionScreen(transactionId = editId, onClose = back)
        "transactions" -> TransactionsScreen(
            onBack = back,
            onOpen = { editId = it; returnTo = "transactions"; route = "editTransaction" }
        )
        "account" -> AccountFormScreen(accountId = editId, onClose = back)
        "categories" -> CategoriesScreen(onBack = back)
        "subscriptions" -> SubscriptionsScreen(onBack = back, onOpenForm = { editId = it; route = "subscriptionForm" })
        "subscriptionForm" -> SubscriptionFormScreen(id = editId, onClose = back)
        "loans" -> LoansScreen(onBack = back, onOpenForm = { editId = it; route = "loanForm" })
        "loanForm" -> LoanFormScreen(id = editId, onClose = back)
        "budgets" -> BudgetsScreen(
            onBack = back,
            onOpenForm = { editId = it; returnTo = "budgets"; route = "budgetForm" },
            onOpenHistory = { editId = it; returnTo = "budgets"; route = "budgetHistory" }
        )
        "budgetForm" -> BudgetFormScreen(id = editId, onClose = back)
        "budgetHistory" -> BudgetHistoryScreen(id = editId ?: 0L, onBack = back)
        "settings" -> SettingsScreen(themeMode = themeMode, onThemeChange = onThemeChange, onBack = back)
        else -> HomeScreen(
            onAddTransaction = { route = "newTransaction" },
            onNewAccount = { editId = null; route = "account" },
            onEditAccount = { editId = it; route = "account" },
            onOpen = { route = it },
            onOpenTransaction = { editId = it; returnTo = "home"; route = "editTransaction" },
            onOpenBudget = { editId = it; returnTo = "home"; route = "budgetForm" },
            onOpenBudgetHistory = { editId = it; returnTo = "home"; route = "budgetHistory" },
            onOpenSettings = { route = "settings" }
        )
    }
}
