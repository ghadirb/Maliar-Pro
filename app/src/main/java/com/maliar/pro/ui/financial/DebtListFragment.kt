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
import com.maliar.pro.database.Debt
import com.maliar.pro.database.DebtType
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentFinancialEntryListBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.DebtListViewModel
import com.maliar.pro.viewmodels.DebtListViewModelFactory
import kotlinx.coroutines.launch

class DebtListFragment : Fragment() {

    private lateinit var binding: FragmentFinancialEntryListBinding
    private lateinit var adapter: FinancialEntryAdapter
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: DebtListViewModel by viewModels { DebtListViewModelFactory(financialManager) }

    private val typeLabels = mapOf(
        DebtType.LOAN to "وام",
        DebtType.PERSONAL to "بدهی شخصی",
        DebtType.CHECK to "چک",
        DebtType.CREDIT_CARD to "کارت اعتباری",
        DebtType.OTHER to "سایر"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.totalLabelText.text = "کل بدهی‌های باز"
        binding.emptyStateIcon.text = "💳"
        binding.emptyStateTitle.text = "هنوز بدهی ثبت نشده"
        binding.emptyStateSubtitle.text = "با دکمه + یک بدهی اضافه کنید"

        adapter = FinancialEntryAdapter(
            onItemClick = { item ->
                val debt = viewModel.debts.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                viewModel.toggleDebtPaid(debt)
            },
            onDeleteClick = { item ->
                val debt = viewModel.debts.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                confirmDelete(debt)
            }
        )
        binding.entryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.entryRecyclerView.adapter = adapter

        binding.addEntryFab.setOnClickListener { showAddDebtDialog() }

        val errorColor = requireContext().getColor(R.color.error)
        val successColor = requireContext().getColor(R.color.success)
        val warningColor = requireContext().getColor(R.color.warning)

        lifecycleScope.launch {
            viewModel.debts.collect { debts ->
                val items = debts.map { debt ->
                    FinancialEntryItem(
                        id = debt.id,
                        title = debt.title,
                        subtitle = typeLabels[debt.type] ?: "",
                        amountText = CurrencyFormatter.format(debt.amount),
                        amountColor = errorColor,
                        statusText = if (debt.isPaid) "پرداخت شده" else "باز (لمس کنید)",
                        statusColor = if (debt.isPaid) successColor else warningColor
                    )
                }
                adapter.submitList(items)
                binding.emptyStateLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.totalUnpaidDebts.collect { total ->
                binding.totalAmountText.text = CurrencyFormatter.format(total)
            }
        }
    }

    private fun confirmDelete(debt: Debt) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف")
            .setMessage("آیا از حذف \"${debt.title}\" مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteDebt(debt) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddDebtDialog() {
        val types = DebtType.values()
        val typeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                types.map { typeLabels[it] ?: it.name }
            )
        }
        val nameInput = EditText(requireContext()).apply { hint = "نام بدهی" }
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
            .setTitle("افزودن بدهی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    val type = types[typeSpinner.selectedItemPosition]
                    viewModel.addDebt(type, name, amount)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
