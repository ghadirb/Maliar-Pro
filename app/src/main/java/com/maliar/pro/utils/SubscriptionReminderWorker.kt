package com.maliar.pro.utils

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker

/** Fires once, a few days before the person's premium subscription expires (see
 *  [SubscriptionManager.scheduleExpiryReminder]). Re-checks the *current* premiumUntil
 *  before notifying, since the person may have already renewed by the time this runs -
 *  in that case it silently does nothing instead of showing a stale reminder. */
class SubscriptionReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Pull the latest status from the backend first so a renewal made from another
        // device (or right before this worker ran) is reflected before we decide.
        SubscriptionManager.refreshFromServer(applicationContext)

        val premiumUntil = PreferencesManager(applicationContext).getPremiumUntil()
        val now = System.currentTimeMillis()
        val daysLeft = ((premiumUntil - now) / (24 * 60 * 60 * 1000)).toInt()

        when {
            premiumUntil <= now -> {
                // Already expired by the time this fired - show the "expired" notification
                // instead of a "days left" one.
                NotificationHelper.notifyExpired(applicationContext)
            }
            daysLeft in 0..SubscriptionManager.EXPIRY_REMINDER_DAYS_BEFORE -> {
                NotificationHelper.notifyExpiryReminder(applicationContext, daysLeft.coerceAtLeast(1))
            }
            else -> {
                // Renewed since this was scheduled (premiumUntil moved further out) -
                // nothing to warn about; a fresh reminder will be scheduled for the new
                // expiry date the next time refreshFromServer()/requestPayment() runs.
            }
        }
        return Result.success()
    }
}
