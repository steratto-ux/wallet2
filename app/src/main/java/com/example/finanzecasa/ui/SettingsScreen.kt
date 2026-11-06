package com.example.finanzecasa.ui

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.alerts.AlertsChecker
import com.example.finanzecasa.backup.AutoBackup
import com.example.finanzecasa.backup.BackupRepository
import com.example.finanzecasa.widget.WalletWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Import/esportazione CSV, backup completo e ripristino. Lo stato dell'ultima operazione resta visibile. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FinanceApp

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private fun perform(start: String, block: suspend () -> String) {
        viewModelScope.launch {
            _status.value = start
            _status.value = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { it.message ?: "Errore sconosciuto" } }
        }
    }

    fun importCsv(uri: Uri) = perform("Import in corso…") {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                .bufferedReader().use { it.readText() }
            app.importRepository.import(text).summary()
        } catch (e: Exception) {
            "Errore nell'import: ${e.message}"
        }
    }

    fun exportCsv(uri: Uri) = perform("Esportazione in corso…") {
        try {
            val csv = app.exportRepository.buildCsv()
            getApplication<Application>().contentResolver.openOutputStream(uri)!!
                .bufferedWriter().use { it.write(csv) }
            "Esportazione completata: ${csv.count { it == '\n' }} righe circa (compresa l'intestazione)."
        } catch (e: Exception) {
            "Errore nell'esportazione: ${e.message}"
        }
    }

    fun createBackup(uri: Uri) = perform("Creazione del backup…") {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                BackupRepository(app, app.db).writeBackup(out)
            }
            "Backup completo salvato."
        } catch (e: Exception) {
            "Errore nel backup: ${e.message}"
        }
    }

    fun restoreBackup(uri: Uri) = perform("Ripristino in corso…") {
        try {
            val result = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                BackupRepository(app, app.db).restore(input)
            }
            runCatching { WalletWidgetProvider.refreshAll(app) }
            "Ripristino completato: ${result.rowCount} righe e ${result.receiptCount} foto."
        } catch (e: IllegalArgumentException) {
            e.message ?: "File non valido."
        } catch (e: Exception) {
            "Errore nel ripristino: ${e.message}"
        }
    }

    fun backupNow() = perform("Backup in corso…") {
        try {
            AutoBackup(app, app.db).runNow()
            "Backup creato."
        } catch (e: Exception) {
            "Errore nel backup: ${e.message}"
        }
    }

    fun checkAlertsNow() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { AlertsChecker(app, app.db).run() } }
    }
}

private val lastBackupFmt = DateTimeFormatter.ofPattern("d MMM yyyy 'alle' HH:mm", Locale.ITALIAN)

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenAccountsOrder: () -> Unit,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val status by vm.status.collectAsState()

    var includeLoans by remember { mutableStateOf(settings.includeLoans()) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled()) }
    var lock by remember { mutableStateOf(settings.lockEnabled()) }
    var autoBackup by remember { mutableStateOf(settings.autoBackupEnabled()) }
    var folder by remember { mutableStateOf(settings.backupFolder()) }
    var message by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.createBackup(uri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settings.setBackupFolder(uri.toString())
            folder = uri.toString()
            message = "Cartella scelta. Il prossimo backup verrà copiato anche lì."
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
        settings.setNotificationsEnabled(granted)
        message = if (granted) "Notifiche attive." else "Senza il permesso le notifiche non possono essere mostrate."
        if (granted) vm.checkAlertsNow()
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

            Text("Generale", style = MaterialTheme.typography.titleMedium)
            AppCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    "Includi i debiti dei prestiti",
                    "Nel patrimonio netto si sottrae il capitale ancora da restituire sui prestiti.",
                    includeLoans
                ) { includeLoans = it; settings.setIncludeLoans(it) }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text("Account", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Cambia l'ordine degli account o nascondi quelli che non usi più.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenAccountsOrder, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Ordina e nascondi account")
                }
            }

            Text("Avvisi e sicurezza", style = MaterialTheme.typography.titleMedium)
            AppCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    "Notifiche",
                    "Avviso quando un budget arriva all'80% e al 100%, e il giorno prima di abbonamenti, rate, " +
                        "ricorrenti e addebiti della carta.",
                    notifications
                ) { on ->
                    if (!on) {
                        notifications = false
                        settings.setNotificationsEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notifications = true
                        settings.setNotificationsEnabled(true)
                        vm.checkAlertsNow()
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    "Blocco dell'app",
                    "All'apertura chiede lo sblocco del telefono (impronta, PIN, sequenza o password). " +
                        "Nasconde anche il saldo nel widget e le anteprime nelle app recenti.",
                    lock
                ) { on ->
                    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (on && !keyguard.isDeviceSecure) {
                        message = "Imposta prima un blocco schermo (PIN, sequenza, password o impronta) nelle impostazioni del telefono."
                    } else {
                        lock = on
                        settings.setLockEnabled(on)
                    }
                }
            }

            Text("Backup completo", style = MaterialTheme.typography.titleMedium)
            AppCard(Modifier.fillMaxWidth()) {
                Text("Salva e ripristina tutto", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Un solo file con account, transazioni, categorie, budget, abbonamenti, prestiti, ricorrenti, " +
                        "obiettivi, tag e foto degli scontrini. Il ripristino sostituisce tutti i dati attuali.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { backupPicker.launch("il-mio-wallet-backup-${LocalDate.now()}.zip") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Crea backup") }
                OutlinedButton(
                    onClick = { restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Ripristina da backup") }
            }
            AppCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    "Backup automatico settimanale",
                    "Una copia ogni 7 giorni nella memoria dell'app e, se scegli una cartella, anche lì. " +
                        "Scegli una cartella fuori dall'app (ad esempio Google Drive) per non perdere i dati se disinstalli.",
                    autoBackup
                ) { autoBackup = it; settings.setAutoBackupEnabled(it) }
                val chosenFolder = folder
                Text(
                    if (chosenFolder != null) "Cartella scelta: ${Uri.parse(chosenFolder).lastPathSegment ?: chosenFolder}" else "Nessuna cartella scelta.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                val last = settings.lastAutoBackupMillis()
                if (last > 0) {
                    Text(
                        "Ultimo backup automatico: " +
                            Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(lastBackupFmt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Scegli la cartella dei backup")
                }
                OutlinedButton(onClick = { vm.backupNow() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Esegui un backup adesso")
                }
            }

            Text("CSV", style = MaterialTheme.typography.titleMedium)
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
                Text("Esporta in CSV", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Salva tutte le transazioni con lo stesso tracciato dell'import, compresi i valori iniziali dei conti. " +
                        "Non contiene le carte di credito, gli abbonamenti, i prestiti e i budget: per questi usa il backup completo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { exportPicker.launch("il-mio-wallet-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Esporta CSV") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            text = "Ripristinare il backup? Tutti i dati attuali dell'app verranno sostituiti con quelli del file.",
            confirmLabel = "Ripristina",
            onConfirm = { restoreUri = null; vm.restoreBackup(uri) },
            onDismiss = { restoreUri = null }
        )
    }
}
