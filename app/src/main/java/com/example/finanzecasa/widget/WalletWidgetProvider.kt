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
import com.example.finanzecasa.domain.NetWorthMath
import com.example.finanzecasa.domain.RuleLoader
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Widget per la schermata Home: grafico a barre della regola 50/30/20 del mese, saldo totale (se scelto
 * nella configurazione del widget) e un tasto per aggiungere subito una transazione.
 * Con il blocco app attivo il saldo e gli importi non vengono mostrati.
 */
class WalletWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                // Un errore nel calcolo dei dati non deve far chiudere l'app: il widget resta com'è
                // e il tasto "Nuova transazione" continua a funzionare.
                showFallback(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = AppSettings(context)
        appWidgetIds.forEach { settings.clearWidget(it) }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_NEW_TRANSACTION = "newTransaction"
        const val OPEN_ANALYSIS = "analysis"

        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetProvider::class.java))
            if (ids.isEmpty()) return
            try {
                refresh(context, manager, ids)
            } catch (e: Exception) {
                showFallback(context, manager, ids)
            }
        }

        /** Widget minimo, senza dati, ma con i tasti che aprono l'app. */
        private fun showFallback(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet)
                views.setTextViewText(R.id.widget_balance, "—")
                views.setViewVisibility(R.id.widget_balance, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_ANALYSIS, 10))
                views.setOnClickPendingIntent(R.id.widget_add, openApp(context, OPEN_NEW_TRANSACTION, 11))
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as FinanceApp
            val dao = app.db.dao()
            val settings = AppSettings(context)
            val now = LocalDateTime.now()
            val locked = settings.lockEnabled()

            // Regola 50/30/20 del mese in corso, disegnata una volta sola per tutti i widget
            val month = YearMonth.from(now)
            val rule = RuleLoader.month(dao, month, now)
            val chart = WidgetChart.render(context, rule, showAmounts = !locked)
            val title = "50/30/20 · " + month.format(DateTimeFormatter.ofPattern("LLLL", Locale.ITALIAN))

            val balance: String = if (locked) {
                "••••••"
            } else {
                val upTo = System.currentTimeMillis() + 60_000
                val accounts = dao.observeBalances(upTo).first().sumOf { it.balanceCents }
                formatMoney(NetWorthMath.netWorthCents(accounts, dao.activeLoans(), settings.includeLoans()))
            }

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_balance, balance)
                views.setViewVisibility(R.id.widget_balance, if (settings.widgetShowBalance(id)) View.VISIBLE else View.GONE)
                views.setImageViewBitmap(R.id.widget_chart, chart)
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_ANALYSIS, 10))
                views.setOnClickPendingIntent(R.id.widget_add, openApp(context, OPEN_NEW_TRANSACTION, 11))
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
