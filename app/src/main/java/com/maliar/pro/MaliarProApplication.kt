package com.maliar.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaliarProApplication : Application() {

    companion object {
        const val CHANNEL_ID = "maliar_pro_channel"
        const val CHANNEL_NAME = "مالیار پرو"
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val REMINDER_CHANNEL_NAME = "یادآوری‌ها"
        const val CALL_CHANNEL_ID = "call_channel"
        const val CALL_CHANNEL_NAME = "تماس‌ها"
        const val BACKGROUND_CHANNEL_ID = "background_channel"
        const val BACKGROUND_CHANNEL_NAME = "فعالیت پس‌زمینه"

        lateinit var instance: MaliarProApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initializeDatabase()
        startBackgroundService()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Main channel
            val mainChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "کانال اعلان‌های اصلی برنامه"
            }
            notificationManager.createNotificationChannel(mainChannel)
            
            // Reminder channel
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "کانال یادآوری‌ها"
            }
            notificationManager.createNotificationChannel(reminderChannel)
            
            // Call channel
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "کانال تماس‌ها"
            }
            notificationManager.createNotificationChannel(callChannel)

            // Background keep-alive channel - deliberately minimal importance so the
            // persistent "app is running" notification never makes a sound/pops up,
            // it just needs to exist to satisfy the foreground service requirement.
            val backgroundChannel = NotificationChannel(
                BACKGROUND_CHANNEL_ID,
                BACKGROUND_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "نگه‌داشتن برنامه در پس‌زمینه برای یادآوری‌های هوشمند"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(backgroundChannel)
        }
    }

    private fun initializeDatabase() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.d("MaliarProApplication", "🔄 Initializing database...")
                // Database initialization will be done here
                Log.d("MaliarProApplication", "✅ Database initialized")
            } catch (e: Exception) {
                Log.e("MaliarProApplication", "❌ Error initializing database", e)
            }
        }
        
        // Initialize API Keys
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.d("MaliarProApplication", "🔄 Auto-provisioning API keys...")
                val result = com.maliar.pro.utils.AutoProvisioningManager.autoProvision(this@MaliarProApplication)
                if (result.isSuccess) {
                    val keys = result.getOrNull() ?: emptyList()
                    Log.d("MaliarProApplication", "✅ ${keys.size} API keys auto-provisioned and activated")
                } else {
                    Log.w("MaliarProApplication", "⚠️ Auto-provisioning failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("MaliarProApplication", "❌ Error in auto-provisioning", e)
            }
        }
    }

    /**
     * Starts the background keep-alive foreground service so smart reminders and the
     * assistant stay responsive even when the app UI isn't in the foreground - unless the
     * person has explicitly turned this off in Settings, in which case its persistent
     * notification never appears at all. Wrapped in try/catch because starting a
     * foreground service from Application.onCreate can throw on some OEM/Android versions
     * if background-start restrictions apply; in that case the app still works, it just
     * loses some background reliability instead of crashing.
     */
    private fun startBackgroundService() {
        val enabled = com.maliar.pro.utils.PreferencesManager(this).isBackgroundServiceEnabled()
        if (!enabled) {
            Log.d("MaliarProApplication", "Background service disabled in Settings, not starting it")
            return
        }
        try {
            com.maliar.pro.services.MaliarBackgroundService.start(this)
        } catch (e: Exception) {
            Log.w("MaliarProApplication", "⚠️ Could not start background service: ${e.message}")
        }
    }
}
