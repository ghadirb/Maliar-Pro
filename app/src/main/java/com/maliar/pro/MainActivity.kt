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
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }
}
