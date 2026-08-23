package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.DebtorManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DebtorDetailViewModel(private val debtorManager: DebtorManager, private val debtorId: Long) : ViewModel() {

    val debtor = debtorManager.getDebtorByIdFlow(debtorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val payments = debtorManager.getPaymentsForDebtor(debtorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPaid = debtorManager.getTotalPaidFlow(debtorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
}

class DebtorDetailViewModelFactory(
    private val debtorManager: DebtorManager,
    private val debtorId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DebtorDetailViewModel(debtorManager, debtorId) as T
}
