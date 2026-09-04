package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Asset
import com.maliar.pro.database.AssetType
import com.maliar.pro.database.AccountPurpose
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

    fun addAsset(
        type: AssetType,
        name: String,
        amount: Double,
        description: String = "",
        purpose: AccountPurpose = AccountPurpose.NORMAL
    ) {
        viewModelScope.launch {
            val id = financialManager.addAsset(
                Asset(type = type, title = name, value = amount, description = description, purpose = purpose)
            )
            if (purpose == AccountPurpose.DAILY_SPENDING) {
                financialManager.setAccountPurpose(id, purpose)
            }
        }
    }

    fun setAccountPurpose(assetId: Long, purpose: AccountPurpose) {
        viewModelScope.launch { financialManager.setAccountPurpose(assetId, purpose) }
    }

    /** Adds a gold asset specified by weight; see [FinancialStatusManager.addGoldAsset]
     *  for why its value is computed from the live rate instead of typed in by hand. */
    fun addGoldAsset(name: String, grams: Double, purpose: AccountPurpose = AccountPurpose.NORMAL) {
        viewModelScope.launch {
            financialManager.addGoldAsset(name, grams, purpose)
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch { financialManager.deleteAsset(asset) }
    }

    /** Best-effort re-price of any weight-based gold assets against the current rate;
     *  called when this screen opens so the list is fresh without waiting for the daily
     *  background worker. Room's Flow-backed [assets] picks up the change automatically. */
    fun refreshGoldValues() {
        viewModelScope.launch { financialManager.refreshGoldAssetValues() }
    }
}

class AssetListViewModelFactory(private val financialManager: FinancialStatusManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetListViewModel(financialManager) as T
}
