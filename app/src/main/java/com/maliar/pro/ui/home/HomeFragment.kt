package com.maliar.pro.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentHomeBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

/**
 * "خانه" - a single-glance overview tab pulling together the highlights already
 * computed elsewhere in the app (balance, category spending, budget, forecast) into one
 * visual summary, instead of the person having to open each section separately to see
 * how things stand. Deliberately read-only and purely a summary/navigation hub - it
 * introduces no new financial calculations of its own; every number here comes from
 * [AccountingViewModel] flows that already back the accounting dashboard and forecast
 * screen, so this can never disagree with them.
 */
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(
            AccountingManager(requireContext()),
            FinancialStatusManager(requireContext())
        )
    }

    /** A calm, legible palette for the category donut - distinct hues, none of them the
     *  app's own success/error red-green so a category slice is never confused with an
     *  income/expense signal elsewhere on the same card. */
    private val paletteColors = listOf(
        "#6C5CE7", "#00B894", "#FDCB6E", "#E17055", "#0984E3", "#B2BEC3"
    ).map { Color.parseColor(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewModel.balance.collect { balance ->
                binding.homeBalanceText.text = CurrencyFormatter.format(balance)
            }
        }
        lifecycleScope.launch {
            viewModel.monthlyIncome.collect { binding.homeIncomeText.text = CurrencyFormatter.format(it) }
        }
        lifecycleScope.launch {
            viewModel.monthlyExpense.collect { binding.homeExpenseText.text = CurrencyFormatter.format(it) }
        }
        lifecycleScope.launch {
            viewModel.categoryBreakdown.collect { renderCategoryChart(it) }
        }
        lifecycleScope.launch {
            viewModel.budgetStatus.collect { binding.homeBudgetStatusText.text = it }
        }
        lifecycleScope.launch {
            viewModel.thirtyDayForecast.collect { forecast ->
                binding.homeForecastText.text = if (forecast == null) {
                    "داده کافی نیست"
                } else {
                    "برآورد ۳۰ روز: ${CurrencyFormatter.format(forecast)}"
                }
            }
        }

        binding.homeBudgetCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_budgetFragment)
        }
        binding.homeForecastCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_forecastFragment)
        }
        binding.homeAssistantCard.setOnClickListener {
            findNavController().navigate(R.id.assistantFragment)
        }
        binding.homeRemindersCard.setOnClickListener {
            findNavController().navigate(R.id.remindersFragment)
        }
        binding.homeStatusCard.setOnClickListener {
            findNavController().navigate(R.id.financialStatusFragment)
        }
    }

    /** Renders the top 5 categories as donut slices and folds everything past that into
     *  a single "سایر" slice, so a person with a dozen scattered categories still gets a
     *  readable chart instead of a wheel of slivers. The legend is built by hand (rather
     *  than relying on MPAndroidChart's own legend) so each row can show the exact
     *  amount and percentage in Persian digits/RTL order next to its color dot. */
    private fun renderCategoryChart(breakdown: List<Pair<String, Double>>) {
        if (breakdown.isEmpty()) {
            binding.homeCategoryContent.visibility = View.GONE
            binding.homeCategoryEmptyText.visibility = View.VISIBLE
            return
        }
        binding.homeCategoryContent.visibility = View.VISIBLE
        binding.homeCategoryEmptyText.visibility = View.GONE

        val top = breakdown.take(5)
        val otherTotal = breakdown.drop(5).sumOf { it.second }
        val slices = if (otherTotal > 0.0) top + ("سایر" to otherTotal) else top
        val total = slices.sumOf { it.second }

        val entries = slices.map { (category, amount) -> PieEntry(amount.toFloat(), category) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = paletteColors
            setDrawValues(false)
        }
        binding.homeCategoryPieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setUsePercentValues(false)
            holeRadius = 55f
            transparentCircleRadius = 0f
            setDrawCenterText(false)
            setTouchEnabled(false)
            invalidate()
        }

        binding.homeCategoryLegendContainer.removeAllViews()
        slices.forEachIndexed { index, (category, amount) ->
            val percent = if (total > 0.0) (amount / total * 100.0).let { "%.0f".format(it) } else "0"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val topMargin = if (index == 0) 0 else (6 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, 0) }
            }
            val dot = View(requireContext()).apply {
                val size = (10 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(paletteColors[index % paletteColors.size])
                }
            }
            val label = TextView(requireContext()).apply {
                text = "$category ($percent٪)"
                textSize = 11f
                setPadding((6 * resources.displayMetrics.density).toInt(), 0, 0, 0)
            }
            row.addView(dot)
            row.addView(label)
            binding.homeCategoryLegendContainer.addView(row)
        }
    }
}
