package com.example.finanzecasa.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.finanzecasa.data.AppDatabase
import com.example.finanzecasa.ui.AppSettings
import java.io.File
import java.time.LocalDate

/**
 * Backup automatico: ogni [INTERVAL_DAYS] giorni salva un file .zip nella memoria dell'app (ultimi [KEEP_INTERNAL])
 * e, se l'utente ha scelto una cartella, anche lì (ultimi [KEEP_IN_FOLDER]). La copia nella cartella scelta è
 * quella che sopravvive anche alla disinstallazione dell'app.
 */
class AutoBackup(private val context: Context, private val db: AppDatabase) {
    private val settings = AppSettings(context)

    /** Esegue il backup se è abilitato e l'ultimo è più vecchio dell'intervallo. Restituisce true se ne ha fatto uno. */
    suspend fun runIfDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!settings.autoBackupEnabled()) return false
        val last = settings.lastAutoBackupMillis()
        if (last != 0L && nowMillis - last < INTERVAL_DAYS * DAY_MILLIS) return false
        return runNow(nowMillis)
    }

    suspend fun runNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val name = "il-mio-wallet-auto-${LocalDate.now()}.zip"
        val repo = BackupRepository(context, db)

        // 1) Copia interna
        val dir = File(context.filesDir, "backups").also { it.mkdirs() }
        val target = File(dir, name)
        target.outputStream().use { repo.writeBackup(it) }
        dir.listFiles { f -> f.name.startsWith("il-mio-wallet-auto-") }
            ?.sortedByDescending { it.name }?.drop(KEEP_INTERNAL)?.forEach { it.delete() }

        // 2) Copia nella cartella scelta (se c'è e se il permesso è ancora valido)
        val folder = settings.backupFolder()
        if (folder != null) {
            runCatching { copyToFolder(Uri.parse(folder), target, name) }
        }
        settings.setLastAutoBackupMillis(nowMillis)
        return true
    }

    private fun copyToFolder(tree: Uri, source: File, name: String) {
        val resolver = context.contentResolver
        val treeDoc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        // Se esiste già un file con questo nome (backup dello stesso giorno) lo sostituisco.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val existing = mutableListOf<Pair<String, Uri>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val display = c.getString(1) ?: continue
                if (display.startsWith("il-mio-wallet-auto-")) {
                    existing += display to DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
        existing.filter { it.first == name }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }

        val created = DocumentsContract.createDocument(resolver, treeDoc, "application/zip", name) ?: return
        resolver.openOutputStream(created)?.use { out -> source.inputStream().use { it.copyTo(out) } }

        // Tengo solo gli ultimi backup automatici nella cartella
        existing.filter { it.first != name }.sortedByDescending { it.first }.drop(KEEP_IN_FOLDER - 1)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }
    }

    companion object {
        const val INTERVAL_DAYS = 7
        const val KEEP_INTERNAL = 3
        const val KEEP_IN_FOLDER = 8
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
