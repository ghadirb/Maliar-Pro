package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.FixedIncome
import com.maliar.pro.database.IncomeType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FixedIncomeListViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    val incomes = financialManager.getAllFixedIncomes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome = incomes.map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addIncome(type: IncomeType, name: String, amount: Double, description: String = "") {
        viewModelScope.launch {
            financialManager.addFixedIncome(FixedIncome(type = type, title = name, amount = amount, description = description))
        }
    }

    fun deleteIncome(income: FixedIncome) {
        viewModelScope.launch { financialManager.deleteFixedIncome(income) }
    }
}

class FixedIncomeListViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FixedIncomeListViewModel(financialManager) as T
}
