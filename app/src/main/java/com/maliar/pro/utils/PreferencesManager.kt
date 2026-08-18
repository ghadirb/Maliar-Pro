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

    fun hasAPIKeys(): Boolean = prefs.contains(KEY_API_KEYS)

    fun clearAPIKeys() {
        prefs.edit().remove(KEY_API_KEYS).apply()
    }

    fun setAutoProvisioning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PROVISIONING, enabled).apply()
    }

    fun isAutoProvisioningEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_PROVISIONING, true)

    fun setNotificationMode(mode: String) {
        prefs.edit().putString(KEY_NOTIFICATION_MODE, mode).apply()
    }

    fun getNotificationMode(): String {
        val stored = prefs.getString(KEY_NOTIFICATION_MODE, "action") ?: "action"
        return if (stored == "none") "none" else "action"
    }

    fun isBackgroundServiceEnabled(): Boolean =
        prefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, true)

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE_ENABLED, enabled).apply()
    }

    fun setLastBackupUri(uri: String?) {
        prefs.edit().putString(KEY_LAST_BACKUP_URI, uri).apply()
    }

    fun getLastBackupUri(): String? = prefs.getString(KEY_LAST_BACKUP_URI, null)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)

    fun getLastSeenAnnouncementId(): String? = prefs.getString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, null)

    fun setLastSeenAnnouncementId(id: String) {
        prefs.edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT_ID, id).apply()
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    fun getPremiumUntil(): Long = prefs.getLong(KEY_PREMIUM_UNTIL, 0L)

    fun setPremiumUntil(epochMillis: Long) {
        prefs.edit().putLong(KEY_PREMIUM_UNTIL, epochMillis).apply()
    }

    fun getLastSubscriptionCheck(): Long = prefs.getLong(KEY_LAST_SUBSCRIPTION_CHECK, 0L)

    fun setLastSubscriptionCheck(epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SUBSCRIPTION_CHECK, epochMillis).apply()
    }

    fun getDailyAiCount(): Int = prefs.getInt(KEY_DAILY_AI_COUNT, 0)

    fun getDailyAiCountDate(): String? = prefs.getString(KEY_DAILY_AI_COUNT_DATE, null)

    fun setDailyAiCount(count: Int, date: String) {
        prefs.edit()
            .putInt(KEY_DAILY_AI_COUNT, count)
            .putString(KEY_DAILY_AI_COUNT_DATE, date)
            .apply()
    }

    fun hasNotifiedQuotaExhausted(): Boolean = prefs.getBoolean(KEY_QUOTA_NOTIFIED, false)

    fun setNotifiedQuotaExhausted(notified: Boolean) {
        prefs.edit().putBoolean(KEY_QUOTA_NOTIFIED, notified).apply()
    }

    fun getExpiryReminderScheduledFor(): Long =
        prefs.getLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, 0L)

    fun setExpiryReminderScheduledFor(premiumUntil: Long) {
        prefs.edit().putLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, premiumUntil).apply()
    }

    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    fun setPhoneNumber(phone: String?) {
        prefs.edit().putString(KEY_PHONE_NUMBER, phone).apply()
    }

    fun getLastStoreChannel(): String? = prefs.getString(KEY_LAST_STORE_CHANNEL, null)

    fun setLastStoreChannel(channel: String) {
        prefs.edit().putString(KEY_LAST_STORE_CHANNEL, channel).apply()
    }
}
