package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.ReminderManager

class RemindersViewModelFactory(private val reminderManager: ReminderManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RemindersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RemindersViewModel(reminderManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
