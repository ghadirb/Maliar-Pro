package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Asset
import com.maliar.pro.database.AssetType
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetListViewModel(private val financialManager: FinancialStatusManager) : ViewModel() {

    val assets = financialManager.getAllAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAssets = assets.map { list -> list.sumOf { it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addAsset(type: AssetType, name: String, amount: Double, description: String = "") {
        viewModelScope.launch {
            financialManager.addAsset(Asset(type = type, title = name, value = amount, description = description))
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch { financialManager.deleteAsset(asset) }
    }
}

class AssetListViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetListViewModel(financialManager) as T
}
