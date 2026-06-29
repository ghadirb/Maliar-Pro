package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.AccountingManager

class AccountingViewModelFactory(private val accountingManager: AccountingManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccountingViewModel(accountingManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
