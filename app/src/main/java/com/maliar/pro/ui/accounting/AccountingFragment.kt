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
        AccountingViewModelFactory(AccountingManager(requireContext()))
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
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.monthlyIncome.collect { binding.dashboardIncomeText.text = formatCurrency(it) }
        }
        lifecycleScope.launch {
            viewModel.monthlyExpense.collect { binding.dashboardExpenseText.text = formatCurrency(it) }
        }
        lifecycleScope.launch {
            // Streak: consecutive days (including today) with at least one income or expense
            // entry - a light, non-punishing nudge toward keeping the books up to date.
            // Combines both flows so it recomputes whenever either changes, and simply
            // hides itself at 0/1 days rather than showing a discouraging "۰ روز".
            kotlinx.coroutines.flow.combine(viewModel.incomeList, viewModel.expenseList) { incomes, expenses ->
                val entryDays = (incomes.map { it.date } + expenses.map { it.date })
                    .map { com.maliar.pro.utils.PersianCalendarHelper.gregorianMillisToJalali(it) }
                    .map { (y, m, d) -> y * 10000 + m * 100 + d }
                    .toSet()
                var streak = 0
                var cursor = System.currentTimeMillis()
                while (true) {
                    val (y, m, d) = com.maliar.pro.utils.PersianCalendarHelper.gregorianMillisToJalali(cursor)
                    if ((y * 10000 + m * 100 + d) !in entryDays) break
                    streak++
                    cursor -= 24 * 60 * 60 * 1000
                }
                streak
            }.collect { streak ->
                if (streak >= 2) {
                    binding.dashboardStreakBadge.visibility = View.VISIBLE
                    binding.dashboardStreakBadge.text = "🔥 $streak روز متوالی"
                } else {
                    binding.dashboardStreakBadge.visibility = View.GONE
                }
            }
        }
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
                binding.dashboardBalanceText.text = formatCurrency(balance)
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
        return String.format("%,.0f تومان", amount)
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
