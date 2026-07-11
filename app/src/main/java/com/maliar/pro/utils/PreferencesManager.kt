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
        private const val KEY_LAST_SEEN_ANNOUNCEMENT_ID = "last_seen_announcement_id"
        private const val KEY_SMS_READING_ENABLED = "sms_reading_enabled"

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
     * notification) is allowed to run. Defaults to true because it measurably helps smart
     * reminders fire reliably on aggressive OEMs - but AlarmManager-scheduled alarms still
     * work without it (just somewhat less reliably on some devices), so it's safe for the
     * person to turn off if they'd rather not see that notification at all.
     */
    fun isBackgroundServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, true)
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

    // --- Startup announcement ---

    fun getLastSeenAnnouncementId(): String? = prefs.getString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, null)

    fun setLastSeenAnnouncementId(id: String) {
        prefs.edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, id).apply()
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

    // --- Bank SMS reading (opt-in) ---

    fun isSmsReadingEnabled(): Boolean = prefs.getBoolean(KEY_SMS_READING_ENABLED, false)

    fun setSmsReadingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_READING_ENABLED, enabled).apply()
    }
}
