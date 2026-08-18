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

    private fun setupBackgroundServiceSetting() {
        binding.backgroundServiceSwitch.isChecked = prefs.isBackgroundServiceEnabled()
        binding.backgroundServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBackgroundServiceEnabled(isChecked)
            if (isChecked) {
                com.maliar.pro.services.MaliarBackgroundService.start(requireContext())
                Toast.makeText(requireContext(), "برنامه در پس‌زمینه فعال ماند", Toast.LENGTH_SHORT).show()
            } else {
                com.maliar.pro.services.MaliarBackgroundService.stop(requireContext())
                Toast.makeText(requireContext(), "نوتیفیکیشن پس‌زمینه حذف شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNotificationSettings() {
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
    }

    private fun performBackup(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { }

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
