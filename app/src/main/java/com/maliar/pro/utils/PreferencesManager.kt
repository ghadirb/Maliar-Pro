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
        private const val KEY_WORKING_MODE = "working_mode"
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

    enum class WorkingMode {
        ONLINE,
        OFFLINE,
        HYBRID
    }

    fun setWorkingMode(mode: WorkingMode) {
        prefs.edit().putString(KEY_WORKING_MODE, mode.name).apply()
    }

    fun getWorkingMode(): WorkingMode {
        val name = prefs.getString(KEY_WORKING_MODE, WorkingMode.ONLINE.name)
        return try {
            WorkingMode.valueOf(name ?: WorkingMode.ONLINE.name)
        } catch (_: Exception) {
            WorkingMode.ONLINE
        }
    }
}
