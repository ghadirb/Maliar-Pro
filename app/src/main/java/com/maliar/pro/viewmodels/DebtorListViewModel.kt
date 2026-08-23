package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Debtor
import com.maliar.pro.database.DebtorManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One [Debtor] plus its live remaining balance (amount - sum of payments so far), used by
 *  the list row so it doesn't need a separate DB read per item. */
data class DebtorWithBalance(val debtor: Debtor, val totalPaid: Double) {
    val remaining: Double get() = (debtor.amount - totalPaid).coerceAtLeast(0.0)
}

class DebtorListViewModel(private val debtorManager: DebtorManager) : ViewModel() {

    val debtors = debtorManager.getAllDebtors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recomputed whenever the debtor list changes; each item's paid total is fetched
     *  once per emission rather than kept as a live per-row Flow, which is more than
     *  enough for a screen that isn't updated many times per second. */
    val debtorsWithBalance = debtorManager.getAllDebtors().map { list ->
        list.map { debtor -> DebtorWithBalance(debtor, debtorManager.getTotalPaid(debtor.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTheyOweMe = debtorsWithBalance.map { list ->
        list.filter { it.debtor.direction == com.maliar.pro.database.DebtorDirection.THEY_OWE_ME && !it.debtor.isSettled }
            .sumOf { it.remaining }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIOweThem = debtorsWithBalance.map { list ->
        list.filter { it.debtor.direction == com.maliar.pro.database.DebtorDirection.I_OWE_THEM && !it.debtor.isSettled }
            .sumOf { it.remaining }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun deleteDebtor(debtor: Debtor) {
        viewModelScope.launch { debtorManager.deleteDebtor(debtor) }
    }
}

class DebtorListViewModelFactory(private val debtorManager: DebtorManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DebtorListViewModel(debtorManager) as T
}
