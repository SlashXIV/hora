package com.gabrielifrim.hora.core.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.domain.repository.BackupRepository
import com.gabrielifrim.hora.core.domain.repository.BackupState
import com.gabrielifrim.hora.core.domain.repository.RestoreCheck
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sauvegarde et restauration de la base, seule copie de l'agenda. Le dossier choisi
 * est propre à ce téléphone : il vit dans les préférences, pas dans la base, pour
 * qu'une restauration ne le remplace pas par celui d'un autre appareil.
 */
@Singleton
class DatabaseBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: HoraDatabase,
) : BackupRepository {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    override val state: StateFlow<BackupState> = _state

    private fun readState() = BackupState(
        folder = prefs.getString(KEY_FOLDER, null),
        folderName = prefs.getString(KEY_FOLDER_NAME, null),
        auto = prefs.getBoolean(KEY_AUTO, false),
        last = prefs.getLong(KEY_LAST, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastFailed = prefs.getBoolean(KEY_FAILED, false),
    )

    private fun update(edit: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(edit).apply()
        _state.value = readState()
    }

    /** Garde l'accès au dossier choisi, au-delà du redémarrage. */
    override fun chooseFolder(uri: String) {
        val tree = uri.toUri()
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        _state.value.folder?.takeIf { it != uri }?.toUri()?.let { old -> runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) } }
        context.contentResolver.takePersistableUriPermission(tree, flags)
        val name = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        update { putString(KEY_FOLDER, uri); putString(KEY_FOLDER_NAME, name); putBoolean(KEY_FAILED, false) }
    }

    override fun setAuto(on: Boolean) {
        update { putBoolean(KEY_AUTO, on) }
        scheduleAuto(context, on)
    }

    /**
     * Écrit une copie de la base dans le dossier choisi.
     * @param auto une par jour, les [KEEP_AUTO] dernières gardées ; une sauvegarde à la main n'est jamais effacée.
     * @return le nom du fichier écrit.
     */
    override suspend fun backupNow(auto: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = requireNotNull(_state.value.folder) { "aucun dossier" }.toUri()
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val now = LocalDateTime.now()
            val name = if (auto) "hora-auto-${now.format(DAY)}.db" else "hora-${now.format(MINUTE)}.db"
            children(tree).filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            val target = requireNotNull(DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)) { "création refusée" }
            requireNotNull(resolver.openOutputStream(target, "w")) { "écriture refusée" }.use { snapshot(it) }
            if (auto) {
                children(tree).filter { it.second.startsWith("hora-auto-") }.sortedByDescending { it.second }.drop(KEEP_AUTO)
                    .forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            }
            update { putLong(KEY_LAST, System.currentTimeMillis()); putBoolean(KEY_FAILED, false) }
            name
        }.onFailure { if (auto) update { putBoolean(KEY_FAILED, true) } }
    }

    /** Les fichiers du dossier : (uri, nom). */
    private fun children(tree: Uri): List<Pair<Uri, String>> {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(list, columns, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getString(1)) }
        }.orEmpty()
    }

    /**
     * Copie cohérente de la base. En WAL, des écritures récentes peuvent n'être que
     * dans le journal : on le reverse dans la base, puis on copie le fichier en
     * bloquant les écritures, et seulement si le journal est resté vide entre-temps.
     */
    private suspend fun snapshot(out: OutputStream) {
        val main = context.getDatabasePath(HoraDatabase.NAME)
        val wal = File(main.path + "-wal")
        repeat(10) {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val copied = db.withTransaction {
                if (wal.length() > 0) false else { main.inputStream().use { it.copyTo(out) }; true }
            }
            if (copied) return
            delay(200)
        }
        error("base occupée")
    }

    /** Copie le fichier choisi à part et l'examine, sans toucher à la base. */
    override suspend fun inspect(uri: String): RestoreCheck = withContext(Dispatchers.IO) {
        val copy = File(context.cacheDir, "restauration.db").apply { delete() }
        runCatching {
            requireNotNull(context.contentResolver.openInputStream(uri.toUri())).use { input -> copy.outputStream().use { input.copyTo(it) } }
            val header = copy.inputStream().use { it.readNBytesCompat(16) }
            if (!header.contentEquals(SQLITE_HEADER)) return@withContext RestoreCheck.NotHora
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                if (sqlite.version > HoraDatabase.VERSION) return@withContext RestoreCheck.TooNew
                fun count(table: String) = sqlite.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) }
                runCatching { count("settings") }.getOrNull() ?: return@withContext RestoreCheck.NotHora
                // Une base v1 (J1) n'a ni agendas ni événements : rien à montrer, mais elle se restaure.
                RestoreCheck.Ready(copy.path, runCatching { count("event") }.getOrDefault(0), runCatching { count("agenda") }.getOrDefault(0))
            }
        }.getOrElse { RestoreCheck.NotHora }
    }

    /**
     * Remplace la base par [check] puis relance l'application : Room rouvre la
     * base restaurée et la migre si elle vient d'une version plus ancienne. La base
     * remplacée est d'abord copiée à part (`avant-restauration.db`), au cas où.
     */
    override suspend fun restore(check: RestoreCheck.Ready): Unit = withContext(Dispatchers.IO) {
        val main = context.getDatabasePath(HoraDatabase.NAME)
        File(context.filesDir, "avant-restauration.db").outputStream().use { snapshot(it) }
        db.close()
        listOf("-wal", "-shm", "-journal").forEach { File(main.path + it).delete() }
        File(check.path).copyTo(main, overwrite = true)
        File(check.path).delete()
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        // Le processus doit mourir : la base fermée ne se rouvre pas dans ce processus.
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val KEY_FOLDER = "folder"
        private const val KEY_FOLDER_NAME = "folder_name"
        private const val KEY_AUTO = "auto"
        private const val KEY_LAST = "last"
        private const val KEY_FAILED = "last_failed"
        private const val KEEP_AUTO = 7

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

        /**
         * Une sauvegarde par jour, vers 3 h, sans réveil exact : l'heure précise ne
         * compte pas. Réarmée au lancement et au démarrage du téléphone.
         */
        fun scheduleAuto(context: Context, on: Boolean) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(context, BackupReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.cancel(intent)
            if (!on) return
            val next = LocalDateTime.now().toLocalDate().plusDays(1).atTime(3, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, intent)
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val buffer = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(buffer, read, n - read)
        if (r < 0) break
        read += r
    }
    return buffer.copyOf(read)
}

/** Réveil quotidien (et démarrage du téléphone) : sauvegarde automatique si elle est activée. */
@AndroidEntryPoint
class BackupReceiver : BroadcastReceiver() {
    @Inject lateinit var backup: DatabaseBackup

    override fun onReceive(context: Context, intent: Intent) {
        val auto = backup.state.value.auto
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DatabaseBackup.scheduleAuto(context, auto)
            return
        }
        if (!auto) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                backup.backupNow(auto = true)
            } finally {
                pending.finish()
            }
        }
    }
}
