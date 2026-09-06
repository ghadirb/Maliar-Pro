package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentForecastBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

/**
 * "آینده مالی من" - the dedicated forecast screen (spec item #4). Shows the same
 * deterministic 30/60/90-day projection [AccountingViewModel.financialForecasts]
 * already computes for the small inline summary on the accounting dashboard, but as a
 * full breakdown (موجودی فعلی → درآمد → هزینه → اقساط → موجودی احتمالی) with a
 * period switcher and a cautious decline/deficit warning when one applies.
 *
 * Deliberately reuses [AccountingViewModel] rather than introducing a second,
 * parallel forecast calculation - one deterministic source of truth for both this
 * screen and the dashboard's summary line.
 */
class ForecastFragment : Fragment() {

    private lateinit var binding: FragmentForecastBinding
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(
            AccountingManager(requireContext()),
            FinancialStatusManager(requireContext())
        )
    }

    private var selectedDays = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentForecastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.forecastPeriodToggle.check(R.id.forecast30Button)
        binding.forecastPeriodToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedDays = when (checkedId) {
                R.id.forecast60Button -> 60
                R.id.forecast90Button -> 90
                else -> 30
            }
            renderCurrentForecast()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.financialForecasts.collect { renderCurrentForecast() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.forecastAlert.collect { alert ->
                if (alert.isNullOrBlank()) {
                    binding.forecastAlertCard.visibility = View.GONE
                } else {
                    binding.forecastAlertCard.visibility = View.VISIBLE
                    binding.forecastAlertText.text = alert
                }
            }
        }
    }

    private fun renderCurrentForecast() {
        val forecast = viewModel.financialForecasts.value.firstOrNull { it.days == selectedDays }
        if (forecast == null || !forecast.hasEnoughData) {
            binding.forecastBreakdownCard.visibility = View.GONE
            binding.forecastInsufficientDataText.visibility = View.VISIBLE
            return
        }
        binding.forecastBreakdownCard.visibility = View.VISIBLE
        binding.forecastInsufficientDataText.visibility = View.GONE

        binding.forecastStartingBalanceText.text = CurrencyFormatter.format(forecast.startingBalance)
        binding.forecastIncomeText.text = "+${CurrencyFormatter.format(forecast.projectedIncome)}"
        binding.forecastExpenseText.text = "-${CurrencyFormatter.format(forecast.projectedExpense)}"
        binding.forecastCommitmentsText.text = "-${CurrencyFormatter.format(forecast.projectedCommitments)}"
        binding.forecastProjectedBalanceText.text = CurrencyFormatter.format(forecast.projectedBalance)
        binding.forecastProjectedBalanceText.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (forecast.projectedBalance < 0) R.color.error else R.color.text_primary
            )
        )
        binding.forecastPeriodLabelText.text = "برآورد $selectedDays روز آینده · بر اساس اطلاعات ثبت‌شده"
    }
}
