package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.FinancialStatusManager

class AssistantViewModelFactory(
    private val accountingManager: AccountingManager,
    private val reminderManager: ReminderManager,
    private val financialManager: FinancialStatusManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssistantViewModel(accountingManager, reminderManager, financialManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
