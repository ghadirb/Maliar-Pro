package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.FinancialStatusManager

class FinancialStatusViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinancialStatusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinancialStatusViewModel(financialManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
