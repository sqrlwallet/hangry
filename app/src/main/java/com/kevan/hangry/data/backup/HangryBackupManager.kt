package com.kevan.hangry.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.kevan.hangry.data.local.HangryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/** The manifest at the root of every backup file. */
@Serializable
internal data class BackupManifest(
    val app: String,
    val format: Int,
    val dbVersion: Int,
    val appVersion: String = "",
    val createdAtEpochMs: Long = 0,
    /** Where photos lived on the phone that made the backup - their paths are stored absolute in the database. */
    val filesDir: String = ""
)

/** [needsRestart]: the swap had already begun, so the app must restart whatever happens next. */
class BackupException(message: String, val needsRestart: Boolean = false) : Exception(message)

/**
 * Hangry's whole local state as one .zip the user keeps wherever they like: the database, every
 * photo and the app's own settings. It never leaves the phone unless they move it themselves -
 * this replaces Android's cloud Auto Backup, which is switched off for Hangry's data.
 *
 * The OpenRouter key is deliberately left out: it's encrypted with a Keystore key that can't
 * leave this phone, so it has to be entered again after a restore.
 */
class HangryBackupManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Writes a complete backup to [out]. Returns the number of files saved besides the database. */
    suspend fun writeBackup(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val database = HangryDatabase.getDatabase(context)
        // Fold the write-ahead log into the main file so copying that one file is a full snapshot.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(DB_NAME)
        val manifest = BackupManifest(
            app = APP_ID,
            format = FORMAT,
            dbVersion = database.openHelper.readableDatabase.version,
            appVersion = appVersion(),
            createdAtEpochMs = System.currentTimeMillis(),
            filesDir = context.filesDir.absolutePath
        )
        var fileCount = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()

            zip.addFile(dbFile, "$DB_PREFIX$DB_NAME")

            for (dirName in FILE_DIRS) {
                val dir = File(context.filesDir, dirName)
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.addFile(file, FILES_PREFIX + file.relativeTo(context.filesDir).invariantSeparatorsPath)
                    fileCount++
                }
            }
            prefsFiles().forEach { file ->
                zip.addFile(file, PREFS_PREFIX + file.name)
                fileCount++
            }
        }
        fileCount
    }

    /**
     * Replaces everything in Hangry with the backup in [input]. Checks the whole file first and
     * changes nothing if it isn't a usable Hangry backup. On success the app must restart
     * straight away ([restartApp]) - the old database and settings are still held in memory.
     */
    suspend fun restoreBackup(input: InputStream) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_staging").apply { deleteRecursively(); mkdirs() }
        try {
            unzipInto(input, staging)
            val manifest = readManifest(staging)
            val stagedDb = File(staging, "$DB_PREFIX$DB_NAME")
            checkDatabase(stagedDb, manifest)
            if (manifest.filesDir.isNotBlank() && manifest.filesDir != context.filesDir.absolutePath) {
                remapPhotoPaths(stagedDb, manifest.filesDir, context.filesDir.absolutePath)
            }

            // Everything checks out - swap it in.
            swapIn(staging, stagedDb)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun swapIn(staging: File, stagedDb: File) {
        try {
            HangryDatabase.getDatabase(context).close()
            val dbFile = context.getDatabasePath(DB_NAME)
            listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal")).forEach { it.delete() }
            stagedDb.copyTo(dbFile, overwrite = true)

            for (dirName in FILE_DIRS) {
                File(context.filesDir, dirName).deleteRecursively()
                val restored = File(staging, FILES_PREFIX + dirName)
                if (restored.exists()) restored.copyRecursively(File(context.filesDir, dirName), overwrite = true)
            }
            prefsFiles().forEach { it.delete() }
            File(staging, PREFS_PREFIX).listFiles()?.forEach { file ->
                file.copyTo(File(prefsDir(), file.name), overwrite = true)
            }
        } catch (e: Exception) {
            throw BackupException("Restore stopped partway (${e.message ?: "storage error"}). Hangry will restart - try again with more free space.", needsRestart = true)
        }
    }

    /** Relaunches Hangry in a fresh process so nothing from before the restore lingers in memory. */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }

    private fun unzipInto(input: InputStream, staging: File) {
        var sawAnything = false
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                if (entry.isDirectory || !isAllowedEntry(name)) return@forEach
                val target = File(staging, name)
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) return@forEach
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
                sawAnything = true
            }
        }
        if (!sawAnything) throw BackupException("That file isn't a Hangry backup.")
    }

    private fun readManifest(staging: File): BackupManifest {
        val file = File(staging, MANIFEST)
        if (!file.exists()) throw BackupException("That file isn't a Hangry backup.")
        val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), file.readText()) }
            .getOrElse { throw BackupException("This backup file is damaged.") }
        checkManifest(manifest, currentDbVersion())
        return manifest
    }

    private fun checkDatabase(file: File, manifest: BackupManifest) {
        if (!file.exists()) throw BackupException("This backup has no data in it.")
        val ok = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
                integrity && db.version == manifest.dbVersion
            }
        }.getOrDefault(false)
        if (!ok) throw BackupException("This backup file is damaged.")
    }

    /** Photo paths are stored absolute, so point them at this phone's storage. */
    private fun remapPhotoPaths(file: File, from: String, to: String) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            PHOTO_COLUMNS.forEach { (table, column) ->
                runCatching { db.execSQL("UPDATE $table SET $column = replace($column, ?, ?) WHERE $column IS NOT NULL", arrayOf(from, to)) }
            }
        }
    }

    private fun currentDbVersion(): Int = HangryDatabase.getDatabase(context).openHelper.readableDatabase.version

    private fun prefsDir() = File(context.applicationInfo.dataDir, "shared_prefs")

    private fun prefsFiles(): List<File> = prefsDir().listFiles()?.filter { isOwnPrefsFile(it.name) }.orEmpty()


    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private fun ZipOutputStream.addFile(file: File, name: String) {
        if (!file.exists()) return
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    companion object {
        private const val APP_ID = "com.kevan.hangry"
        private const val FORMAT = 1
        private const val MIN_DB_VERSION = 2
        private const val MANIFEST = "manifest.json"
        private const val DB_NAME = "hangry.db"
        private const val DB_PREFIX = "database/"
        private const val FILES_PREFIX = "files/"
        private const val PREFS_PREFIX = "shared_prefs/"
        private const val SECURE_PREFS = "hangry_secure_prefs"

        /** App-private folders holding the user's photos and Jetpack DataStore settings. */
        internal val FILE_DIRS = listOf("food_photos", "posture_photos", "supplement_photos", "coach_images", "datastore")
        private val EXTRA_PREFS = setOf("supplement_reminders.xml")

        /** Only what Hangry itself writes, and nothing that could escape the staging folder. */
        internal fun isAllowedEntry(name: String): Boolean {
            if (name.contains("..") || name.startsWith("/") || name.contains('\\')) return false
            return name == MANIFEST || name == "$DB_PREFIX$DB_NAME" ||
                FILE_DIRS.any { name.startsWith("$FILES_PREFIX$it/") && name.length > "$FILES_PREFIX$it/".length } ||
                (name.startsWith(PREFS_PREFIX) && isOwnPrefsFile(name.removePrefix(PREFS_PREFIX)))
        }

        /** The app's own settings files - never the encrypted API key, never a library's prefs. */
        internal fun isOwnPrefsFile(name: String) =
            name.endsWith(".xml") && name != "$SECURE_PREFS.xml" && !name.contains('/') &&
                (name.startsWith("hangry_") || name in EXTRA_PREFS)

        /** Throws unless this phone's Hangry can open a backup described by [manifest]. */
        internal fun checkManifest(manifest: BackupManifest, currentDbVersion: Int) {
            if (manifest.app != APP_ID) throw BackupException("That file isn't a Hangry backup.")
            if (manifest.format > FORMAT || manifest.dbVersion > currentDbVersion) {
                throw BackupException("This backup was made by a newer version of Hangry. Update the app, then restore it.")
            }
            if (manifest.dbVersion < MIN_DB_VERSION) throw BackupException("This backup is from a version of Hangry that's too old to restore.")
        }

        private val PHOTO_COLUMNS = listOf(
            "food_log" to "photoPath",
            "supplements" to "photoPath",
            "posture_scans" to "photoPathsJson",
            "coach_messages" to "imagePaths"
        )
    }
}
