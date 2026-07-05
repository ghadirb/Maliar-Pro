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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.maliar.pro.databinding.ActivityMainBinding
import com.maliar.pro.utils.KeyManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // The user denied it; notifications simply won't show. We don't force it.
            }
            requestAssistantActionPermissions()
        }

    /**
     * The assistant's "call so-and-so" command and the notification action buttons need
     * CALL_PHONE / READ_CONTACTS at runtime (declaring them in the manifest alone does
     * nothing on Android 6+). Without this, VoiceCallHelper.makeCall() throws a
     * SecurityException every single time and the assistant silently "does nothing" when
     * asked to call someone - which is exactly the symptom being reported.
     */
    private val assistantPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            checkExactAlarmPermission()
        }

    private fun requestAssistantActionPermissions() {
        val needed = listOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CONTACTS,
            // Needed for the assistant's voice-command notification and for smart
            // reminders' spoken "انجام شد" voice-response detection.
            android.Manifest.permission.RECORD_AUDIO
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            assistantPermissionsLauncher.launch(needed.toTypedArray())
        } else {
            checkExactAlarmPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load encrypted keys at startup
        lifecycleScope.launch {
            try {
                KeyManager.loadKeys(this@MainActivity)
                // Initialize AI services with keys
            } catch (e: Exception) {
                // Handle key loading error
            }
        }

        setupNavigation()
        ensureNotificationPermissions()
    }

    /**
     * On Android 13+ (API 33+) notifications require a runtime permission grant.
     * On Android 12+ (API 31+) exact alarms require the user to explicitly
     * allow "Alarms & reminders" in system settings. Without both of these,
     * reminders/notifications silently never fire.
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
        requestAssistantActionPermissions()
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
     * Many OEMs (Xiaomi/MIUI, Huawei/EMUI, Oppo/ColorOS, Samsung, etc.) aggressively kill
     * background apps and silently prevent scheduled alarms/notifications from firing at all,
     * even when POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM are both granted, unless the app
     * is explicitly excluded from battery optimization. This is one of the most common
     * real-world reasons reminders "just don't do anything" on real devices.
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
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }
}
