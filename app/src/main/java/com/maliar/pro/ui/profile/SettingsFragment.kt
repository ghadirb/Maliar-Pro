package com.maliar.pro.ui.profile

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.databinding.FragmentSettingsBinding
import com.maliar.pro.utils.AutoBackupWorker
import com.maliar.pro.utils.BackupManager
import com.maliar.pro.utils.FinancialInsightWorker
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding
    private val prefs by lazy { PreferencesManager(requireContext()) }

    // Must be registered before the fragment reaches CREATED - a property initializer
    // (runs during construction) is early enough, an onViewCreated call would not be.
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) performBackup(uri)
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmAndRestore(uri)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSubscriptionSetting()
        setupNotificationSettings()
        setupBackgroundServiceSetting()
        setupBackupSettings()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from SubscriptionActivity after a payment - reflect the new status
        // right away instead of showing the stale one from before the person left.
        renderSubscriptionStatus()
    }

    private fun setupSubscriptionSetting() {
        renderSubscriptionStatus()
        binding.manageSubscriptionButton.setOnClickListener {
            startActivity(Intent(requireContext(), com.maliar.pro.ui.profile.SubscriptionActivity::class.java))
        }
    }

    private fun renderSubscriptionStatus() {
        val context = requireContext()
        val premiumLabel = com.maliar.pro.utils.SubscriptionManager.premiumExpiryLabel(context)
        binding.subscriptionStatusText.text = when {
            premiumLabel != null -> premiumLabel
            com.maliar.pro.utils.SubscriptionManager.hasPersonalKey(context) ->
                "شما از کلید هوش مصنوعی شخصی خودتان استفاده می‌کنید - بدون محدودیت."
            else -> {
                val remaining = com.maliar.pro.utils.SubscriptionManager.remainingFreeLifetime(context)
                "$remaining از ${com.maliar.pro.utils.SubscriptionManager.FREE_AI_LIFETIME_LIMIT} پیام رایگان اولیه باقی مانده"
            }
        }
    }

    /** This is intentionally opt-in: no automatic start on app launch or device boot. */
    private fun setupBackgroundServiceSetting() {
        binding.backgroundServiceSwitch.isChecked = prefs.isBackgroundServiceEnabled()
        binding.backgroundServiceSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.setBackgroundServiceEnabled(enabled)
            if (enabled) {
                com.maliar.pro.services.MaliarBackgroundService.start(requireContext())
                Toast.makeText(requireContext(), "حالت پایداری یادآوری فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                com.maliar.pro.services.MaliarBackgroundService.stop(requireContext())
                Toast.makeText(requireContext(), "حالت پایداری یادآوری غیرفعال شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNotificationSettings() {
        // Only two real states now: reminders fire completely silently, or with the full
        // action notification (انجام شد / تعویق / تماس). The old "ساده" middle option never
        // matched what people expected from a notification "type" and was removed - anyone
        // with it saved from before just lands on the "با نوتیفیکیشن" option below.
        val notificationMode = prefs.getNotificationMode()
        when (notificationMode) {
            "none" -> binding.noneNotification.isChecked = true
            else -> binding.actionNotification.isChecked = true
        }

        binding.notificationModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.noneNotification.id -> "none"
                else -> "action"
            }
            prefs.setNotificationMode(mode)

            if (mode == "none") {
                // The point of this mode is that it takes effect immediately, not just for
                // the next reminder - so any reminder notification already on screen right
                // now is cleared too. Only the reminder channel is touched, so the always-on
                // background-service notification (a separate feature) is left alone.
                val notificationManager = requireContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    notificationManager.activeNotifications
                        .filter { it.notification.channelId == com.maliar.pro.MaliarProApplication.REMINDER_CHANNEL_ID }
                        .forEach { notificationManager.cancel(it.id) }
                }
                Toast.makeText(requireContext(), "نوتیفیکیشن‌های یادآوری غیرفعال و پاک شدند", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "نوتیفیکیشن یادآوری فعال شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Backup uses Storage Access Framework (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT)
     * instead of a hand-built Google Drive integration - the system picker that opens
     * already lets the person choose "Google Drive" (or any other cloud app / plain device
     * storage) as the target on its own, with no OAuth setup needed here at all.
     */
    private fun setupBackupSettings() {
        binding.backupNowButton.setOnClickListener {
            val fileName = "maliar-pro-backup-${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.zip"
            backupLauncher.launch(fileName)
        }
        binding.restoreButton.setOnClickListener {
            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }

        binding.autoBackupSwitch.isChecked = prefs.isAutoBackupEnabled()
        binding.autoBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && prefs.getLastBackupUri() == null) {
                Toast.makeText(requireContext(), "اول یک‌بار «تهیه پشتیبان» را بزنید تا مقصد ذخیره شود", Toast.LENGTH_LONG).show()
                binding.autoBackupSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.setAutoBackupEnabled(isChecked)
            if (isChecked) {
                AutoBackupWorker.schedule(requireContext())
                Toast.makeText(requireContext(), "پشتیبان‌گیری خودکار روزانه فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                AutoBackupWorker.cancel(requireContext())
            }
        }

        binding.financialInsightsSwitch.isChecked = prefs.isFinancialInsightsEnabled()
        binding.financialInsightsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setFinancialInsightsEnabled(isChecked)
            if (isChecked) {
                FinancialInsightWorker.schedule(requireContext())
                Toast.makeText(requireContext(), "پیشنهادهای هوشمند مالی فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                FinancialInsightWorker.cancel(requireContext())
            }
        }
    }

    private fun performBackup(uri: Uri) {
        // A picked document Uri only grants temporary access by default - persisting it is
        // what lets the daily auto-backup worker reuse it later without asking again.
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { /* some providers don't support persisting - backup itself still works now */ }

        setBackupStatus("⏳ در حال تهیه پشتیبان…")
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { BackupManager.backupToUri(requireContext(), uri) }
            if (ok) {
                prefs.setLastBackupUri(uri.toString())
                setBackupStatus("✅ پشتیبان با موفقیت ذخیره شد.")
            } else {
                setBackupStatus("❌ تهیه پشتیبان با خطا مواجه شد.")
            }
        }
    }

    private fun confirmAndRestore(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("بازیابی اطلاعات")
            .setMessage("با این کار تمام اطلاعات فعلی برنامه (حسابداری، یادآوری‌ها، مخاطبین) با اطلاعات داخل این فایل پشتیبان جایگزین می‌شود و برنامه بسته می‌شود. ادامه می‌دهید؟")
            .setPositiveButton("بله، بازیابی کن") { _, _ -> performRestore(uri) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun performRestore(uri: Uri) {
        setBackupStatus("⏳ در حال بازیابی…")
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { BackupManager.restoreFromUri(requireContext(), uri) }
            if (ok) {
                // The live Room connection was closed as part of the restore - continuing
                // to use it (or reopening it) in this same process risks reading a half-
                // swapped database, so the app must fully restart before anything touches
                // it again.
                AlertDialog.Builder(requireContext())
                    .setTitle("بازیابی انجام شد")
                    .setMessage("اطلاعات با موفقیت بازیابی شد. برای دیدن تغییرات، برنامه الان بسته می‌شود - دوباره آن را باز کنید.")
                    .setCancelable(false)
                    .setPositiveButton("باشه") { _, _ ->
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                    .show()
            } else {
                setBackupStatus("❌ بازیابی ناموفق بود. فایل انتخاب‌شده معتبر نیست.")
            }
        }
    }

    private fun setBackupStatus(text: String) {
        binding.backupStatusText.visibility = View.VISIBLE
        binding.backupStatusText.text = text
    }
}
