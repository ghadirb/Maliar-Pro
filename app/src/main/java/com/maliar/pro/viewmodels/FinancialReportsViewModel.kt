package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialReport
import com.maliar.pro.database.FinancialReportManager
import com.maliar.pro.database.ReportPeriod
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FinancialReportsViewModel(
    accountingManager: AccountingManager,
    preferencesManager: PreferencesManager
) : ViewModel() {

    private val reportManager = FinancialReportManager(accountingManager, preferencesManager.getFinancialPeriodStartDay())

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.MONTHLY)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _report = MutableStateFlow<FinancialReport?>(null)
    val report = _report.asStateFlow()

    init {
        loadReport(ReportPeriod.MONTHLY)
    }

    fun loadReport(period: ReportPeriod) {
        _selectedPeriod.value = period
        viewModelScope.launch {
            _report.value = reportManager.buildReport(period)
        }
    }
}

class FinancialReportsViewModelFactory(
    private val accountingManager: AccountingManager,
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FinancialReportsViewModel(accountingManager, preferencesManager) as T
}
