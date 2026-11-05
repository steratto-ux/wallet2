package com.example.finanzecasa.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.data.AccountType
import kotlinx.coroutines.launch

/** Creazione (accountId == null) e modifica di un account, con la configurazione delle carte di credito. */
@Composable
fun AccountFormScreen(accountId: Long?, onClose: () -> Unit, vm: ManageViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(accountId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AccountType.BANK) }
    var initialText by rememberSaveable { mutableStateOf("") }
    var cycleStartText by rememberSaveable { mutableStateOf("") }
    var debitDayText by rememberSaveable { mutableStateOf("") }
    var linkedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId) {
        if (accountId != null && !loaded) {
            vm.accountById(accountId)?.let { a ->
                name = a.name
                type = a.type
                initialText = centsToInput(a.initialBalanceCents)
                cycleStartText = a.cycleStartDay?.toString().orEmpty()
                debitDayText = a.debitDay?.toString().orEmpty()
                linkedId = a.linkedAccountId
            }
            loaded = true
        }
    }

    ScreenScaffold(if (accountId == null) "Nuovo account" else "Modifica account", onBack = onClose) { inner ->
        FormColumn(inner) {
            TextInput("Nome", name) { name = it; error = null }
            SegmentedChoice(
                options = listOf(
                    AccountType.BANK to "Conto",
                    AccountType.CASH to "Contanti",
                    AccountType.CREDIT_CARD to "Carta"
                ),
                selected = type,
                onSelect = { type = it; error = null }
            )
            MoneyInput("Valore iniziale (€)", initialText) { initialText = it; error = null }
            Text(
                "Il valore iniziale entra nel saldo dell'account e nel patrimonio netto. Può essere negativo.",
                style = MaterialTheme.typography.bodySmall
            )

            if (type == AccountType.CREDIT_CARD) {
                Text("Addebito automatico", style = MaterialTheme.typography.titleSmall)
                AccountField(
                    label = "Conto di addebito",
                    accounts = accounts.filter { it.type != AccountType.CREDIT_CARD && it.id != accountId },
                    selectedId = linkedId,
                    onSelect = { linkedId = it; error = null }
                )
                IntInput("Inizio periodo (giorno del mese)", cycleStartText) { cycleStartText = it; error = null }
                IntInput("Giorno di addebito (giorno del mese)", debitDayText) { debitDayText = it; error = null }
                Text(
                    "Esempio: inizio periodo 12, addebito 24. Il 24 di ogni mese viene creato un trasferimento " +
                        "dal conto alla carta con le spese del periodo chiuso (dal 12 al 11 del mese precedente). " +
                        "Senza conto di addebito l'addebito automatico resta spento.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val initial = if (initialText.isBlank()) 0L else parseSignedCents(initialText)
                    val cycle = cycleStartText.toIntOrNull()
                    val debit = debitDayText.toIntOrNull()
                    val wantsAuto = type == AccountType.CREDIT_CARD && linkedId != null
                    when {
                        name.isBlank() -> error = "Inserisci un nome."
                        initial == null -> error = "Valore iniziale non valido."
                        wantsAuto && (cycle == null || cycle !in 1..31 || debit == null || debit !in 1..31) ->
                            error = "Inserisci inizio periodo e giorno di addebito (da 1 a 31)."
                        else -> scope.launch {
                            val problem = vm.saveAccount(
                                accountId, name.trim(), type, initial,
                                cycle.takeIf { wantsAuto }, debit.takeIf { wantsAuto }, linkedId.takeIf { wantsAuto }
                            )
                            if (problem != null) error = problem else onClose()
                        }
                    }
                }
            ) { Text("Salva") }
        }
    }
}
