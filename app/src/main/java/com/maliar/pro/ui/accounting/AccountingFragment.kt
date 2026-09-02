package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.maliar.pro.R
import com.maliar.pro.databinding.FragmentAccountingBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.dialogs.AddIncomeDialog
import com.maliar.pro.dialogs.AddExpenseDialog
import com.maliar.pro.dialogs.AddCheckDialog
import com.maliar.pro.dialogs.AddInstallmentDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import com.maliar.pro.viewmodels.DueSoonViewModel
import com.maliar.pro.viewmodels.DueSoonViewModelFactory
import kotlinx.coroutines.launch

class AccountingFragment : Fragment() {

    private lateinit var binding: FragmentAccountingBinding
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(
            AccountingManager(requireContext()),
            com.maliar.pro.database.FinancialStatusManager(requireContext())
        )
    }
    private val dueSoonViewModel: DueSoonViewModel by viewModels {
        DueSoonViewModelFactory(
            AccountingManager(requireContext()),
            FinancialStatusManager(requireContext()),
            DebtorManager(requireContext())
        )
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccountingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.incomeCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_incomeListFragment)
        }

        binding.expenseCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_expenseListFragment)
        }

        binding.checksCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_checkListFragment)
        }

        binding.installmentsCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_installmentListFragment)
        }

        binding.addIncomeButton.setOnClickListener {
            showAddIncomeDialog()
        }

        binding.addExpenseButton.setOnClickListener {
            showAddExpenseDialog()
        }

        binding.addCheckButton.setOnClickListener {
            showAddCheckDialog()
        }

        binding.addInstallmentButton.setOnClickListener {
            showAddInstallmentDialog()
        }

        binding.dueSoonCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_dueSoonFragment)
        }

        binding.debtorsCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_debtorListFragment)
        }

        binding.financialCalendarCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_financialCalendarFragment)
        }

        binding.financialReportsCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_financialReportsFragment)
        }

        binding.carsCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_carListFragment)
        }

        binding.mealPlanCard.setOnClickListener {
            findNavController().navigate(R.id.action_accountingFragment_to_mealPlanFragment)
        }
    }

    private fun observeViewModel() {
        // Card totals: all-time totals, reactive to Room - updates immediately regardless
        // of whether the write came from this screen's own dialogs or from somewhere else
        // entirely (e.g. the assistant tab actually saving an income/expense).
        lifecycleScope.launch {
            viewModel.totalIncome.collect { income ->
                binding.incomeAmount.text = formatCurrency(income)
            }
        }
        lifecycleScope.launch {
            viewModel.totalExpense.collect { expense ->
                binding.expenseAmount.text = formatCurrency(expense)
            }
        }
        lifecycleScope.launch {
            viewModel.balance.collect { balance ->
                binding.balanceAmount.text = formatCurrency(balance)
                // Same red highlight as the period balance below - a plain white "-" on
                // this dark green card is very easy to miss at a glance, so make a
                // negative total balance visually unmistakable too.
                binding.balanceAmount.setTextColor(
                    if (balance < 0) android.graphics.Color.parseColor("#FFCDD2")
                    else android.graphics.Color.WHITE
                )
            }
        }
        lifecycleScope.launch {
            viewModel.checkList.collect { list ->
                binding.checksCount.text = "${list.size} چک"
            }
        }
        lifecycleScope.launch {
            viewModel.installmentList.collect { list ->
                binding.installmentsCount.text = "${list.size} قسط"
            }
        }

        // Professional monthly dashboard header
        lifecycleScope.launch {
            viewModel.monthlyIncome.collect { income ->
                binding.monthlyIncomeAmount.text = formatCurrency(income)
            }
        }
        lifecycleScope.launch {
            viewModel.monthlyExpense.collect { expense ->
                binding.monthlyExpenseAmount.text = formatCurrency(expense)
            }
        }
        lifecycleScope.launch {
            // "تراز همین دوره" answers exactly the question people keep asking: the big
            // "تراز کل" number above is the all-time total (by design - it never changes
            // just because you change the period), so this row shows the period-scoped
            // net (period income − period expense) right next to it, colored red when
            // negative so it's unmistakable.
            viewModel.monthlyBalance.collect { balance ->
                binding.periodBalanceAmount.text = formatCurrency(balance)
                binding.periodBalanceAmount.setTextColor(
                    if (balance < 0) android.graphics.Color.parseColor("#FFCDD2")
                    else android.graphics.Color.WHITE
                )
            }
        }
        lifecycleScope.launch {
            viewModel.uncashedChecksCount.collect { count ->
                val total = viewModel.uncashedChecksTotal.value
                binding.monthlyChecksSummary.text = "$count چک · ${formatCurrency(total)}"
            }
        }
        lifecycleScope.launch {
            viewModel.uncashedChecksTotal.collect { total ->
                val count = viewModel.uncashedChecksCount.value
                binding.monthlyChecksSummary.text = "$count چک · ${formatCurrency(total)}"
            }
        }
        lifecycleScope.launch {
            viewModel.activeInstallmentsCount.collect { count ->
                val total = viewModel.activeInstallmentsMonthlyTotal.value
                binding.monthlyInstallmentsSummary.text = "$count قسط · ${formatCurrency(total)}"
            }
        }
        lifecycleScope.launch {
            viewModel.activeInstallmentsMonthlyTotal.collect { total ->
                val count = viewModel.activeInstallmentsCount.value
                binding.monthlyInstallmentsSummary.text = "$count قسط · ${formatCurrency(total)}"
            }
        }

        lifecycleScope.launch {
            viewModel.todayExpense.collect { binding.todayExpenseAmount.text = formatCurrency(it) }
        }
        lifecycleScope.launch {
            viewModel.weekExpense.collect { binding.weekExpenseAmount.text = formatCurrency(it) }
        }
        lifecycleScope.launch {
            viewModel.currentPeriodSavings.collect { binding.todaySavingsAmount.text = formatCurrency(it) }
        }
        lifecycleScope.launch {
            viewModel.financialHealthScore.collect { score ->
                binding.financialHealthText.text = if (score == 0) {
                    "سلامت مالی تقریبی: داده کافی نیست"
                } else {
                    "سلامت مالی تقریبی: $score از ۱۰۰ · فقط یک شاخص راهنما"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.suggestedMonthlyBudget.collect { amount ->
                binding.budgetSuggestionText.text = if (amount <= 0.0) {
                    "بودجه پیشنهادی ماهانه: داده کافی نیست"
                } else {
                    "بودجه پیشنهادی ماهانه: ${formatCurrency(amount)} · قابل ویرایش"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.suggestedSpendableAmount.collect { amount ->
                binding.spendableSuggestionText.text = if (amount == null) {
                    "قابل خرج پیشنهادی: داده کافی نیست"
                } else {
                    "قابل خرج پیشنهادی تا پایان دوره: ${formatCurrency(amount)}"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.activeGoalsSummary.collect { goals ->
                binding.goalsSummaryText.text = if (goals.isEmpty()) {
                    "اهداف فعال: داده‌ای ثبت نشده"
                } else {
                    val progress = goals.map { goal ->
                        if (goal.targetAmount <= 0.0) 0.0
                        else (goal.currentProgress / goal.targetAmount * 100.0).coerceIn(0.0, 100.0)
                    }.average().toInt()
                    "اهداف فعال: ${goals.size} · میانگین پیشرفت $progress٪"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.nearestGoalSavingsSuggestion.collect { suggestion ->
                binding.goalSavingsSuggestionText.text = if (suggestion == null) {
                    "پیشنهاد پس‌انداز هدف: داده کافی نیست"
                } else {
                    "برای «${suggestion.first}» ماهانه حدود ${formatCurrency(suggestion.second)} پس‌انداز پیشنهادی است"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.emergencyFundSummary.collect { summary ->
                binding.emergencyFundText.text = if (summary == null) {
                    "صندوق اضطراری: هدفی تنظیم نشده"
                } else {
                    "صندوق اضطراری: ${formatCurrency(summary.current)} از ${formatCurrency(summary.target)} · ${summary.percent}٪"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.sevenDayForecast.collect { amount ->
                binding.sevenDayForecastText.text = if (amount == null) {
                    "برآورد ۷ روز آینده: داده کافی نیست"
                } else {
                    "برآورد موجودی/تراز ۷ روز آینده: ${formatCurrency(amount)} · تخمینی"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.unpaidDebtSummary.collect { summary ->
                binding.unpaidDebtText.text = if (summary.second == 0) {
                    "بدهی پرداخت‌نشده: داده‌ای ثبت نشده"
                } else {
                    "بدهی پرداخت‌نشده: ${summary.second} مورد · ${formatCurrency(summary.first)}"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.expenseAnalysis.collect { analysis ->
                binding.expenseAnalysisText.text = if (analysis == null) {
                    "تحلیل هزینه: داده کافی نیست"
                } else {
                    "بیشترین دسته: ${analysis.topCategory} (${formatCurrency(analysis.topCategoryAmount)}) · میانگین روزانه: ${formatCurrency(analysis.dailyAverage)}"
                }
            }
        }
        lifecycleScope.launch {
            viewModel.budgetStatus.collect { status ->
                binding.budgetStatusText.text = status
                binding.budgetStatusText.setTextColor(
                    if (status.contains("هشدار")) android.graphics.Color.parseColor("#C62828")
                    else resources.getColor(com.maliar.pro.R.color.text_secondary, null)
                )
            }
        }
        lifecycleScope.launch {
            viewModel.expenseTrend.collect { change ->
                binding.expenseTrendText.text = if (change == null) {
                    "روند هزینه: داده کافی نیست"
                } else {
                    val direction = if (change >= 0.0) "افزایش" else "کاهش"
                    "روند هزینه نسبت به دوره قبل: $direction ${kotlin.math.abs(change).toInt()}٪"
                }
            }
        }

        // Due-soon widget: only shown once there's actually something due within a week,
        // so it never occupies space with an empty state on a quiet week.
        lifecycleScope.launch {
            dueSoonViewModel.dueItems.collect {
                val soon = dueSoonViewModel.itemsDueWithin(7)
                if (soon.isEmpty()) {
                    binding.dueSoonCard.visibility = View.GONE
                } else {
                    binding.dueSoonCard.visibility = View.VISIBLE
                    val overdue = soon.count { it.isOverdue }
                    binding.dueSoonSubtitle.text = if (overdue > 0) {
                        "${soon.size} مورد · $overdue مورد سررسید گذشته!"
                    } else {
                        "${soon.size} مورد در ۷ روز آینده"
                    }
                }
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return com.maliar.pro.utils.CurrencyFormatter.format(amount)
    }

    private fun showAddIncomeDialog() {
        AddIncomeDialog(requireContext(), viewModel).show()
    }

    private fun showAddExpenseDialog() {
        AddExpenseDialog(requireContext(), viewModel).show()
    }

    private fun showAddCheckDialog() {
        AddCheckDialog(requireContext(), viewModel).show()
    }

    private fun showAddInstallmentDialog() {
        AddInstallmentDialog(requireContext(), viewModel).show()
    }
}
