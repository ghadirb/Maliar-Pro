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

    /** 0 = the current/ongoing period, 1 = one period back, 2 = two back, etc. Reset to 0
     *  whenever the person switches the period *type* (daily/weekly/monthly/yearly chip),
     *  since "one period back" means something different for each type. */
    private val _periodOffset = MutableStateFlow(0)
    val periodOffset = _periodOffset.asStateFlow()

    private val _rangeLabel = MutableStateFlow("")
    val rangeLabel = _rangeLabel.asStateFlow()

    private val _report = MutableStateFlow<FinancialReport?>(null)
    val report = _report.asStateFlow()

    init {
        loadReport(ReportPeriod.MONTHLY)
    }

    /** Called when the person taps a period-type chip (روزانه/هفتگی/ماهانه/سالانه). */
    fun loadReport(period: ReportPeriod) {
        _selectedPeriod.value = period
        _periodOffset.value = 0
        refresh()
    }

    /** Steps one window further into the past, e.g. from this month to last month. */
    fun goToPreviousPeriod() {
        _periodOffset.value += 1
        refresh()
    }

    /** Steps one window back toward the present. No-ops once already at the current period. */
    fun goToNextPeriod() {
        if (_periodOffset.value <= 0) return
        _periodOffset.value -= 1
        refresh()
    }

    private fun refresh() {
        val period = _selectedPeriod.value
        val offset = _periodOffset.value
        _rangeLabel.value = reportManager.rangeLabel(period, offset)
        viewModelScope.launch {
            _report.value = reportManager.buildReport(period, offset)
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
