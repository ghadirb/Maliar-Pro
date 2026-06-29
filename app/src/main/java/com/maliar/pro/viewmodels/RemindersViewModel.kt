package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Reminder
import com.maliar.pro.database.ReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemindersViewModel(private val reminderManager: ReminderManager) : ViewModel() {

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    init {
        loadReminders()
    }

    fun loadReminders() {
        viewModelScope.launch {
            val reminderList = reminderManager.getActiveRemindersList()
            _reminders.value = reminderList
        }
    }

    fun addReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderManager.addReminder(reminder)
            loadReminders()
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderManager.deleteReminder(reminder)
            loadReminders()
        }
    }

    fun markAsCompleted(reminder: Reminder) {
        viewModelScope.launch {
            reminderManager.markAsCompleted(reminder)
            loadReminders()
        }
    }
}
