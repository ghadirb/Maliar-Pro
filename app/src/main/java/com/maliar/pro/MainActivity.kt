package com.maliar.pro

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.maliar.pro.databinding.ActivityMainBinding
import com.maliar.pro.utils.AnnouncementManager
import com.maliar.pro.utils.KeyManager
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // The user denied it; notifications simply won't show. We don't force it.
            }
            checkExactAlarmPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load encrypted keys at startup
        lifecycleScope.launch {
            try {
                KeyManager.loadKeys(this@MainActivity)
            } catch (e: Exception) {
                // Handle key loading error
            }
        }

        setupNavigation()
        ensureNotificationPermissions()
    }

    /**
     * On Android 13+ (API 33+) notifications require a runtime permission grant.
     * No SMS, phone-call, or contacts permission is requested here. The app's
     * background reminders continue through the exact-alarm and battery settings below.
     */
    private fun ensureNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkExactAlarmPermission()
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("مجوز یادآوری دقیق")
                    .setMessage("برای اینکه یادآوری‌ها و اعلان‌ها سر وقت به‌شما نمایش داده شوند، باید مجوز «زنگ هشدار و یادآوری» را برای مالیار پرو فعال کنید.")
                    .setPositiveButton("رفتن به تنظیمات") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Device doesn't support the direct intent; ignore.
                        }
                    }
                    .setNegativeButton("بعداً", null)
                    .show()
                return
            }
        }
        checkBatteryOptimization()
    }

    /**
     * Many OEMs aggressively kill background apps and silently prevent scheduled alarms
     * and notifications from firing. Keep this reminder reliability setting independent
     * from SMS/phone/contact functionality.
     */
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("بهینه‌سازی باتری")
                .setMessage("برخی گوشی‌ها برنامه‌های در پس‌زمینه را می‌بندند و مانع نمایش یادآوری‌ها می‌شوند. برای جلوگیری از این مشکل، مالیار پرو را از بهینه‌سازی باتری مستثنی کنید.")
                .setPositiveButton("رفتن به تنظیمات") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e2: Exception) {
                            // Ignore if neither is available on this device
                        }
                    }
                }
                .setNegativeButton("بعداً", null)
                .show()
        }
        showRemoteAnnouncementIfAny()
    }

    private fun showRemoteAnnouncementIfAny() {
        lifecycleScope.launch {
            val announcement = withContext(Dispatchers.IO) { AnnouncementManager.fetch() } ?: return@launch
            if (!announcement.enabled) return@launch

            val prefs = PreferencesManager(this@MainActivity)
            if (announcement.id == prefs.getLastSeenAnnouncementId()) return@launch
            if (isFinishing || isDestroyed) return@launch

            AlertDialog.Builder(this@MainActivity)
                .setTitle(announcement.title)
                .setMessage(announcement.message)
                .setCancelable(true)
                .setPositiveButton(announcement.buttonText ?: "متوجه شدم") { _, _ ->
                    prefs.setLastSeenAnnouncementId(announcement.id)
                }
                .show()
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }
}
