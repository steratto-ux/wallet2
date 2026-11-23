package com.example.finanzecasa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.MainActivity
import com.example.finanzecasa.R
import com.example.finanzecasa.domain.CalendarMath
import com.example.finanzecasa.domain.WidgetDeadlines
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Widget con il pulsante "+ Nuova transazione" che apre l'app sul modulo. Se lo si attiva in Configurazione, sotto
 * il pulsante mostra anche le prossime tre scadenze (abbonamenti, rate, ricorrenti) con l'importo. Per impostazione
 * predefinita non legge nessun dato, quindi non mostra nulla di riservato nemmeno con il blocco app attivo.
 */
class WalletWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // La lettura delle scadenze usa il database: si fa fuori dal thread principale
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rows = runCatching { loadDeadlines(context) }.getOrNull()
                update(context, appWidgetManager, appWidgetIds, rows)
            } finally {
                pending.finish()
            }
        }
    }

    /** Una riga delle scadenze: testo a sinistra e importo a destra. */
    private data class DeadlineRow(val text: String, val amount: String)

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_NEW_TRANSACTION = "newTransaction"
        const val OPEN_ANALYSIS = "analysis"
        const val OPEN_CALENDAR = "calendar"

        /** Aggiorna tutti i widget presenti (chiamato anche dall'app e dal lavoro periodico). */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val rows = runCatching { loadDeadlines(context) }.getOrNull()
            update(context, manager, ids, rows)
        }

        /** Null se le scadenze non sono attive; una lista vuota se attive ma non ce ne sono. */
        private suspend fun loadDeadlines(context: Context): List<DeadlineRow>? {
            if (!AppSettings(context).widgetDeadlinesEnabled()) return null
            val dao = (context.applicationContext as FinanceApp).db.dao()
            val today = LocalDate.now()
            val all = CalendarMath.scheduled(
                dao.activeSubscriptions(), dao.activeLoans(), dao.activeRecurring(),
                today, today.plusDays(WidgetDeadlines.HORIZON_DAYS)
            )
            return WidgetDeadlines.pick(all, today).map {
                DeadlineRow(WidgetDeadlines.whenLabel(it.date, today) + " · " + it.title, formatMoney(it.cents))
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray, deadlines: List<DeadlineRow>?) {
            val rowViews = listOf(
                Triple(R.id.widget_row1, R.id.widget_t1, R.id.widget_a1),
                Triple(R.id.widget_row2, R.id.widget_t2, R.id.widget_a2),
                Triple(R.id.widget_row3, R.id.widget_t3, R.id.widget_a3)
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet)
                views.setOnClickPendingIntent(R.id.widget_add, openApp(context, OPEN_NEW_TRANSACTION, 11))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_NEW_TRANSACTION, 11))
                if (deadlines == null) {
                    views.setViewVisibility(R.id.widget_list, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_list, View.VISIBLE)
                    views.setOnClickPendingIntent(R.id.widget_list, openApp(context, OPEN_CALENDAR, 12))
                    val lines = deadlines.ifEmpty { listOf(DeadlineRow("Nessuna scadenza nei prossimi 30 giorni", "")) }
                    rowViews.forEachIndexed { i, (row, text, amount) ->
                        if (i < lines.size) {
                            views.setViewVisibility(row, View.VISIBLE)
                            views.setTextViewText(text, lines[i].text)
                            views.setTextViewText(amount, lines[i].amount)
                        } else {
                            views.setViewVisibility(row, View.GONE)
                        }
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun openApp(context: Context, open: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                if (open != null) putExtra(EXTRA_OPEN, open)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
