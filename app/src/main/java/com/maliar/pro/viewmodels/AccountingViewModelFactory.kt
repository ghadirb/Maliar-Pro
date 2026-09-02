package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager

class AccountingViewModelFactory(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccountingViewModel(accountingManager, financialStatusManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
