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
import kotlinx.coroutines.withContext

/** "گزارش‌های مالی حرفه‌ای": period-scoped income/expense totals, biggest individual
 *  expenses/incomes, the category that ate the most money, and an income-vs-expense trend
 *  chart - everything computed by [com.maliar.pro.database.FinancialReportManager]. */
class FinancialReportsFragment : Fragment() {

    private lateinit var binding: FragmentFinancialReportsBinding
    private lateinit var topExpensesAdapter: ReportRowAdapter
    private lateinit var topIncomesAdapter: ReportRowAdapter

    private val csvExportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { exportReport(it, isPdf = false) } }

    private val pdfExportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { exportReport(it, isPdf = true) } }

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

        binding.exportCsvButton.setOnClickListener {
            val period = viewModel.selectedPeriod.value.name.lowercase()
            val offset = viewModel.periodOffset.value
            val suffix = if (offset > 0) "-${offset}back" else ""
            csvExportLauncher.launch("maliar-pro-report-$period$suffix.csv")
        }
        binding.exportPdfButton.setOnClickListener {
            val period = viewModel.selectedPeriod.value.name.lowercase()
            val offset = viewModel.periodOffset.value
            val suffix = if (offset > 0) "-${offset}back" else ""
            pdfExportLauncher.launch("maliar-pro-report-$period$suffix.pdf")
        }

        binding.periodPrevButton.setOnClickListener { viewModel.goToPreviousPeriod() }
        binding.periodNextButton.setOnClickListener { viewModel.goToNextPeriod() }

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
            viewModel.rangeLabel.collect { binding.periodRangeLabel.text = it }
        }
        lifecycleScope.launch {
            viewModel.periodOffset.collect { offset ->
                binding.periodNextButton.isEnabled = offset > 0
            }
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

    private fun exportReport(uri: android.net.Uri, isPdf: Boolean) {
        val report = viewModel.report.value
        if (report == null) {
            android.widget.Toast.makeText(requireContext(), "گزارشی برای خروجی گرفتن وجود ندارد", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val periodLabel = when (viewModel.selectedPeriod.value) {
            ReportPeriod.DAILY -> "روزانه"
            ReportPeriod.WEEKLY -> "هفتگی"
            ReportPeriod.MONTHLY -> "ماهانه"
            ReportPeriod.YEARLY -> "سالانه"
        } + " (${viewModel.rangeLabel.value})"
        lifecycleScope.launch {
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (isPdf) {
                        com.maliar.pro.utils.ReportExporter.exportPdf(requireContext(), uri, report, periodLabel)
                    } else {
                        com.maliar.pro.utils.ReportExporter.exportCsv(requireContext(), uri, report)
                    }
                }
                android.widget.Toast.makeText(requireContext(), "گزارش با موفقیت ذخیره شد", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "خطا در ذخیره گزارش: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Builds one row per category that both has spending this period and a known
     *  approximate benchmark (see BenchmarkData for why categories without a match are
     *  skipped rather than guessed at). Simple plain TextViews, not a RecyclerView - at
     *  most a handful of rows, not worth the extra adapter machinery. */
    private fun renderBenchmarkSection(report: FinancialReport) {
        val rows = report.categoryBreakdown.mapNotNull { categoryTotal ->
            val benchmarkPercent = com.maliar.pro.utils.BenchmarkData.approxPercentFor(categoryTotal.category) ?: return@mapNotNull null
            if (report.totalExpense <= 0) return@mapNotNull null
            val userPercent = (categoryTotal.total / report.totalExpense) * 100
            Triple(categoryTotal.category, userPercent, benchmarkPercent)
        }

        binding.benchmarkSection.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        binding.benchmarkRowsContainer.removeAllViews()

        rows.forEach { (category, userPercent, benchmarkPercent) ->
            val diff = userPercent - benchmarkPercent
            val (indicator, color) = when {
                diff > 5 -> "⬆️ بیشتر از میانگین" to "#F44336"
                diff < -5 -> "⬇️ کمتر از میانگین" to "#4CAF50"
                else -> "≈ نزدیک به میانگین" to "#757575"
            }
            val row = android.widget.TextView(requireContext()).apply {
                text = String.format("%s: %.0f%% شما - %.0f%% میانگین (%s)", category, userPercent, benchmarkPercent, indicator)
                textSize = 12f
                setTextColor(Color.parseColor(color))
                setPadding(0, 6, 0, 6)
            }
            binding.benchmarkRowsContainer.addView(row)
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

        renderBenchmarkSection(report)

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
