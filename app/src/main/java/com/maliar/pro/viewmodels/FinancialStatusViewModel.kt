package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FinancialStatusViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    private val _totalAssets = MutableStateFlow(0.0)
    val totalAssets: StateFlow<Double> = _totalAssets.asStateFlow()

    private val _totalDebts = MutableStateFlow(0.0)
    val totalDebts: StateFlow<Double> = _totalDebts.asStateFlow()

    private val _netWorth = MutableStateFlow(0.0)
    val netWorth: StateFlow<Double> = _netWorth.asStateFlow()

    private val _completionPercentage = MutableStateFlow(0)
    val completionPercentage: StateFlow<Int> = _completionPercentage.asStateFlow()

    init {
        loadFinancialData()
    }

    fun loadFinancialData() {
        viewModelScope.launch {
            val assets = financialManager.getTotalAssets()
            val debts = financialManager.getTotalUnpaidDebts()
            val completion = financialManager.getCompletionPercentage()

            _totalAssets.value = assets
            _totalDebts.value = debts
            _netWorth.value = assets - debts
            _completionPercentage.value = completion
        }
    }

    fun addAsset(name: String, amount: Double) {
        viewModelScope.launch {
            financialManager.addAsset(name, amount)
            loadFinancialData()
        }
    }

    fun addDebt(name: String, amount: Double) {
        viewModelScope.launch {
            financialManager.addDebt(name, amount)
            loadFinancialData()
        }
    }

    fun addFinancialGoal(name: String, targetAmount: Double) {
        viewModelScope.launch {
            financialManager.addFinancialGoal(name, targetAmount)
            loadFinancialData()
        }
    }

    fun addFixedIncome(name: String, amount: Double) {
        viewModelScope.launch {
            financialManager.addFixedIncome(name, amount)
            loadFinancialData()
        }
    }

    fun setFinancialPreferences(emergencyFund: Double, savingGoal: Double) {
        viewModelScope.launch {
            financialManager.setPreferences(emergencyFund, savingGoal)
            loadFinancialData()
        }
    }

    fun refreshData() {
        loadFinancialData()
    }
}