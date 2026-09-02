package com.maliar.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.maliar.pro.models.APIKey

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "maliar_pro_prefs"
        private const val KEY_API_KEYS = "api_keys"
        private const val KEY_AUTO_PROVISIONING = "auto_provisioning"
        private const val KEY_NOTIFICATION_MODE = "notification_mode"
        private const val KEY_BACKGROUND_SERVICE_ENABLED = "background_service_enabled"
        private const val KEY_LAST_BACKUP_URI = "last_backup_uri"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_FINANCIAL_INSIGHTS_ENABLED = "financial_insights_enabled"
        private const val KEY_AUTO_DUE_REMINDERS_ENABLED = "auto_due_reminders_enabled"
        private const val KEY_AUTO_DUE_REMINDER_DAYS_BEFORE = "auto_due_reminder_days_before"
        private const val KEY_FINANCIAL_PERIOD_START_DAY = "financial_period_start_day"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START_MINUTES = "quiet_hours_start_minutes"
        private const val KEY_QUIET_HOURS_END_MINUTES = "quiet_hours_end_minutes"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LAST_SEEN_ANNOUNCEMENT_ID = "last_seen_announcement_id"
        private const val KEY_BATTERY_OPT_PROMPT_DISMISSED = "battery_optimization_prompt_dismissed"

        // --- Subscription / entitlement ---
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PREMIUM_UNTIL = "premium_until"
        private const val KEY_LAST_SUBSCRIPTION_CHECK = "last_subscription_check"
        private const val KEY_DAILY_AI_COUNT = "daily_ai_count"
        private const val KEY_DAILY_AI_COUNT_DATE = "daily_ai_count_date"
        private const val KEY_QUOTA_NOTIFIED = "quota_exhausted_notified"
        private const val KEY_EXPIRY_REMINDER_SCHEDULED_FOR = "expiry_reminder_scheduled_for"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_LAST_STORE_CHANNEL = "last_store_channel"
    }

    fun saveAPIKeys(keys: List<APIKey>) {
        val json = gson.toJson(keys)
        prefs.edit().putString(KEY_API_KEYS, json).apply()
    }

    fun getAPIKeys(): List<APIKey> {
        val json = prefs.getString(KEY_API_KEYS, null) ?: return emptyList()
        val type = object : TypeToken<List<APIKey>>() {}.type
        return gson.fromJson(json, type)
    }

    fun hasAPIKeys(): Boolean {
        return prefs.contains(KEY_API_KEYS)
    }

    fun clearAPIKeys() {
        prefs.edit().remove(KEY_API_KEYS).apply()
    }

    fun setAutoProvisioning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PROVISIONING, enabled).apply()
    }

    fun isAutoProvisioningEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_PROVISIONING, true)
    }

    fun setNotificationMode(mode: String) {
        prefs.edit().putString(KEY_NOTIFICATION_MODE, mode).apply()
    }

    fun getNotificationMode(): String {
        // Only "none" and "action" exist now (the old middle "simple" option was removed
        // from Settings) - default to notifications on for anyone without a saved value yet,
        // and anyone with "simple" saved from before also lands on the real notification.
        val stored = prefs.getString(KEY_NOTIFICATION_MODE, "action") ?: "action"
        return if (stored == "none") "none" else "action"
    }

    /**
     * Whether MaliarBackgroundService (the persistent, silent "app is running"
     * notification) is allowed to run. It defaults to false and is only enabled after an
     * explicit choice in Settings; alarms still work without it.
     */
    fun isBackgroundServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, false)
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE_ENABLED, enabled).apply()
    }

    // --- Backup & restore ---

    /** The last SAF location (device storage OR a provider like Google Drive) the person
     *  backed up to - kept so automatic daily backups can reuse it without asking again. */
    fun setLastBackupUri(uri: String?) {
        prefs.edit().putString(KEY_LAST_BACKUP_URI, uri).apply()
    }

    fun getLastBackupUri(): String? = prefs.getString(KEY_LAST_BACKUP_URI, null)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)

    // --- AI financial insights ---

    /** Whether the daily "پیشنهادهای هوشمند مالی" notification worker is allowed to run -
     *  on by default since the feature is purely local-analysis-first and only degrades
     *  gracefully (no AI key just means a slightly less polished sentence, not a failure). */
    fun setFinancialInsightsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINANCIAL_INSIGHTS_ENABLED, enabled).apply()
    }

    fun isFinancialInsightsEnabled(): Boolean = prefs.getBoolean(KEY_FINANCIAL_INSIGHTS_ENABLED, true)

    // --- Automatic due-date reminders (checks/installments/debts/debtors) ---

    fun setAutoDueRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DUE_REMINDERS_ENABLED, enabled).apply()
    }

    fun isAutoDueRemindersEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_DUE_REMINDERS_ENABLED, true)

    /** How many days before a check/installment/debt/debtor due date to auto-create a
     *  reminder, if the person hasn't already made one for it themselves. */
    fun setAutoDueReminderDaysBefore(days: Int) {
        prefs.edit().putInt(KEY_AUTO_DUE_REMINDER_DAYS_BEFORE, days.coerceIn(0, 30)).apply()
    }

    fun getAutoDueReminderDaysBefore(): Int = prefs.getInt(KEY_AUTO_DUE_REMINDER_DAYS_BEFORE, 1)

    // --- Custom financial period (for monthly balance/report calculations) ---

    /** Which Jalali day-of-month a "month" starts on for balance/report purposes - e.g. 15
     *  means the period runs from the 15th of one month to the 14th of the next, for
     *  people whose income/expense cycle doesn't follow the calendar month (paid on the
     *  15th, rent due on the 15th, etc). 1 (the actual 1st) is the default/no-op value. */
    fun setFinancialPeriodStartDay(day: Int) {
        prefs.edit().putInt(KEY_FINANCIAL_PERIOD_START_DAY, day.coerceIn(1, 31)).apply()
    }

    fun getFinancialPeriodStartDay(): Int = prefs.getInt(KEY_FINANCIAL_PERIOD_START_DAY, 1)

    // --- Quiet hours (سکوت شبانه) ---
    // Deliberately NOT implemented via Android's Do Not Disturb / notification-listener
    // APIs - reading DND state or other apps' notifications requires the
    // BIND_NOTIFICATION_LISTENER_SERVICE permission, which is exactly the kind of
    // heavily-scrutinized, spyware-adjacent permission that risks a repeat of the Play
    // Protect "harmful" flag this project has already been through (it grants a special
    // service the ability to read every notification on the device, from every app).
    // Instead, this is a simple local time-window check consulted only when *this app's
    // own* already-scheduled reminder alarm fires - no new permission, no listener
    // service, nothing running that isn't already part of the existing reminder flow.

    fun setQuietHoursEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, enabled).apply()
    }

    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)

    /** Both as minutes-since-midnight (0-1439), local device time. */
    fun setQuietHoursRange(startMinutes: Int, endMinutes: Int) {
        prefs.edit()
            .putInt(KEY_QUIET_HOURS_START_MINUTES, startMinutes.coerceIn(0, 1439))
            .putInt(KEY_QUIET_HOURS_END_MINUTES, endMinutes.coerceIn(0, 1439))
            .apply()
    }

    fun getQuietHoursStartMinutes(): Int = prefs.getInt(KEY_QUIET_HOURS_START_MINUTES, 23 * 60) // 23:00
    fun getQuietHoursEndMinutes(): Int = prefs.getInt(KEY_QUIET_HOURS_END_MINUTES, 7 * 60) // 07:00

    /** True right now if quiet hours are on and the current time falls in the configured
     *  window - correctly handles a window that crosses midnight (e.g. 23:00 to 07:00). */
    fun isWithinQuietHoursNow(): Boolean {
        if (!isQuietHoursEnabled()) return false
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = getQuietHoursStartMinutes()
        val end = getQuietHoursEndMinutes()
        return if (start <= end) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    // --- Startup announcement ---

    fun getLastSeenAnnouncementId(): String? = prefs.getString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, null)

    fun setLastSeenAnnouncementId(id: String) {
        prefs.edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, id).apply()
    }

    // --- Battery optimization prompt (widget/reminder reliability) ---

    /** True once the person has explicitly dismissed the "ignore battery optimization"
     *  prompt with "بعداً" (later), so we don't nag them again every app open. If they
     *  actually grant the exemption, [android.os.PowerManager.isIgnoringBatteryOptimizations]
     *  itself becomes the source of truth and this flag stops mattering. */
    fun hasBatteryOptimizationPromptBeenDismissed(): Boolean =
        prefs.getBoolean(KEY_BATTERY_OPT_PROMPT_DISMISSED, false)

    fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPT_DISMISSED, dismissed).apply()
    }

    // --- Subscription / entitlement ---

    /** A stable random ID for this install, generated once and reused - sent to the
     *  backend so it can tell this device apart from others without needing a full
     *  login/account system. */
    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    /** Epoch millis when the premium subscription expires; 0 means never subscribed. */
    fun getPremiumUntil(): Long = prefs.getLong(KEY_PREMIUM_UNTIL, 0L)

    fun setPremiumUntil(epochMillis: Long) {
        prefs.edit().putLong(KEY_PREMIUM_UNTIL, epochMillis).apply()
    }

    fun getLastSubscriptionCheck(): Long = prefs.getLong(KEY_LAST_SUBSCRIPTION_CHECK, 0L)

    fun setLastSubscriptionCheck(epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SUBSCRIPTION_CHECK, epochMillis).apply()
    }

    /** Today's free-tier AI usage count and the date it belongs to (yyyy-MM-dd), so
     *  SubscriptionManager can reset the counter the moment the date rolls over without
     *  needing a background job. */
    fun getDailyAiCount(): Int = prefs.getInt(KEY_DAILY_AI_COUNT, 0)

    fun getDailyAiCountDate(): String? = prefs.getString(KEY_DAILY_AI_COUNT_DATE, null)

    fun setDailyAiCount(count: Int, date: String) {
        prefs.edit()
            .putInt(KEY_DAILY_AI_COUNT, count)
            .putString(KEY_DAILY_AI_COUNT_DATE, date)
            .apply()
    }

    // --- Payment/notification bookkeeping ---

    /** So the "free quota exhausted" notification is shown once, not on every failed
     *  canUseAi() check. Reset back to false whenever premium is (re)activated. */
    fun hasNotifiedQuotaExhausted(): Boolean = prefs.getBoolean(KEY_QUOTA_NOTIFIED, false)

    fun setNotifiedQuotaExhausted(notified: Boolean) {
        prefs.edit().putBoolean(KEY_QUOTA_NOTIFIED, notified).apply()
    }

    /** The premiumUntil value (epoch millis) that the currently-scheduled expiry-reminder
     *  WorkManager job was scheduled for. Used to avoid re-scheduling the same reminder
     *  over and over, and to know when to reschedule (e.g. after a renewal). */
    fun getExpiryReminderScheduledFor(): Long = prefs.getLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, 0L)

    fun setExpiryReminderScheduledFor(premiumUntil: Long) {
        prefs.edit().putLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, premiumUntil).apply()
    }

    /** Optional phone number, only used so the payment backend can text/identify a
     *  receipt or recover a purchase for support - never required to use the app. */
    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    fun setPhoneNumber(phone: String?) {
        prefs.edit().putString(KEY_PHONE_NUMBER, phone).apply()
    }

    /** Which store the last successful/attempted purchase went through - "bazaar",
     *  "myket" or "direct". Purely informational (e.g. for support/debugging). */
    fun getLastStoreChannel(): String? = prefs.getString(KEY_LAST_STORE_CHANNEL, null)

    fun setLastStoreChannel(channel: String) {
        prefs.edit().putString(KEY_LAST_STORE_CHANNEL, channel).apply()
    }

}
