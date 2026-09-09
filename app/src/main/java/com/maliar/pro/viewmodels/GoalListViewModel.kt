package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.FinancialGoal
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.GoalType
import com.maliar.pro.database.Priority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalListViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    val goals = financialManager.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addGoal(
        type: GoalType,
        name: String,
        targetAmount: Double,
        targetDateMillis: Long,
        priority: Priority = Priority.MEDIUM
    ) {
        viewModelScope.launch {
            financialManager.addGoal(
                FinancialGoal(
                    type = type,
                    title = name,
                    targetAmount = targetAmount,
                    targetDate = targetDateMillis,
                    priority = priority
                )
            )
        }
    }

    fun deleteGoal(goal: FinancialGoal) {
        viewModelScope.launch { financialManager.deleteGoal(goal) }
    }
}

class GoalListViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GoalListViewModel(financialManager) as T
}
