package com.maliar.pro.ui.profile

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.maliar.pro.databinding.FragmentSettingsBinding
import com.maliar.pro.utils.PreferencesManager

class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding
    private val prefs by lazy { PreferencesManager(requireContext()) }

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
        setupNotificationSettings()
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
}