package com.example.finanzecasa.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Preferenze dell'app (per ora solo il tema), salvate sul telefono. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
    }
}

/** Import ed esportazione del CSV. Lo stato dell'ultima operazione resta visibile nella schermata. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _status.value = "Import in corso…"
            _status.value = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    app.importRepository.import(text).summary()
                }.getOrElse { "Errore nell'import: ${it.message}" }
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _status.value = "Esportazione in corso…"
            _status.value = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = app.exportRepository.buildCsv()
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!
                        .bufferedWriter().use { it.write(csv) }
                    "Esportazione completata: ${csv.count { it == '\n' }} righe circa (compresa l'intestazione)."
                }.getOrElse { "Errore nell'esportazione: ${it.message}" }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val status by vm.status.collectAsState()
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    ScreenScaffold("Configurazione", onBack = onBack) { inner ->
        FormColumn(inner) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            SegmentedChoice(
                options = listOf(
                    ThemeMode.SYSTEM to "Automatico",
                    ThemeMode.LIGHT to "Chiaro",
                    ThemeMode.DARK to "Scuro"
                ),
                selected = themeMode,
                onSelect = onThemeChange
            )
            Text(
                "Automatico segue l'impostazione del telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Dati", style = MaterialTheme.typography.titleMedium)
            AppCard(Modifier.fillMaxWidth()) {
                Text("Importa da CSV", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Legge un file nel formato Cashew (modello o esportazione completa), compreso il file creato da \"Esporta in CSV\". " +
                        "Le transazioni già presenti non vengono duplicate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { importPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Importa CSV") }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text("Esporta in CSV (backup)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Salva tutte le transazioni con lo stesso tracciato dell'import, compresi i valori iniziali dei conti. " +
                        "Non contiene i dettagli delle carte di credito, gli abbonamenti, i prestiti e i budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { exportPicker.launch("il-mio-wallet-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Esporta CSV") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
