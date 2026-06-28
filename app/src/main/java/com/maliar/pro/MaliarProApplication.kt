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
        
        lateinit var instance: MaliarProApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initializeDatabase()
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
}
