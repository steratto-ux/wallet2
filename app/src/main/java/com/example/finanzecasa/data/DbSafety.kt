package com.example.finanzecasa.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Rete di sicurezza per gli aggiornamenti: prima che Room migri il database a una versione più recente,
 * ne salva una copia accanto all'originale. Se una migrazione andasse storta i dati non sono persi.
 */
object DbSafety {
    fun copyBeforeMigration(context: Context, dbName: String, targetVersion: Int) {
        val file = context.getDatabasePath(dbName)
        if (!file.exists()) return
        try {
            var currentVersion = 0
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                currentVersion = db.version
                if (currentVersion in 1 until targetVersion) {
                    // Porta nel file principale le scritture ancora nel registro (WAL), poi copia.
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            } finally {
                db.close()
            }
            if (currentVersion in 1 until targetVersion) {
                val copy = File(file.parentFile, "$dbName.prima-della-v$targetVersion.bak")
                if (!copy.exists()) file.copyTo(copy, overwrite = false)
            }
        } catch (_: Exception) {
            // La copia è solo un extra: se non riesce, la migrazione procede comunque.
        }
    }
}
