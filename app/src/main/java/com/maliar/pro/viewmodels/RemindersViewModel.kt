package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.SmartReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemindersViewModel(private val smartManager: SmartReminderManager) : ViewModel() {

    private val _reminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = _reminders.asStateFlow()

    private val _filter = MutableStateFlow("all")
    val filter: StateFlow<String> = _filter.asStateFlow()

    init {
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            smartManager.getAllReminders().collect { allReminders ->
                applyFilter(allReminders)
            }
        }
    }

    fun setFilter(filterType: String) {
        _filter.value = filterType
        viewModelScope.launch {
            val allReminders = smartManager.getAllRemindersList()
            applyFilter(allReminders)
        }
    }

    private fun applyFilter(allReminders: List<ReminderEntity>) {
        val filtered = when (_filter.value) {
            "time" -> allReminders.filter { it.reminderType == "SIMPLE" || it.reminderType == "TASK" }
            "recurring" -> allReminders.filter { it.repeatPattern != "ONCE" }
            "high_priority" -> allReminders.filter { it.priority == "HIGH" || it.priority == "URGENT" }
            else -> allReminders
        }
        _reminders.value = filtered
    }

    fun addReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            smartManager.addReminder(reminder)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            smartManager.deleteReminder(reminder)
        }
    }

    fun completeReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            smartManager.completeReminder(reminder.id)
        }
    }

    fun updateReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            smartManager.updateReminder(reminder)
        }
    }
}