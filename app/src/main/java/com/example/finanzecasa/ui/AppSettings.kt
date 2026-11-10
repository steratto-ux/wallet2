package com.example.finanzecasa.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Preferenze dell'app salvate sul telefono. Le chiavi non cambiano mai tra una versione e l'altra:
 * quelle nuove hanno sempre un valore predefinito, così le impostazioni esistenti restano valide.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
    }

    // --- Patrimonio netto
    fun includeLoans(): Boolean = prefs.getBoolean("include_loans", false)
    fun setIncludeLoans(on: Boolean) { prefs.edit().putBoolean("include_loans", on).apply() }

    /** Emette il valore attuale e poi ogni cambiamento (la schermata principale si aggiorna da sola). */
    fun includeLoansFlow(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "include_loans") trySend(includeLoans())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(includeLoans())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // --- Notifiche
    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications", false)
    fun setNotificationsEnabled(on: Boolean) { prefs.edit().putBoolean("notifications", on).apply() }

    /** Avvisi già inviati (chiavi come "budget-3-2026-11-80"): ogni avviso parte una volta sola. */
    fun sentAlerts(): Set<String> = prefs.getStringSet("sent_alerts", emptySet()) ?: emptySet()
    fun setSentAlerts(keys: Set<String>) { prefs.edit().putStringSet("sent_alerts", keys.toSet()).apply() }

    // --- Blocco app
    fun lockEnabled(): Boolean = prefs.getBoolean("lock", false)
    fun setLockEnabled(on: Boolean) { prefs.edit().putBoolean("lock", on).apply() }

    // --- Backup automatico
    fun autoBackupEnabled(): Boolean = prefs.getBoolean("auto_backup", true)
    fun setAutoBackupEnabled(on: Boolean) { prefs.edit().putBoolean("auto_backup", on).apply() }

    fun backupFolder(): String? = prefs.getString("backup_folder", null)
    fun setBackupFolder(uri: String?) { prefs.edit().putString("backup_folder", uri).apply() }

    fun lastAutoBackupMillis(): Long = prefs.getLong("last_auto_backup", 0L)
    fun setLastAutoBackupMillis(v: Long) { prefs.edit().putLong("last_auto_backup", v).apply() }

    // --- Analisi: obiettivo di risparmio mensile (0 = non impostato)
    fun savingsTargetCents(): Long = prefs.getLong("savings_target", 0L)
    fun setSavingsTargetCents(v: Long) { prefs.edit().putLong("savings_target", v).apply() }

    // --- Previsione: voci stimate dallo storico che l'utente non vuole più vedere (chiavi dei titoli)
    fun forecastIgnored(): Set<String> = prefs.getStringSet("forecast_ignored", emptySet()) ?: emptySet()
    fun addForecastIgnored(key: String) {
        if (key.isNotEmpty()) prefs.edit().putStringSet("forecast_ignored", forecastIgnored() + key).apply()
    }
    fun clearForecastIgnored() { prefs.edit().remove("forecast_ignored").apply() }

    // --- Avvisi aggiuntivi (v1.1)
    /** Avviso quando una categoria supera di molto la sua spesa media mensile. */
    fun unusualAlertsEnabled(): Boolean = prefs.getBoolean("unusual_alerts", true)
    fun setUnusualAlertsEnabled(on: Boolean) { prefs.edit().putBoolean("unusual_alerts", on).apply() }

    /** Promemoria serale: parte solo se durante la giornata non è stata inserita nessuna transazione. */
    fun reminderEnabled(): Boolean = prefs.getBoolean("reminder", false)
    fun setReminderEnabled(on: Boolean) { prefs.edit().putBoolean("reminder", on).apply() }
    fun reminderHour(): Int = prefs.getInt("reminder_hour", 21).coerceIn(0, 23)
    fun setReminderHour(h: Int) { prefs.edit().putInt("reminder_hour", h.coerceIn(0, 23)).apply() }

    /** Ultimo giorno in cui l'utente ha inserito una transazione dal modulo, e ultimo giorno del promemoria. */
    fun lastEntryEpochDay(): Long = prefs.getLong("last_entry_day", -1L)
    fun setLastEntryEpochDay(day: Long) { prefs.edit().putLong("last_entry_day", day).apply() }
    fun lastReminderEpochDay(): Long = prefs.getLong("last_reminder_day", -1L)
    fun setLastReminderEpochDay(day: Long) { prefs.edit().putLong("last_reminder_day", day).apply() }

    // --- Fondo di emergenza: mesi di spese da coprire e conti dove si tiene
    fun emergencyMonths(): Int = prefs.getInt("emergency_months", 3)
    fun setEmergencyMonths(m: Int) { prefs.edit().putInt("emergency_months", m).apply() }
    fun emergencyAccountIds(): Set<Long> =
        (prefs.getStringSet("emergency_accounts", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    fun setEmergencyAccountIds(ids: Set<Long>) {
        prefs.edit().putStringSet("emergency_accounts", ids.map { it.toString() }.toSet()).apply()
    }

    // --- Nome dell'utente, per il saluto nella schermata principale
    fun userName(): String = prefs.getString("user_name", "") ?: ""
    fun setUserName(name: String) { prefs.edit().putString("user_name", name.trim()).apply() }

    /** Emette il valore di [read] subito e ogni volta che cambia una delle [keys]. */
    fun <T> changes(vararg keys: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in keys) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun forecastIgnoredFlow(): Flow<Set<String>> = changes("forecast_ignored") { forecastIgnored() }
    fun userNameFlow(): Flow<String> = changes("user_name") { userName() }
    fun emergencyFlow(): Flow<Pair<Int, Set<Long>>> =
        changes("emergency_months", "emergency_accounts") { emergencyMonths() to emergencyAccountIds() }
}
