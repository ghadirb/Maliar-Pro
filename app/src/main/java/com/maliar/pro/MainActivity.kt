package com.maliar.pro

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.maliar.pro.databinding.ActivityMainBinding
import com.maliar.pro.utils.AnnouncementManager
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // The user denied it; notifications simply won't show. We don't force it.
            }
            requestAssistantActionPermissions()
        }

    /**
     * Fixed store-review bug: this used to request android.permission.READ_CONTACTS at
     * runtime even though it was never declared in the Manifest (a request for an
     * undeclared permission is always auto-denied, and Play/Myket review flags the
     * inconsistency). In practice the assistant's contact matching only ever reads the
     * app's own local Contact table (see ContactManager/ContactDao, a Room database) and
     * never touches the device's real ContactsContract, so the real Android contacts
     * permission was unnecessary and has been dropped entirely rather than added to the
     * Manifest.
     *
     * RECORD_AUDIO and the whole voice-command feature (VoiceCommandActivity, its
     * notification action button, and its Manifest entry) have been temporarily removed
     * for this store submission to unblock Bazaar/Myket review. Nothing here requests
     * that permission anymore. The feature's code isn't deleted from history - just
     * pulled out of this build - so it can be reintroduced later alongside a proper
     * Data Safety / permission-usage explanation for the stores.
     *
     * This Play-Safe test build also intentionally never requests CALL_PHONE, so
     * VoiceCallHelper always falls back to opening the dialer (ACTION_DIAL) instead of
     * placing a call directly (ACTION_CALL).
     */
    private fun requestAssistantActionPermissions() {
        checkExactAlarmPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PreferencesManager(this).isOnboardingCompleted()) {
            startActivity(Intent(this, com.maliar.pro.ui.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Shared/free AI access goes through a server-side proxy now (AIBackendClient),
        // not a locally-decrypted key file - see AutoProvisioningManager for why. Nothing
        // to load here at startup anymore.

        setupNavigation()
        ensureNotificationPermissions()
        // Deferred to after this frame is laid out (rather than called synchronously here)
        // since starting a BiometricPrompt fragment transaction before the Activity has
        // reached onStart/onResume is a known source of flaky IllegalStateExceptions on
        // some OEM ROMs - see authenticateAppIfNeeded().
        binding.root.post { authenticateAppIfNeeded() }
        handleAssistantDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistantDeepLink(intent)
    }

    /** Routes the "نرخ طلا/ارز نوسان داشت" notification tap to the دستیار هوشمند tab with
     *  a ready-made question, instead of just opening whatever tab the app last had open -
     *  see [com.maliar.pro.utils.NotificationHelper.notifyFinancialInsight] and
     *  [com.maliar.pro.utils.PendingAssistantQuestion]. A no-op for every other intent. */
    private fun handleAssistantDeepLink(intent: Intent) {
        if (!intent.getBooleanExtra(com.maliar.pro.utils.NotificationHelper.EXTRA_OPEN_ASSISTANT_MARKET_QUESTION, false)) return
        intent.removeExtra(com.maliar.pro.utils.NotificationHelper.EXTRA_OPEN_ASSISTANT_MARKET_QUESTION)
        com.maliar.pro.utils.PendingAssistantQuestion.pendingQuestion =
            "با توجه به نرخ لحظه‌ای طلا و ارز و بودجه/موجودی فعلی من، چه تحلیلی داری؟"
        runCatching {
            navController.navigate(R.id.assistantFragment)
        }
    }

    private fun authenticateAppIfNeeded() {
        if (!PreferencesManager(this).isBiometricLockEnabled()) return
        // Try fingerprint/biometric first, then fall back to the phone's own PIN/pattern/
        // password screen if that fails for any reason (canAuthenticate() reporting
        // available but authenticate() then throwing is a known issue on cheap/unofficial
        // devices - e.g. reported on a G-Plus P10 running Android 10 - where the vendor's
        // fingerprint HAL/binder is broken even though it announces itself as present).
        // DEVICE_CREDENTIAL used alone (not combined with a biometric type) doesn't have
        // the API<30 restriction that combining it with BIOMETRIC_WEAK/STRONG does, and
        // doesn't depend on the fingerprint hardware at all, so it's a solid fallback.
        tryAuthenticate(
            authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK,
            title = "بازکردن مالیار",
            subtitle = "برای مشاهده اطلاعات مالی، اثر انگشت خود را تأیید کنید",
            onSuccess = { /* app already open; nothing else to do */ },
            onUnavailable = {
                tryAuthenticate(
                    authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    title = "بازکردن مالیار",
                    subtitle = "قفل بیومتریک این دستگاه پاسخ نمی‌دهد؛ رمز/الگوی صفحه گوشی را وارد کنید",
                    onSuccess = { /* app already open; nothing else to do */ },
                    onUnavailable = {
                        // Neither fingerprint nor a phone lock screen is usable. Don't trap
                        // the user outside their own app over a broken/unset OEM feature -
                        // let them in, but say so plainly and turn the broken toggle off so
                        // this doesn't repeat every launch.
                        Toast.makeText(
                            this,
                            "قفل بیومتریک این دستگاه پاسخ نمی‌دهد و قفل صفحه‌ای هم تنظیم نشده. قفل مالیار خاموش شد؛ می‌توانید دوباره از تنظیمات آن را روشن کنید.",
                            Toast.LENGTH_LONG
                        ).show()
                        PreferencesManager(this).setBiometricLockEnabled(false)
                    },
                    onFailed = { /* wrong PIN/pattern entered; let them retry, prompt stays open */ },
                    onCancelledOrError = { finish() }
                )
            },
            onFailed = { Toast.makeText(this, "اثر انگشت شناسایی نشد.", Toast.LENGTH_SHORT).show() },
            onCancelledOrError = { finish() }
        )
    }

    /** One authenticate attempt with one [authenticators] value (never a combination on
     *  API < 30 - see the comment above). [onUnavailable] fires when the check/attempt
     *  can't even start (no hardware, nothing enrolled, or - the case this exists for - a
     *  broken vendor implementation throwing where it shouldn't); [onCancelledOrError]
     *  fires for a real prompt-level error (user backed out, too many attempts, etc). */
    private fun tryAuthenticate(
        authenticators: Int,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit,
        onFailed: () -> Unit,
        onCancelledOrError: () -> Unit
    ) {
        try {
            val can = BiometricManager.from(this).canAuthenticate(authenticators)
            if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                onUnavailable()
                return
            }
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onCancelledOrError()
                    }
                    override fun onAuthenticationFailed() {
                        onFailed()
                    }
                })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
        } catch (e: RuntimeException) {
            // SecurityException / IllegalStateException / IllegalArgumentException all
            // mean this particular authenticator can't be used right now - try the next
            // one in the chain rather than crashing or giving up outright.
            onUnavailable()
        }
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
     * Many Iranian-market phone brands (Xiaomi/MIUI, Samsung, Huawei, etc.) aggressively
     * freeze/force-stop apps that aren't exempted from battery optimization. When that
     * happens to Maliar Pro, reminders stop firing and the home-screen widget gets stuck
     * showing the system's own "tap to open the app so the widget can refresh" placeholder
     * instead of real data - it looks broken even though nothing crashed. Asking for this
     * exemption (a single system dialog, not silently granted) is the standard fix. Only
     * asked once per "بعداً" dismissal; if the user already granted it, or already declined
     * once, we don't ask again on every launch.
     */
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val alreadyExempt = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
        val prefs = PreferencesManager(this)

        if (alreadyExempt || prefs.hasBatteryOptimizationPromptBeenDismissed()) {
            showRemoteAnnouncementIfAny()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("پایداری یادآوری‌ها و ویجت")
            .setMessage(
                "بعضی گوشی‌ها برای صرفه‌جویی باتری، برنامه‌های استفاده‌نشده را در پس‌زمینه " +
                    "می‌بندند؛ در این حالت ممکن است یادآوری‌ها دیر برسند یا ویجت صفحه اصلی " +
                    "به‌روزرسانی نشود. برای جلوگیری از این مشکل، مالیار پرو را از بهینه‌سازی " +
                    "باتری معاف کنید."
            )
            .setPositiveButton("رفتن به تنظیمات") { _, _ ->
                requestIgnoreBatteryOptimizations()
                showRemoteAnnouncementIfAny()
            }
            .setNegativeButton("بعداً") { _, _ ->
                prefs.setBatteryOptimizationPromptDismissed(true)
                showRemoteAnnouncementIfAny()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Some ROMs reject the direct per-app request intent; fall back to the general
            // battery-optimization list so the person can still find and allow it manually.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignored: Exception) {
                // Device exposes neither screen; nothing more we can do here.
            }
        }
    }

    /**
     * Shown last, after the permission dialogs, so it never fights with them for the
     * screen. Reads a small JSON file from a URL you control (see AnnouncementManager) -
     * whatever "id" is in that file decides whether this has already been dismissed, so
     * changing the id later (a new announcement) shows it again to everyone, including
     * people who dismissed an older one. Any network failure here is silent - this is
     * purely informational and must never get in the way of using the app.
     */
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
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }
}
