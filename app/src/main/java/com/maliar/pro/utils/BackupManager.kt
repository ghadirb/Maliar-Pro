package com.maliar.pro.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.maliar.pro.database.AppDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backs up / restores the *entire* app database (accounting, reminders, contacts,
 * financial status - everything) by zipping the raw SQLite files directly, rather than
 * hand-serializing every table to JSON. This guarantees a full, exact copy with no risk of
 * missing a field or table, and needs no changes here if entities change later.
 *
 * The destination/source is a plain SAF Uri from ACTION_CREATE_DOCUMENT /
 * ACTION_OPEN_DOCUMENT - deliberately not a hand-rolled Google Drive API integration,
 * since the system document picker already lets the person choose "Google Drive" (or any
 * other provider they have installed, e.g. a USB drive, another cloud app) as the
 * destination on its own, with no OAuth/API-key setup needed on our side at all.
 */
object BackupManager {
    private const val TAG = "BackupManager"
    private const val DB_NAME = "maliar_pro_database"
    private val PART_NAMES = listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm")

    /** Writes a full backup (as a .zip) to [uri]. Returns true on success. */
    fun backupToUri(context: Context, uri: Uri): Boolean {
        return try {
            // Merge the write-ahead-log into the main file first, so the copy is complete
            // even if the database is using WAL journal mode.
            try {
                AppDatabase.getDatabase(context).openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                Log.w(TAG, "wal_checkpoint failed (continuing anyway)", e)
            }

            val dbDir = context.getDatabasePath(DB_NAME).parentFile
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    for (name in PART_NAMES) {
                        val file = File(dbDir, name)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry(name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } ?: return false
            true
        } catch (e: Exception) {
            Log.e(TAG, "backupToUri failed", e)
            false
        }
    }

    /**
     * Extracts a previously-created backup zip from [uri] over the live database files.
     * The app MUST be fully restarted right after this (a live Room connection can't
     * safely be swapped out from under itself) - callers are responsible for prompting a
     * restart immediately after a successful restore.
     */
    fun restoreFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val dbDir = context.getDatabasePath(DB_NAME).parentFile ?: return false
            dbDir.mkdirs()

            // Close the live connection first so no other part of the app is holding the
            // files open while we overwrite them.
            try {
                AppDatabase.getDatabase(context).close()
            } catch (e: Exception) {
                Log.w(TAG, "closing database before restore failed (continuing anyway)", e)
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var restoredAny = false
                    while (entry != null) {
                        // Only accept exactly the file names we ourselves wrote - guards
                        // against a malformed/malicious zip trying to write outside the
                        // database directory.
                        if (entry.name in PART_NAMES) {
                            File(dbDir, entry.name).outputStream().use { out -> zip.copyTo(out) }
                            restoredAny = true
                        }
                        entry = zip.nextEntry
                    }
                    if (!restoredAny) return false
                }
            } ?: return false
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromUri failed", e)
            false
        }
    }
}
