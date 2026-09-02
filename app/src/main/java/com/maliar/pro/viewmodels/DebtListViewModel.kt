package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Debt
import com.maliar.pro.database.DebtType
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtListViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    val debts = financialManager.getAllDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnpaidDebts = debts.map { list -> list.filter { !it.isPaid }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addDebt(type: DebtType, name: String, amount: Double, description: String = "") {
        viewModelScope.launch {
            financialManager.addDebt(Debt(type = type, title = name, amount = amount, description = description))
        }
    }

    fun toggleDebtPaid(debt: Debt) {
        viewModelScope.launch { financialManager.updateDebt(debt.copy(isPaid = !debt.isPaid)) }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch { financialManager.deleteDebt(debt) }
    }
}

class DebtListViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DebtListViewModel(financialManager) as T
}
