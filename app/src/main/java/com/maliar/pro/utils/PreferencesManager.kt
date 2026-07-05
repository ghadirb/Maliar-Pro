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
}
