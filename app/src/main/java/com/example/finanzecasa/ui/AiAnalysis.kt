package com.example.finanzecasa.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.ai.AiModel
import com.example.finanzecasa.ai.AiResult
import com.example.finanzecasa.ai.AnthropicClient
import com.example.finanzecasa.data.AccountType
import com.example.finanzecasa.data.Cadence
import com.example.finanzecasa.domain.AiAccountLine
import com.example.finanzecasa.domain.AiBudgetLine
import com.example.finanzecasa.domain.AiGoalLine
import com.example.finanzecasa.domain.AiLoanLine
import com.example.finanzecasa.domain.AiReport
import com.example.finanzecasa.domain.AiRuleLine
import com.example.finanzecasa.domain.AiSnapshot
import com.example.finanzecasa.domain.AiSubscriptionLine
import com.example.finanzecasa.domain.EmergencyFund
import com.example.finanzecasa.domain.RuleLoader
import com.example.finanzecasa.domain.toMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Done(val text: String, val atMillis: Long) : AiUiState
    data class Error(val message: String) : AiUiState
}

/** Prepara il riepilogo dei dati, lo manda al servizio e tiene l'ultimo report (anche se si chiude la finestra mentre aspetta). */
class AiAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FinanceApp).db.dao()
    private val settings = AppSettings(application)

    private val stateFlow = MutableStateFlow<AiUiState>(initialState())
    val state: StateFlow<AiUiState> = stateFlow.asStateFlow()

    private fun initialState(): AiUiState {
        val text = settings.aiLastReport()
        return if (text.isNotBlank()) AiUiState.Done(text, settings.aiLastReportMillis()) else AiUiState.Idle
    }

    fun apiKey(): String = settings.aiApiKey()
    fun saveKey(key: String) = settings.setAiApiKey(key)
    fun model(): AiModel = AiModel.fromId(settings.aiModelId())

    fun run(analysis: AnalysisData?) {
        if (stateFlow.value == AiUiState.Loading) return
        val key = settings.aiApiKey()
        if (key.isBlank()) {
            stateFlow.value = AiUiState.Error("Inserisci prima la chiave API.")
            return
        }
        stateFlow.value = AiUiState.Loading
        viewModelScope.launch {
            val result = try {
                val prompt = AiReport.buildPrompt(buildSnapshot(analysis))
                AnthropicClient.complete(key, model(), AiReport.SYSTEM_PROMPT, prompt)
            } catch (e: Exception) {
                AiResult.Failure("Non sono riuscito a preparare i dati: ${e.message}")
            }
            stateFlow.value = when (result) {
                is AiResult.Success -> {
                    val at = System.currentTimeMillis()
                    settings.setAiLastReport(result.text, at)
                    AiUiState.Done(result.text, at)
                }
                is AiResult.Failure -> AiUiState.Error(result.message)
            }
        }
    }

    fun dismissError() {
        if (stateFlow.value is AiUiState.Error) stateFlow.value = initialState()
    }

    /** Legge dal database tutto ciò che serve al riepilogo; ciò che l'Analisi ha già calcolato arriva in [d]. */
    private suspend fun buildSnapshot(d: AnalysisData?): AiSnapshot {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val nowMs = now.plusMinutes(1).toMillis()
        val current = YearMonth.from(today)
        val from = current.minusMonths(5).atDay(1).atStartOfDay().toMillis()
        val yearFrom = today.withDayOfYear(1).atStartOfDay().toMillis()

        val balances = dao.observeBalances(nowMs).first()
        fun byMonth(list: List<com.example.finanzecasa.data.MonthTotal>) = list.associate { YearMonth.parse(it.month) to it.totalCents }
        val income = byMonth(dao.observeIncomeByMonth(from, nowMs).first())
        val expenseList = dao.observeExpenseByMonth(from, nowMs).first()
        val expense = byMonth(expenseList)
        val saved = byMonth(dao.observeSavingsByMonth(from, nowMs).first())
        val items = RuleLoader.toItems(dao.spendRowsOnce(from, nowMs))

        val average = EmergencyFund.averageMonthly(expenseList, current, 6)
        val emergencyIds = settings.emergencyAccountIds()
        val emergencyMonths = if (emergencyIds.isNotEmpty() && average != null && average > 0) {
            EmergencyFund.monthsCovered(balances.filter { it.id in emergencyIds }.sumOf { it.balanceCents }, average).toInt()
        } else null

        val subs = dao.observeSubscriptions().first().filter { it.active }.map {
            val perYear = when (it.cadence) { Cadence.WEEKLY -> 52L; Cadence.MONTHLY -> 12L; Cadence.YEARLY -> 1L }
            AiSubscriptionLine(it.name, it.amountCents * perYear, it.toReview)
        }
        val loans = dao.observeLoans().first().filter { it.remainingInstallments > 0 }.map {
            AiLoanLine(it.name, it.installmentCents, it.remainingInstallments, it.annualRatePercent)
        }
        val balanceOf = balances.associate { it.id to it.balanceCents }
        val goals = dao.observeGoals().first().map { g ->
            val net = g.categoryId?.let {
                dao.observeCategoryNet(it, java.time.LocalDate.ofEpochDay(g.createdEpochDay).atStartOfDay().toMillis(), nowMs).first()
            } ?: 0L
            AiGoalLine(
                g.name, g.manualCents + (g.accountId?.let { balanceOf[it] } ?: 0L) + net, g.targetCents,
                g.deadlineEpochDay?.let { java.time.LocalDate.ofEpochDay(it) }
            )
        }
        val budgets = budgetCards(dao, now).first().map { AiBudgetLine(it.name, it.spentCents, it.amountCents) }

        val lastClosed = d?.rule?.get(current.minusMonths(1))?.takeIf { it.incomeCents > 0 }
        val rule = lastClosed?.let {
            AiRuleLine(
                current.minusMonths(1), it.percentOfIncome(it.needCents), it.percentOfIncome(it.wantCents),
                it.percentOfIncome(it.savingsCents)
            )
        }

        return AiSnapshot(
            today = today,
            netWorthCents = balances.sumOf { it.balanceCents },
            accounts = balances.filter { !it.hidden }.map { AiAccountLine(it.name, accountTypeLabel(it.type), it.balanceCents) },
            months = AiReport.monthLines(current, income, expense, saved),
            categories = AiReport.categoryLines(items, current),
            forecastEndOfMonthCents = d?.forecast?.projectedEndCents,
            fixedMonthlyCents = d?.baseline?.fixedCents?.takeIf { it > 0 },
            rule = rule,
            loans = loans,
            subscriptions = subs,
            goals = goals,
            budgets = budgets,
            savedYearCents = dao.observeSavedBetween(yearFrom, nowMs).first(),
            provisionedYearCents = dao.observeProvisionedBetween(yearFrom, nowMs).first(),
            liquidCents = balances.filter { it.type == AccountType.BANK || it.type == AccountType.CASH }.sumOf { it.balanceCents },
            averageMonthlyExpenseCents = average,
            emergencyMonths = emergencyMonths
        )
    }

    private fun accountTypeLabel(t: AccountType) = when (t) {
        AccountType.BANK -> "conto"
        AccountType.CASH -> "contanti"
        AccountType.CREDIT_CARD -> "carta di credito"
        AccountType.INVESTMENT -> "investimenti"
    }
}

private val reportDateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN)

/** Finestra dell'Analisi AI: chiede la chiave la prima volta, poi avvia l'analisi e mostra il report. */
@Composable
fun AiAnalysisDialog(analysis: AnalysisData?, onDismiss: () -> Unit, vm: AiAnalysisViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var hasKey by remember { mutableStateOf(vm.apiKey().isNotBlank()) }
    var keyText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Analisi AI") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasKey) {
                    Text(
                        "Per usare l'Analisi AI serve una chiave API personale di Anthropic (la crei su console.anthropic.com, " +
                            "sezione API keys). Ha un costo a consumo, in genere pochi centesimi per analisi. " +
                            "La chiave resta salvata solo su questo telefono.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = keyText, onValueChange = { keyText = it }, label = { Text("Chiave API") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else when (val st = state) {
                    AiUiState.Idle -> {
                        Text(AI_DISCLOSURE, style = MaterialTheme.typography.bodyMedium)
                        Text("Modello: ${vm.model().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AiUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text("Sto analizzando i tuoi dati… può volerci un minuto.")
                    }
                    is AiUiState.Done -> {
                        val at = Instant.ofEpochMilli(st.atMillis).atZone(ZoneId.systemDefault()).format(reportDateFmt)
                        Text("Report del $at", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(st.text, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            "Generato da un'intelligenza artificiale sui dati dell'app: può sbagliare e non è una consulenza finanziaria.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AiUiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                !hasKey -> TextButton(enabled = keyText.isNotBlank(), onClick = {
                    vm.saveKey(keyText); hasKey = true; keyText = ""
                }) { Text("Salva chiave") }
                state == AiUiState.Loading -> TextButton(onClick = onDismiss) { Text("Chiudi") }
                state is AiUiState.Done -> Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString((state as AiUiState.Done).text)) }) { Text("Copia") }
                    TextButton(onClick = { vm.run(analysis) }) { Text("Nuova analisi") }
                }
                state is AiUiState.Error -> TextButton(onClick = { vm.run(analysis) }) { Text("Riprova") }
                else -> Button(onClick = { vm.run(analysis) }) { Text("Avvia analisi") }
            }
        },
        dismissButton = { if (state != AiUiState.Loading) TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

private const val AI_DISCLOSURE =
    "L'analisi invia ad Anthropic, tramite la tua chiave, un riepilogo dei tuoi dati: saldi e nomi dei conti, totali mensili " +
        "di entrate, uscite e risparmio, spese per categoria, budget, prestiti, abbonamenti e obiettivi. " +
        "Non vengono inviati i titoli, le note e le foto delle singole transazioni.\n\n" +
        "Il report che ricevi viene salvato sul telefono, così puoi rileggerlo senza rifare l'analisi."

/** Scheda della Configurazione: chiave API e modello per l'Analisi AI. */
@Composable
fun AiSettingsCard(settings: AppSettings) {
    var key by remember { mutableStateOf(settings.aiApiKey()) }
    var model by remember { mutableStateOf(AiModel.fromId(settings.aiModelId())) }
    var saved by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Text("Analisi AI", style = MaterialTheme.typography.titleSmall)
        Text(
            "Nella schermata Analisi, il pulsante Analisi AI scrive un report sullo stato delle tue finanze. " +
                "Serve una chiave API personale di Anthropic (console.anthropic.com → API keys), con costo a consumo. " +
                "La chiave resta solo su questo telefono. Vengono inviati solo riepiloghi e totali, mai i titoli delle singole transazioni.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it; saved = false }, label = { Text("Chiave API") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        DropdownField(
            label = "Modello",
            value = model.label,
            entries = AiModel.values().map { m -> m.label to { model = m; settings.setAiModelId(m.id) } }
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { settings.setAiApiKey(key); saved = true }, enabled = key.trim() != settings.aiApiKey()) { Text("Salva chiave") }
            if (settings.aiApiKey().isNotBlank()) {
                OutlinedButton(onClick = { settings.setAiApiKey(""); key = ""; saved = false }) { Text("Rimuovi") }
            }
        }
        if (saved) Text("Chiave salvata.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}
