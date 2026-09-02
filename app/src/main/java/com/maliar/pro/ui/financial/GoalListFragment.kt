package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maliar.pro.R
import com.maliar.pro.adapters.FinancialEntryAdapter
import com.maliar.pro.adapters.FinancialEntryItem
import com.maliar.pro.database.FinancialGoal
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.GoalType
import com.maliar.pro.database.Priority
import com.maliar.pro.databinding.FragmentFinancialEntryListBinding
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.GoalListViewModel
import com.maliar.pro.viewmodels.GoalListViewModelFactory
import kotlinx.coroutines.launch

class GoalListFragment : Fragment() {

    private lateinit var binding: FragmentFinancialEntryListBinding
    private lateinit var adapter: FinancialEntryAdapter
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: GoalListViewModel by viewModels { GoalListViewModelFactory(financialManager) }

    private val typeLabels = mapOf(
        GoalType.HOUSE to "خرید خانه",
        GoalType.CAR to "خرید خودرو",
        GoalType.TRAVEL to "سفر",
        GoalType.INVESTMENT to "سرمایه‌گذاری",
        GoalType.EMERGENCY_FUND to "صندوق اضطراری",
        GoalType.RETIREMENT to "بازنشستگی",
        GoalType.CUSTOM to "سایر"
    )

    private var selectedTargetDateMillis: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.totalCard.visibility = View.GONE
        binding.emptyStateIcon.text = "🎯"
        binding.emptyStateTitle.text = "هنوز هدفی ثبت نشده"
        binding.emptyStateSubtitle.text = "با دکمه + یک هدف مالی اضافه کنید"

        adapter = FinancialEntryAdapter(
            onItemClick = { /* Reserved for future edit screen */ },
            onDeleteClick = { item ->
                val goal = viewModel.goals.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                confirmDelete(goal)
            }
        )
        binding.entryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.entryRecyclerView.adapter = adapter

        binding.addEntryFab.setOnClickListener { showAddGoalDialog() }

        val primaryColor = requireContext().getColor(R.color.primary)
        val successColor = requireContext().getColor(R.color.success)

        lifecycleScope.launch {
            viewModel.goals.collect { goals ->
                val items = goals.map { goal ->
                    val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(goal.targetDate)
                    FinancialEntryItem(
                        id = goal.id,
                        title = goal.title,
                        subtitle = "${typeLabels[goal.type] ?: ""} · تا ${PersianCalendarHelper.formatJalali(y, m, d)}",
                        amountText = CurrencyFormatter.format(goal.targetAmount),
                        amountColor = primaryColor,
                        statusText = if (goal.isCompleted) "تکمیل شده" else null,
                        statusColor = if (goal.isCompleted) successColor else null
                    )
                }
                adapter.submitList(items)
                binding.emptyStateLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmDelete(goal: FinancialGoal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف")
            .setMessage("آیا از حذف \"${goal.title}\" مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteGoal(goal) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddGoalDialog() {
        val types = GoalType.values()
        val typeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                types.map { typeLabels[it] ?: it.name }
            )
        }
        val nameInput = EditText(requireContext()).apply { hint = "نام هدف" }
        val amountInput = EditText(requireContext()).apply {
            hint = "مبلغ هدف (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val defaultDateMillis = System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L
        selectedTargetDateMillis = defaultDateMillis
        val (dy, dm, dd) = PersianCalendarHelper.gregorianMillisToJalali(defaultDateMillis)
        val dateButton = Button(requireContext()).apply {
            text = PersianCalendarHelper.formatJalali(dy, dm, dd)
        }
        dateButton.setOnClickListener {
            val today = PersianCalendarHelper.getCurrentJalaliDate()
            PersianDatePickerDialog(
                requireContext(),
                initialYear = today.first,
                initialMonth = today.second,
                initialDay = today.third
            ) { year, month, day ->
                selectedTargetDateMillis = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
                dateButton.text = PersianCalendarHelper.formatJalali(year, month, day)
            }.show()
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(typeSpinner)
            addView(nameInput)
            addView(amountInput)
            addView(dateButton)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن هدف مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    val type = types[typeSpinner.selectedItemPosition]
                    viewModel.addGoal(type, name, amount, selectedTargetDateMillis, Priority.MEDIUM)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
