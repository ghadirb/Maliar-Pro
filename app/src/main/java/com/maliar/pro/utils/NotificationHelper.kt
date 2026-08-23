package com.maliar.pro.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.maliar.pro.R
import com.maliar.pro.ui.profile.SubscriptionActivity

/** All subscription/billing notifications (quota exhausted, expiry reminder, expired)
 *  in one place, so channel setup and the "tap opens SubscriptionActivity" behavior stay
 *  consistent. Uses POST_NOTIFICATIONS, which the app already requests/declares. */
object NotificationHelper {

    private const val CHANNEL_ID = "subscription_channel"
    private const val NOTIFICATION_ID_QUOTA_EXHAUSTED = 5001
    private const val NOTIFICATION_ID_EXPIRY_REMINDER = 5002
    private const val NOTIFICATION_ID_EXPIRED = 5003

    private const val INSIGHT_CHANNEL_ID = "financial_insights_channel"
    private const val NOTIFICATION_ID_FINANCIAL_INSIGHT = 5010

    private fun ensureInsightChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                INSIGHT_CHANNEL_ID,
                "تحلیل و پیشنهاد هوشمند مالی",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "مقایسه هزینه‌ها با ماه گذشته و پیش‌بینی وضعیت مالی پایان ماه"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Opens the app itself (accounting tab) rather than SubscriptionActivity, since this
     *  is an analytical nudge, not a billing prompt. */
    private fun openMainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.maliar.pro.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 1, intent, flags)
    }

    /** One AI/rule-generated insight per day, e.g. "هزینه حمل‌ونقل شما در مرداد نسبت به
     *  تیر ۲۳٪ افزایش داشته است" or a projected month-end surplus/deficit. */
    fun notifyFinancialInsight(context: Context, message: String) {
        ensureInsightChannel(context)
        val notification = NotificationCompat.Builder(context, INSIGHT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💡 تحلیل مالی مالیار")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openMainActivityIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FINANCIAL_INSIGHT, notification)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted: ${e.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "اشتراک و پرداخت",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اطلاع‌رسانی پایان سهمیه رایگان و یادآوری تمدید اشتراک"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun openSubscriptionActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, SubscriptionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openSubscriptionActivityIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+) - nothing else to do; the
            // in-chat upgradeMessage() text still covers this case.
            android.util.Log.w("NotificationHelper", "Notification permission not granted: ${e.message}")
        }
    }

    /** Shown once, the moment the 15 free messages run out. */
    fun notifyQuotaExhausted(context: Context) {
        show(
            context,
            NOTIFICATION_ID_QUOTA_EXHAUSTED,
            "سهمیه رایگان شما تمام شد",
            "برای ادامه استفاده از دستیار هوشمند مالیار پرو، اشتراک ماهانه یا سالانه را فعال کنید."
        )
    }

    /** Shown a few days before the active subscription expires. */
    fun notifyExpiryReminder(context: Context, daysLeft: Int) {
        show(
            context,
            NOTIFICATION_ID_EXPIRY_REMINDER,
            "اشتراک شما رو به پایان است",
            "اشتراک پریمیوم مالیار پرو شما تا $daysLeft روز دیگر منقضی می‌شود. برای جلوگیری از قطع دسترسی، همین حالا تمدید کنید."
        )
    }

    /** Shown once the subscription has actually expired (as opposed to the reminder). */
    fun notifyExpired(context: Context) {
        show(
            context,
            NOTIFICATION_ID_EXPIRED,
            "اشتراک شما منقضی شد",
            "اشتراک پریمیوم مالیار پرو شما به پایان رسیده است. برای ادامه دسترسی کامل، اشتراک را تمدید کنید."
        )
    }
}
