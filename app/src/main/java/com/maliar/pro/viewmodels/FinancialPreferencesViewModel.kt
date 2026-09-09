package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.FinancialPreferences
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.PurchasePreference
import com.maliar.pro.database.RiskTolerance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinancialPreferencesViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    val preferences = financialManager.getPreferencesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun savePreferences(
        emergencyFund: Double,
        savingGoal: Double,
        riskTolerance: RiskTolerance,
        purchasePreference: PurchasePreference
    ) {
        viewModelScope.launch {
            val current = financialManager.getPreferences()
            if (current != null) {
                financialManager.updatePreferences(
                    current.copy(
                        emergencyFundTarget = emergencyFund,
                        monthlySavingGoal = savingGoal,
                        riskTolerance = riskTolerance,
                        purchasePreference = purchasePreference,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                financialManager.savePreferences(
                    FinancialPreferences(
                        riskTolerance = riskTolerance,
                        investmentInterest = false,
                        savingsInterest = true,
                        purchasePreference = purchasePreference,
                        emergencyFundTarget = emergencyFund,
                        monthlySavingGoal = savingGoal
                    )
                )
            }
        }
    }
}

class FinancialPreferencesViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FinancialPreferencesViewModel(financialManager) as T
}
