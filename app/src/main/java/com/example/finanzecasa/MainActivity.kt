package com.example.finanzecasa

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.finanzecasa.alerts.AlertsChecker
import com.example.finanzecasa.domain.DueItemsProcessor
import com.example.finanzecasa.ui.AccountFormScreen
import com.example.finanzecasa.ui.AccountsOrderScreen
import com.example.finanzecasa.ui.AnalysisScreen
import com.example.finanzecasa.ui.GuideScreen
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.AppTheme
import com.example.finanzecasa.ui.BudgetHistoryScreen
import com.example.finanzecasa.ui.BudgetFormScreen
import com.example.finanzecasa.ui.BudgetsScreen
import com.example.finanzecasa.ui.CategoriesScreen
import com.example.finanzecasa.ui.GoalFormScreen
import com.example.finanzecasa.ui.GoalsScreen
import com.example.finanzecasa.ui.HomeScreen
import com.example.finanzecasa.ui.LoanFormScreen
import com.example.finanzecasa.ui.LoansScreen
import com.example.finanzecasa.ui.NewTransactionScreen
import com.example.finanzecasa.ui.IrregularFormScreen
import com.example.finanzecasa.ui.IrregularScreen
import com.example.finanzecasa.ui.RecurringFormScreen
import com.example.finanzecasa.ui.RecurringScreen
import com.example.finanzecasa.ui.ReportsScreen
import com.example.finanzecasa.ui.SettingsScreen
import com.example.finanzecasa.ui.SubscriptionFormScreen
import com.example.finanzecasa.ui.SubscriptionsScreen
import com.example.finanzecasa.ui.ThemeMode
import com.example.finanzecasa.ui.TransactionsScreen
import com.example.finanzecasa.widget.WalletWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var settings: AppSettings
    private val locked = mutableStateOf(false)
    private val openRequest = mutableStateOf<String?>(null)
    private var lastStopAt = 0L

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) locked.value = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        locked.value = settings.lockEnabled()
        // Apertura da widget: solo alla prima creazione, non quando lo schermo ruota
        if (savedInstanceState == null) openRequest.value = intent?.getStringExtra(WalletWidgetProvider.EXTRA_OPEN)

        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode()) }
            AppTheme(themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot(
                            themeMode = themeMode,
                            onThemeChange = { mode -> themeMode = mode; settings.setThemeMode(mode) },
                            openRequest = openRequest.value,
                            onOpenHandled = { openRequest.value = null }
                        )
                        if (locked.value) LockScreen(onUnlock = ::requestUnlock)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(WalletWidgetProvider.EXTRA_OPEN)?.let { openRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Con il blocco attivo l'anteprima nelle app recenti e gli screenshot sono disattivati
        if (settings.lockEnabled()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Ci si blocca dopo più di 30 secondi lontani dall'app
        if (settings.lockEnabled() && lastStopAt != 0L &&
            SystemClock.elapsedRealtime() - lastStopAt > LOCK_AFTER_MILLIS
        ) {
            locked.value = true
        }
        if (!settings.lockEnabled()) locked.value = false

        // Recupera scadenze, controlla gli avvisi e aggiorna il widget
        val app = application as FinanceApp
        lifecycleScope.launch(Dispatchers.IO) {
            DueItemsProcessor(app.db).run(LocalDate.now())
            runCatching { AlertsChecker(app, app.db).run() }
            runCatching { WalletWidgetProvider.refreshAll(app) }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStopAt = SystemClock.elapsedRealtime()
    }

    private fun requestUnlock() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val intent = if (keyguard.isDeviceSecure) {
            keyguard.createConfirmDeviceCredentialIntent("Il mio wallet", "Sblocca per vedere i tuoi dati")
        } else {
            null
        }
        // Se il telefono non ha più un blocco schermo non si resta chiusi fuori
        if (intent == null) locked.value = false else unlockLauncher.launch(intent)
    }

    companion object {
        private const val LOCK_AFTER_MILLIS = 30_000L
    }
}

/** Schermata di blocco: copre tutta l'app finché non si sblocca il telefono. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Surface(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }, // assorbe i tocchi sotto
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Il mio wallet è bloccato", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Sblocca") }
        }
    }
}

/** Navigazione semplice a stato: una "rotta" e, per i moduli, l'id dell'elemento in modifica (null = nuovo). */
@Composable
private fun AppRoot(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    openRequest: String?,
    onOpenHandled: () -> Unit
) {
    var route by rememberSaveable { mutableStateOf("home") }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnTo by rememberSaveable { mutableStateOf("home") } // dove tornare dopo aver modificato transazione o budget

    // Richiesta dal widget: "+ Nuova transazione"
    LaunchedEffect(openRequest) {
        if (openRequest == WalletWidgetProvider.OPEN_NEW_TRANSACTION) route = "newTransaction"
        if (openRequest == WalletWidgetProvider.OPEN_ANALYSIS) route = "analysis"
        if (openRequest != null) onOpenHandled()
    }

    val back: () -> Unit = {
        route = when (route) {
            "subscriptionForm", "loanForm", "recurringForm" -> returnTo
            "irregularForm" -> "irregular"
            "goalForm" -> "goals"
            "accountsOrder", "guide" -> "settings"
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
            onOpen = { editId = it; returnTo = "transactions"; route = "editTransaction" },
            onOpenScheduled = { form, id -> editId = id; returnTo = "transactions"; route = form }
        )
        "account" -> AccountFormScreen(accountId = editId, onClose = back)
        "accountsOrder" -> AccountsOrderScreen(onBack = back)
        "categories" -> CategoriesScreen(onBack = back)
        "subscriptions" -> SubscriptionsScreen(onBack = back, onOpenForm = { editId = it; returnTo = "subscriptions"; route = "subscriptionForm" })
        "subscriptionForm" -> SubscriptionFormScreen(id = editId, onClose = back)
        "loans" -> LoansScreen(onBack = back, onOpenForm = { editId = it; returnTo = "loans"; route = "loanForm" })
        "loanForm" -> LoanFormScreen(id = editId, onClose = back)
        "recurring" -> RecurringScreen(onBack = back, onOpenForm = { editId = it; returnTo = "recurring"; route = "recurringForm" })
        "recurringForm" -> RecurringFormScreen(id = editId, onClose = back)
        "irregular" -> IrregularScreen(onBack = back, onOpenForm = { editId = it; route = "irregularForm" })
        "irregularForm" -> IrregularFormScreen(id = editId, onClose = back)
        "goals" -> GoalsScreen(onBack = back, onOpenForm = { editId = it; route = "goalForm" })
        "goalForm" -> GoalFormScreen(id = editId, onClose = back)
        "reports" -> ReportsScreen(onBack = back)
        "analysis" -> AnalysisScreen(onBack = back)
        "guide" -> GuideScreen(onBack = back)
        "budgets" -> BudgetsScreen(
            onBack = back,
            onOpenForm = { editId = it; returnTo = "budgets"; route = "budgetForm" },
            onOpenHistory = { editId = it; returnTo = "budgets"; route = "budgetHistory" }
        )
        "budgetForm" -> BudgetFormScreen(id = editId, onClose = back)
        "budgetHistory" -> BudgetHistoryScreen(id = editId ?: 0L, onBack = back)
        "settings" -> SettingsScreen(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onOpenAccountsOrder = { route = "accountsOrder" },
            onOpenGuide = { route = "guide" },
            onBack = back
        )
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
