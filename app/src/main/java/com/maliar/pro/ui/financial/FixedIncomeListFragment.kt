package com.maliar.pro.ui.financial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.FixedIncome
import com.maliar.pro.database.IncomeType
import com.maliar.pro.databinding.FragmentFinancialEntryListBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.FixedIncomeListViewModel
import com.maliar.pro.viewmodels.FixedIncomeListViewModelFactory
import kotlinx.coroutines.launch

class FixedIncomeListFragment : Fragment() {

    private lateinit var binding: FragmentFinancialEntryListBinding
    private lateinit var adapter: FinancialEntryAdapter
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: FixedIncomeListViewModel by viewModels { FixedIncomeListViewModelFactory(financialManager) }

    private val typeLabels = mapOf(
        IncomeType.SALARY to "حقوق",
        IncomeType.SECOND_JOB to "شغل دوم",
        IncomeType.RENT to "اجاره",
        IncomeType.INVESTMENT_RETURN to "سود سرمایه‌گذاری",
        IncomeType.OTHER to "سایر"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.totalLabelText.text = "کل درآمدهای ثابت"
        binding.emptyStateIcon.text = "💵"
        binding.emptyStateTitle.text = "هنوز درآمد ثابتی ثبت نشده"
        binding.emptyStateSubtitle.text = "با دکمه + یک درآمد ثابت اضافه کنید"

        adapter = FinancialEntryAdapter(
            onItemClick = { /* Reserved for future edit screen */ },
            onDeleteClick = { item ->
                val income = viewModel.incomes.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                confirmDelete(income)
            }
        )
        binding.entryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.entryRecyclerView.adapter = adapter

        binding.addEntryFab.setOnClickListener { showAddIncomeDialog() }

        val successColor = requireContext().getColor(R.color.success)

        lifecycleScope.launch {
            viewModel.incomes.collect { incomes ->
                val items = incomes.map { income ->
                    FinancialEntryItem(
                        id = income.id,
                        title = income.title,
                        subtitle = typeLabels[income.type] ?: "",
                        amountText = CurrencyFormatter.format(income.amount),
                        amountColor = successColor
                    )
                }
                adapter.submitList(items)
                binding.emptyStateLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.totalIncome.collect { total ->
                binding.totalAmountText.text = CurrencyFormatter.format(total)
            }
        }
    }

    private fun confirmDelete(income: FixedIncome) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف")
            .setMessage("آیا از حذف \"${income.title}\" مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteIncome(income) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddIncomeDialog() {
        val types = IncomeType.values()
        val typeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                types.map { typeLabels[it] ?: it.name }
            )
        }
        val nameInput = EditText(requireContext()).apply { hint = "نام درآمد ثابت" }
        val amountInput = EditText(requireContext()).apply {
            hint = "مبلغ (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(typeSpinner)
            addView(nameInput)
            addView(amountInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن درآمد ثابت")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    val type = types[typeSpinner.selectedItemPosition]
                    viewModel.addIncome(type, name, amount)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
