package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager

class DueSoonViewModelFactory(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager,
    private val debtorManager: DebtorManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DueSoonViewModel(accountingManager, financialStatusManager, debtorManager) as T
    }
}
