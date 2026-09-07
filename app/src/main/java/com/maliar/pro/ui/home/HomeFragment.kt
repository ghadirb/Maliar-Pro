package com.maliar.pro.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
import com.maliar.pro.database.AssetType
import com.maliar.pro.database.Expense
import com.maliar.pro.database.ExpenseCategory
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.Income
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.databinding.FragmentHomeBinding
import com.maliar.pro.dialogs.AddExpenseDialog
import com.maliar.pro.dialogs.AddIncomeDialog
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

/**
 * "خانه" - a single-glance financial dashboard (redesign spec: codex/ui-home-redesign-v1).
 *
 * Deliberately introduces ZERO new financial logic. Every figure on this screen is read
 * from a flow [AccountingViewModel] (or [SmartReminderManager] for the one reminder
 * lookup) already exposes elsewhere in the app - the balance/income/expense figures back
 * the accounting dashboard, categoryBreakdown/attentionItems/goalList/assetList are
 * either pre-existing or thin, purely-additive flows added alongside this screen without
 * touching how any of them are calculated. Nothing here can disagree with the screens
 * that originate these numbers, because there is only one source for each of them.
 *
 * Sections not backed by real project data are simply never rendered (no "۰٪ پیشرفت"
 * placeholder Goal card when there are no goals, no fabricated "حساب‌های من" list when
 * there's only one account, etc.) per the spec's explicit "no fake capability" rule.
 */
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val accountingManager by lazy { AccountingManager(requireContext()) }
    private val financialStatusManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(accountingManager, financialStatusManager)
    }

    private val paletteColors = listOf(
        "#6C5CE7", "#00B894", "#FDCB6E", "#E17055", "#0984E3", "#B2BEC3"
    ).map { Color.parseColor(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        renderHeader()
        setupClickListeners()

        lifecycleScope.launch { viewModel.balance.collect { binding.homeBalanceText.text = CurrencyFormatter.format(it) } }

        // Hero card income/expense + qualitative month status + "این ماه چطور گذشت؟" bars,
        // all driven off the same two numbers so they can never disagree with each other.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(viewModel.monthlyIncome, viewModel.monthlyExpense) { income, expense ->
                income to expense
            }.collect { (income, expense) -> renderMonthSummary(income, expense) }
        }

        lifecycleScope.launch { viewModel.categoryBreakdown.collect { renderCategoryChart(it) } }
        lifecycleScope.launch { viewModel.attentionItems.collect { renderAttention(it) } }
        lifecycleScope.launch { viewModel.goalList.collect { renderGoals(it) } }
        lifecycleScope.launch { viewModel.assetList.collect { renderAccounts(it) } }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(viewModel.incomeList, viewModel.expenseList) { incomes, expenses ->
                incomes to expenses
            }.collect { (incomes, expenses) -> renderRecentTransactions(incomes, expenses) }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.budgetStatus, viewModel.expenseAnalysis, viewModel.forecastAlert
            ) { budget, analysis, forecastAlert ->
                Triple(budget, analysis, forecastAlert)
            }.collect { (budget, analysis, forecastAlert) -> renderAssistantInsight(budget, analysis, forecastAlert) }
        }
    }

    private fun renderHeader() {
        binding.homeGreetingText.text = "سلام 👋"
        val (y, m, d) = PersianCalendarHelper.getCurrentJalaliDate()
        binding.homeDateText.text = "امروز، ${PersianCalendarHelper.formatJalali(y, m, d)}"
    }

    private fun setupClickListeners() {
        binding.homeNotificationButton.setOnClickListener { findNavController().navigate(R.id.remindersFragment) }
        binding.homeProfileButton.setOnClickListener { findNavController().navigate(R.id.profileFragment) }

        binding.homeQuickAddExpense.setOnClickListener { AddExpenseDialog(requireContext(), viewModel).show() }
        binding.homeQuickAddIncome.setOnClickListener { AddIncomeDialog(requireContext(), viewModel).show() }
        binding.homeQuickReport.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_financialReportsFragment)
        }
        binding.homeSeeAllTransactions.setOnClickListener { findNavController().navigate(R.id.accountingFragment) }

        binding.homeAssistantCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_financialReportsFragment)
        }
        binding.homeGoalsCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_goalListFragment)
        }
        binding.homeBudgetCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_budgetFragment)
        }
        binding.homeForecastCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_forecastFragment)
        }
        binding.homeSupportCard.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_supportFragment)
        }
    }

    /** Hero card's income/expense figures + a plain-language status derived only from
     *  their relationship (never a fabricated "vs. last month" percentage - the app
     *  doesn't have a clean previous-period total readily available, so per the spec's
     *  "no fake data" rule this stays qualitative), plus the "این ماه چطور گذشت؟" bar
     *  pair scaled to whichever of the two figures is larger. */
    private fun renderMonthSummary(income: Double, expense: Double) {
        binding.homeIncomeText.text = CurrencyFormatter.format(income)
        binding.homeExpenseText.text = CurrencyFormatter.format(expense)

        if (income > 0.0 || expense > 0.0) {
            binding.homeMonthStatusText.visibility = View.VISIBLE
            binding.homeMonthStatusText.text = when {
                expense <= income * 0.7 -> "وضعیت این ماه: عالی ✨"
                expense <= income -> "وضعیت این ماه: خوب 👍"
                else -> "وضعیت این ماه: نیاز به توجه ⚠️"
            }
        } else {
            binding.homeMonthStatusText.visibility = View.GONE
        }

        val maxValue = maxOf(income, expense, 1.0)
        val totalBarWidth = (resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density)).toInt()
        binding.homeMonthIncomeBar.layoutParams = binding.homeMonthIncomeBar.layoutParams.apply {
            width = (totalBarWidth * (income / maxValue)).toInt().coerceAtLeast(4)
        }
        binding.homeMonthExpenseBar.layoutParams = binding.homeMonthExpenseBar.layoutParams.apply {
            width = (totalBarWidth * (expense / maxValue)).toInt().coerceAtLeast(4)
        }
        val balance = income - expense
        binding.homeMonthBalanceText.text = "مانده این ماه: ${CurrencyFormatter.format(balance)}"
    }

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
            val percent = if (total > 0.0) "%.0f".format(amount / total * 100.0) else "0"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val topMargin = if (index == 0) 0 else (6 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
            row.addView(dot); row.addView(label)
            binding.homeCategoryLegendContainer.addView(row)
        }
    }

    /** Newest 5 income+expense rows merged and sorted by date - both lists already exist
     *  independently ([AccountingViewModel.incomeList]/[expenseList]); this only merges
     *  and truncates them for display, no new querying. */
    private fun renderRecentTransactions(incomes: List<Income>, expenses: List<Expense>) {
        binding.homeTransactionsContainer.removeAllViews()
        data class Row(val title: String, val date: Long, val amount: Double, val isIncome: Boolean, val icon: String)

        val rows = (incomes.map { Row(it.description.ifBlank { "درآمد" }, it.date, it.amount, true, "💰") } +
            expenses.map { Row(it.description.ifBlank { it.category.ifBlank { "هزینه" } }, it.date, it.amount, false, categoryIcon(it.category)) })
            .sortedByDescending { it.date }
            .take(5)

        if (rows.isEmpty()) {
            binding.homeTransactionsEmptyText.visibility = View.VISIBLE
            return
        }
        binding.homeTransactionsEmptyText.visibility = View.GONE

        rows.forEachIndexed { index, row ->
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(row.date)
            val line = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val topPadding = if (index == 0) (8 * resources.displayMetrics.density).toInt() else (12 * resources.displayMetrics.density).toInt()
                setPadding(0, topPadding, 0, 0)
            }
            val icon = TextView(requireContext()).apply { text = row.icon; textSize = 16f }
            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * resources.displayMetrics.density).toInt()
                }
            }
            info.addView(TextView(requireContext()).apply { text = row.title; textSize = 13f })
            info.addView(TextView(requireContext()).apply {
                text = PersianCalendarHelper.formatJalali(y, m, d)
                textSize = 11f
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
            val amount = TextView(requireContext()).apply {
                text = (if (row.isIncome) "+" else "-") + CurrencyFormatter.format(row.amount)
                textSize = 13f
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), if (row.isIncome) R.color.success else R.color.error)
                )
            }
            line.addView(icon); line.addView(info); line.addView(amount)
            binding.homeTransactionsContainer.addView(line)
        }
    }

    private fun categoryIcon(category: String): String = when (category.trim()) {
        ExpenseCategory.FOOD -> "🍔"
        ExpenseCategory.TRANSPORT -> "🚌"
        ExpenseCategory.CAR -> "🚗"
        ExpenseCategory.HOUSING -> "🏠"
        ExpenseCategory.BILLS -> "🧾"
        ExpenseCategory.ENTERTAINMENT -> "🎬"
        ExpenseCategory.HEALTH -> "💊"
        ExpenseCategory.SHOPPING -> "🛍️"
        ExpenseCategory.CLOTHING -> "👕"
        else -> "📦"
    }

    /** "نیازمند توجه": merges [AccountingViewModel.attentionItems] (installments/debts/
     *  checks due soon) with the single nearest active reminder, fetched directly from
     *  [SmartReminderManager] (a separate manager/domain, deliberately not threaded
     *  through AccountingViewModel) exactly the way RemindersFragment itself already
     *  reads it. The whole card is hidden - not just emptied - when there is truly
     *  nothing to flag. */
    private fun renderAttention(items: List<AccountingViewModel.AttentionItem>) {
        lifecycleScope.launch {
            val nextReminder = runCatching {
                SmartReminderManager(requireContext()).reconcileRecurringReminders().firstOrNull()
            }.getOrNull()

            binding.homeAttentionContainer.removeAllViews()
            var shown = 0

            if (nextReminder != null) {
                val days = (nextReminder.triggerTime - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)
                addAttentionRow("⏰", nextReminder.title, dueLabel(days), null) {
                    findNavController().navigate(R.id.remindersFragment)
                }
                shown++
            }
            items.take(4).forEach { item ->
                val (icon, destination) = when (item.type) {
                    AccountingViewModel.AttentionType.INSTALLMENT -> "📅" to R.id.action_homeFragment_to_installmentListFragment
                    AccountingViewModel.AttentionType.DEBT -> "💳" to R.id.action_homeFragment_to_debtListFragment
                    AccountingViewModel.AttentionType.CHECK -> "🧾" to R.id.action_homeFragment_to_checkListFragment
                }
                addAttentionRow(icon, item.title, dueLabel(item.dueInDays), CurrencyFormatter.format(item.amount)) {
                    findNavController().navigate(destination)
                }
                shown++
            }

            binding.homeAttentionCard.visibility = if (shown > 0) View.VISIBLE else View.GONE
        }
    }

    private fun dueLabel(days: Long): String = when {
        days < 0 -> "سررسید گذشته"
        days == 0L -> "امروز"
        days == 1L -> "فردا"
        else -> "$days روز دیگر"
    }

    private fun addAttentionRow(icon: String, title: String, subtitle: String, amount: String?, onClick: () -> Unit) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
            setOnClickListener { onClick() }
        }
        row.addView(TextView(requireContext()).apply { text = icon; textSize = 16f })
        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * resources.displayMetrics.density).toInt()
            }
        }
        info.addView(TextView(requireContext()).apply { text = title; textSize = 13f })
        info.addView(TextView(requireContext()).apply {
            text = subtitle
            textSize = 11f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.warning))
        })
        row.addView(info)
        if (amount != null) {
            row.addView(TextView(requireContext()).apply { text = amount; textSize = 12f })
        }
        binding.homeAttentionContainer.addView(row)
    }

    /** Reuses the exact same locally-computed analysis the assistant/budget/forecast
     *  screens already show (no network AI call here, no new text-generation) - picks
     *  whichever of these already-real signals is most relevant, in the same spirit as
     *  [com.maliar.pro.utils.FinancialInsightWorker]'s own priority chain. */
    private fun renderAssistantInsight(
        budgetStatus: String?,
        analysis: AccountingViewModel.ExpenseAnalysis?,
        forecastAlert: String?
    ) {
        val increase = analysis?.biggestIncreaseCategory
        binding.homeAssistantInsightText.text = when {
            !budgetStatus.isNullOrBlank() && budgetStatus.contains("هشدار") -> budgetStatus
            increase != null -> "هزینه ${increase.category} شما نسبت به دوره قبل حدود ${increase.changePercent.let { "%.0f".format(it) }}٪ افزایش داشته است."
            !forecastAlert.isNullOrBlank() -> forecastAlert
            !budgetStatus.isNullOrBlank() && !budgetStatus.contains("داده کافی نیست") -> budgetStatus
            else -> "برای ارائه تحلیل دقیق‌تر، تراکنش‌های بیشتری ثبت کنید."
        }
    }

    private fun renderGoals(goals: List<com.maliar.pro.database.FinancialGoal>) {
        binding.homeGoalsContainer.removeAllViews()
        if (goals.isEmpty()) {
            binding.homeGoalsCard.visibility = View.GONE
            return
        }
        binding.homeGoalsCard.visibility = View.VISIBLE
        goals.take(3).forEachIndexed { index, goal ->
            val percent = if (goal.targetAmount > 0) (goal.currentProgress / goal.targetAmount * 100.0).coerceIn(0.0, 100.0) else 0.0
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val topMargin = if (index == 0) 0 else (14 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, 0) }
            }
            container.addView(TextView(requireContext()).apply { text = goal.title; textSize = 13f })
            val track = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (8 * resources.displayMetrics.density).toInt()
                ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background))
            }
            track.addView(View(requireContext()).apply {
                layoutParams = FrameLayoutParamsForPercent(percent)
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary))
            })
            container.addView(track)
            container.addView(TextView(requireContext()).apply {
                text = "${"%.0f".format(percent)}٪ - ${CurrencyFormatter.format(goal.currentProgress)} از ${CurrencyFormatter.format(goal.targetAmount)}"
                textSize = 11f
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
                setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            binding.homeGoalsContainer.addView(container)
        }
    }

    private fun FrameLayoutParamsForPercent(percent: Double): android.widget.FrameLayout.LayoutParams {
        val totalWidth = (resources.displayMetrics.widthPixels - (64 * resources.displayMetrics.density)).toInt()
        return android.widget.FrameLayout.LayoutParams(
            (totalWidth * (percent / 100.0)).toInt().coerceAtLeast(2),
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    /** Only shown when there is more than one liquid account (spec: "اگر کاربر چند
     *  حساب دارد") - a single account is already fully represented by the hero card's
     *  balance, so listing it again here would be redundant, not informative. */
    private fun renderAccounts(assets: List<com.maliar.pro.database.Asset>) {
        val accounts = assets.filter { it.type == AssetType.CASH || it.type == AssetType.BANK_ACCOUNT || it.type == AssetType.DEPOSIT }
        binding.homeAccountsContainer.removeAllViews()
        if (accounts.size < 2) {
            binding.homeAccountsCard.visibility = View.GONE
            return
        }
        binding.homeAccountsCard.visibility = View.VISIBLE
        accounts.forEachIndexed { index, asset ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val topMargin = if (index == 0) 0 else (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, topMargin, 0, 0) }
            }
            row.addView(TextView(requireContext()).apply {
                text = asset.title
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = CurrencyFormatter.format(asset.value)
                textSize = 13f
            })
            binding.homeAccountsContainer.addView(row)
        }
    }
}
