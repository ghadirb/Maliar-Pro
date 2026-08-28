package com.maliar.pro.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialReport
import com.maliar.pro.database.ReportPeriod
import com.maliar.pro.databinding.FragmentFinancialReportsBinding
import com.maliar.pro.adapters.ReportRowAdapter
import com.maliar.pro.adapters.ReportRowItem
import com.maliar.pro.viewmodels.FinancialReportsViewModel
import com.maliar.pro.viewmodels.FinancialReportsViewModelFactory
import kotlinx.coroutines.launch

/** "گزارش‌های مالی حرفه‌ای": period-scoped income/expense totals, biggest individual
 *  expenses/incomes, the category that ate the most money, and an income-vs-expense trend
 *  chart - everything computed by [com.maliar.pro.database.FinancialReportManager]. */
class FinancialReportsFragment : Fragment() {

    private lateinit var binding: FragmentFinancialReportsBinding
    private lateinit var topExpensesAdapter: ReportRowAdapter
    private lateinit var topIncomesAdapter: ReportRowAdapter

    private val viewModel: FinancialReportsViewModel by viewModels {
        FinancialReportsViewModelFactory(
            AccountingManager(requireContext()),
            com.maliar.pro.utils.PreferencesManager(requireContext())
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topExpensesAdapter = ReportRowAdapter(amountColor = Color.parseColor("#F44336"))
        topIncomesAdapter = ReportRowAdapter(amountColor = Color.parseColor("#4CAF50"))
        binding.topExpensesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.topExpensesRecyclerView.adapter = topExpensesAdapter
        binding.topIncomesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.topIncomesRecyclerView.adapter = topIncomesAdapter

        setupChart()

        binding.periodChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val period = when (id) {
                R.id.chipDaily -> ReportPeriod.DAILY
                R.id.chipWeekly -> ReportPeriod.WEEKLY
                R.id.chipYearly -> ReportPeriod.YEARLY
                else -> ReportPeriod.MONTHLY
            }
            viewModel.loadReport(period)
        }

        lifecycleScope.launch {
            viewModel.report.collect { report ->
                if (report != null) renderReport(report)
            }
        }
    }

    private fun setupChart() {
        binding.trendChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            setTouchEnabled(true)
            setPinchZoom(true)
        }
    }

    private fun renderReport(report: FinancialReport) {
        binding.totalIncomeText.text = String.format("%,.0f", report.totalIncome)
        binding.totalExpenseText.text = String.format("%,.0f", report.totalExpense)
        binding.netText.text = String.format("%,.0f", report.net)
        binding.netText.setTextColor(
            if (report.net >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )

        if (report.topExpenseCategory != null) {
            binding.topCategoryLabel.visibility = View.VISIBLE
            binding.topCategoryLabel.text = String.format(
                "🏷 بیشترین هزینه در دسته «%s»: %,.0f تومان",
                report.topExpenseCategory.category, report.topExpenseCategory.total
            )
        } else {
            binding.topCategoryLabel.visibility = View.GONE
        }

        topExpensesAdapter.submitList(report.topExpenses.map {
            ReportRowItem(it.description, it.category, it.date, it.amount)
        })
        topIncomesAdapter.submitList(report.topIncomes.map {
            ReportRowItem(it.description, it.category, it.date, it.amount)
        })

        val incomeEntries = report.trend.mapIndexed { i, p -> Entry(i.toFloat(), p.income.toFloat()) }
        val expenseEntries = report.trend.mapIndexed { i, p -> Entry(i.toFloat(), p.expense.toFloat()) }
        val labels = report.trend.map { it.label }

        val incomeSet = LineDataSet(incomeEntries, "درآمد").apply {
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
        }
        val expenseSet = LineDataSet(expenseEntries, "هزینه").apply {
            color = Color.parseColor("#F44336")
            setCircleColor(Color.parseColor("#F44336"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
        }

        binding.trendChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.trendChart.data = LineData(incomeSet, expenseSet)
        binding.trendChart.invalidate()
    }
}
