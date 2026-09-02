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
import com.maliar.pro.adapters.FinancialEntryAdapter
import com.maliar.pro.adapters.FinancialEntryItem
import com.maliar.pro.database.Asset
import com.maliar.pro.database.AssetType
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentFinancialEntryListBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.AssetListViewModel
import com.maliar.pro.viewmodels.AssetListViewModelFactory
import kotlinx.coroutines.launch

class AssetListFragment : Fragment() {

    private lateinit var binding: FragmentFinancialEntryListBinding
    private lateinit var adapter: FinancialEntryAdapter
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: AssetListViewModel by viewModels { AssetListViewModelFactory(financialManager) }

    private val typeLabels = mapOf(
        AssetType.CASH to "نقد",
        AssetType.BANK_ACCOUNT to "حساب بانکی",
        AssetType.DEPOSIT to "سپرده",
        AssetType.GOLD to "طلا",
        AssetType.CRYPTO to "ارز دیجیتال",
        AssetType.STOCK to "سهام",
        AssetType.VEHICLE to "خودرو",
        AssetType.REAL_ESTATE to "ملک",
        AssetType.OTHER to "سایر"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.totalLabelText.text = "کل دارایی‌ها"
        binding.emptyStateIcon.text = "💰"
        binding.emptyStateTitle.text = "هنوز دارایی ثبت نشده"
        binding.emptyStateSubtitle.text = "با دکمه + یک دارایی اضافه کنید"

        adapter = FinancialEntryAdapter(
            onItemClick = { /* Reserved for future edit screen */ },
            onDeleteClick = { item ->
                val asset = viewModel.assets.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                confirmDelete(asset)
            }
        )
        binding.entryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.entryRecyclerView.adapter = adapter

        binding.addEntryFab.setOnClickListener { showAddAssetDialog() }

        lifecycleScope.launch {
            viewModel.assets.collect { assets ->
                val items = assets.map { asset ->
                    FinancialEntryItem(
                        id = asset.id,
                        title = asset.title,
                        subtitle = typeLabels[asset.type] ?: "",
                        amountText = CurrencyFormatter.format(asset.value),
                        amountColor = requireContext().getColor(com.maliar.pro.R.color.success)
                    )
                }
                adapter.submitList(items)
                binding.emptyStateLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.totalAssets.collect { total ->
                binding.totalAmountText.text = CurrencyFormatter.format(total)
            }
        }
    }

    private fun confirmDelete(asset: Asset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف")
            .setMessage("آیا از حذف \"${asset.title}\" مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteAsset(asset) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAddAssetDialog() {
        val types = AssetType.values()
        val typeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                types.map { typeLabels[it] ?: it.name }
            )
        }
        val nameInput = EditText(requireContext()).apply { hint = "نام دارایی" }
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
            .setTitle("افزودن دارایی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    val type = types[typeSpinner.selectedItemPosition]
                    viewModel.addAsset(type, name, amount)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
