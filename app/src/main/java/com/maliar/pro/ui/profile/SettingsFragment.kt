package com.maliar.pro.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        setupSettings()
    }

    private fun setupSettings() {
        val notificationMode = prefs.getNotificationMode()
        
        when (notificationMode) {
            "simple" -> binding.simpleNotification.isChecked = true
            "action" -> binding.actionNotification.isChecked = true
            else -> binding.simpleNotification.isChecked = true
        }

        binding.notificationModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.simpleNotification.id -> prefs.setNotificationMode("simple")
                binding.actionNotification.id -> prefs.setNotificationMode("action")
            }
        }
    }
}
