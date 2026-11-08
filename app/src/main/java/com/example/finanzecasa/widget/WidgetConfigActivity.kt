package com.example.finanzecasa.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Scelta delle opzioni del widget: si apre quando lo si aggiunge (e dalla pressione prolungata, per cambiarle). */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Se si esce senza confermare, il widget non viene aggiunto
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val settings = AppSettings(this)

        setContent {
            AppTheme(settings.themeMode()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showBalance by remember { mutableStateOf(settings.widgetShowBalance(widgetId)) }
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Widget Il mio wallet", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Il widget mostra, in un grafico a barre, come stai rispettando la regola 50/30/20 nel mese " +
                                "in corso, con il tasto per aggiungere una nuova transazione.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Mostra il saldo totale", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Se il blocco dell'app è attivo il saldo viene comunque nascosto.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = showBalance, onCheckedChange = { showBalance = it })
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                settings.setWidgetShowBalance(widgetId, showBalance)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                val app = applicationContext
                                // Con una schermata di configurazione tocca all'app disegnare il widget la prima volta
                                CoroutineScope(Dispatchers.IO).launch { WalletWidgetProvider.refreshAll(app) }
                                finish()
                            }
                        ) { Text("Fine") }
                    }
                }
            }
        }
    }
}
