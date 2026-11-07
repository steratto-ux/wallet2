package com.example.finanzecasa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.finanzecasa.FinanceApp
import com.example.finanzecasa.MainActivity
import com.example.finanzecasa.R
import com.example.finanzecasa.domain.NetWorthMath
import com.example.finanzecasa.ui.AppSettings
import com.example.finanzecasa.ui.budgetCards
import com.example.finanzecasa.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Widget per la schermata Home: patrimonio netto, il budget più "caldo" del mese e un tasto
 * per aggiungere subito una transazione. Con il blocco app attivo il saldo non viene mostrato.
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

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_NEW_TRANSACTION = "newTransaction"

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
                views.setTextViewText(R.id.widget_budget, "")
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null, 10))
                views.setOnClickPendingIntent(R.id.widget_add, openApp(context, OPEN_NEW_TRANSACTION, 11))
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as FinanceApp
            val dao = app.db.dao()
            val settings = AppSettings(context)
            val now = LocalDateTime.now()

            val balance: String
            if (settings.lockEnabled()) {
                balance = "••••••"
            } else {
                val upTo = System.currentTimeMillis() + 60_000
                val accounts = dao.observeBalances(upTo).first().sumOf { it.balanceCents }
                val net = NetWorthMath.netWorthCents(accounts, dao.activeLoans(), settings.includeLoans())
                balance = formatMoney(net)
            }

            val budgets = budgetCards(dao, now).first().filter { it.amountCents > 0 }
            val hottest = budgets.maxByOrNull { it.spentCents.toDouble() / it.amountCents }
            val budgetLine = when {
                hottest == null -> ""
                settings.lockEnabled() -> hottest.name
                else -> "${hottest.name}: ${hottest.spentCents * 100 / hottest.amountCents}%"
            }

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet)
                views.setTextViewText(R.id.widget_balance, balance)
                views.setTextViewText(R.id.widget_budget, budgetLine)
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null, 10))
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
