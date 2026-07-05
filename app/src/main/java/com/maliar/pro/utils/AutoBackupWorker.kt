package com.maliar.pro.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (while "پشتیبان‌گیری خودکار" is on) and re-writes the backup zip to the
 * exact same SAF location the person picked the first time they backed up manually - a
 * document tree/file Uri from the system picker keeps working across app runs as long as
 * we've taken a persistable permission on it, so no repeated picker prompt is needed for
 * the daily run itself, whether that location is on-device or a provider like Google Drive.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isAutoBackupEnabled()) return Result.success()

        val uriString = prefs.getLastBackupUri() ?: return Result.success()
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "Saved backup uri is invalid, disabling auto-backup", e)
            prefs.setAutoBackupEnabled(false)
            return Result.failure()
        }

        val ok = BackupManager.backupToUri(applicationContext, uri)
        if (!ok) {
            Log.e(TAG, "Scheduled auto-backup failed for uri=$uri")
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val UNIQUE_WORK_NAME = "maliar_pro_auto_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
