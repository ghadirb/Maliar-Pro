package com.maliar.pro.ui.market

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.AssetType
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentMarketRatesBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.MarketRatesRepository
import kotlinx.coroutines.launch

class MarketRatesFragment : Fragment() {

    private lateinit var binding: FragmentMarketRatesBinding
    private lateinit var repository: MarketRatesRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMarketRatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MarketRatesRepository(requireContext())
        binding.refreshButton.setOnClickListener { load(force = true) }
        load(force = false)
    }

    private fun load(force: Boolean) {
        binding.statusText.text = if (force) "در حال به‌روزرسانی..." else "در حال خواندن نرخ‌ها..."
        binding.refreshButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val rates = repository.getRates(forceRefresh = force)
            binding.refreshButton.isEnabled = true
            if (rates == null) {
                binding.statusText.text = "نرخ‌ها الان در دسترس نیستند. اینترنت را بررسی کنید و دوباره تلاش کنید."
                binding.analysisText.text = ""
                return@launch
            }
            binding.statusText.text = "منبع: فایل عمومی نرخ‌ها · ${rates.updatedAt}"
            binding.usdValue.text = CurrencyFormatter.format(rates.usdToman)
            binding.goldValue.text = CurrencyFormatter.format(rates.gold18Toman)
            binding.coinValue.text = rates.coinEmamiToman?.let { CurrencyFormatter.format(it) } ?: "—"
            binding.halfCoinValue.text = rates.coinHalfToman?.let { CurrencyFormatter.format(it) } ?: "—"
            binding.quarterCoinValue.text = rates.coinQuarterToman?.let { CurrencyFormatter.format(it) } ?: "—"

            val assets = FinancialStatusManager(requireContext()).getAllAssetsList()
            val liquid = assets.filter {
                it.type == AssetType.CASH || it.type == AssetType.BANK_ACCOUNT || it.type == AssetType.DEPOSIT
            }.sumOf { it.value }
            val accounting = AccountingManager(requireContext())
            val surplus = accounting.getMonthlyIncome() - accounting.getMonthlyExpense()
            binding.analysisText.text = repository.buildAnalysis(rates, liquid, surplus)
        }
    }
}
