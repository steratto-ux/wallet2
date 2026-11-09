package com.example.finanzecasa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.finanzecasa.MainActivity
import com.example.finanzecasa.R

/**
 * Widget 2 x 1 con un solo pulsante: apre l'app direttamente sul modulo di una nuova transazione.
 * Non legge nessun dato, quindi non mostra nulla di riservato nemmeno con il blocco app attivo.
 */
class WalletWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_NEW_TRANSACTION = "newTransaction"
        const val OPEN_ANALYSIS = "analysis"

        /** Rimette il pulsante in tutti i widget presenti (chiamato anche dall'app e dal lavoro periodico). */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet)
                views.setOnClickPendingIntent(R.id.widget_add, openApp(context, OPEN_NEW_TRANSACTION, 11))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_NEW_TRANSACTION, 11))
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
