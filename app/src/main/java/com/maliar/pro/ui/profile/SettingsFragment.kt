package com.maliar.pro.ui.profile

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
        val notificationMode = prefs.getNotificationMode()

        when (notificationMode) {
            "none" -> binding.noneNotification.isChecked = true
            "simple" -> binding.simpleNotification.isChecked = true
            "action" -> binding.actionNotification.isChecked = true
            else -> binding.simpleNotification.isChecked = true
        }

        binding.notificationModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.noneNotification.id -> "none"
                binding.simpleNotification.id -> "simple"
                binding.actionNotification.id -> "action"
                else -> "simple"
            }
            prefs.setNotificationMode(mode)
            Toast.makeText(requireContext(), "حالت نوتیفیکیشن تغییر کرد: $mode", Toast.LENGTH_SHORT).show()
            // Trigger receiver integration if needed
        }
    }
}