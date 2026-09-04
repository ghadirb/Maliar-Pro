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
import com.maliar.pro.database.AccountPurpose
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

    private val purposeLabels = mapOf(
        AccountPurpose.NORMAL to "عادی",
        AccountPurpose.DAILY_SPENDING to "حساب خرج روزانه",
        AccountPurpose.SAVINGS to "پس‌انداز",
        AccountPurpose.EMERGENCY to "اضطراری"
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
            onItemClick = { item ->
                val asset = viewModel.assets.value.firstOrNull { it.id == item.id } ?: return@FinancialEntryAdapter
                showPurposeDialog(asset)
            },
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
                    val typeLabel = typeLabels[asset.type] ?: ""
                    val purposeLabel = purposeLabels[asset.purpose] ?: asset.purpose.name
                    val subtitle = "$typeLabel · $purposeLabel"
                    FinancialEntryItem(
                        id = asset.id,
                        title = asset.title,
                        subtitle = if (asset.goldGrams != null && asset.goldGrams > 0) {
                            "$subtitle · ${CurrencyFormatter.formatPlainNumber(asset.goldGrams)} گرم · نرخ خودکار"
                        } else subtitle,
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

    override fun onResume() {
        super.onResume()
        // Re-prices weight-based gold assets against the latest rate every time this
        // screen is opened, so the user doesn't have to wait for the once-a-day worker.
        viewModel.refreshGoldValues()
    }

    private fun confirmDelete(asset: Asset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف")
            .setMessage("آیا از حذف \"${asset.title}\" مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteAsset(asset) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showPurposeDialog(asset: Asset) {
        val purposes = AccountPurpose.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("نوع حساب")
            .setSingleChoiceItems(
                purposes.map { purposeLabels[it] ?: it.name }.toTypedArray(),
                purposes.indexOf(asset.purpose)
            ) { dialog, position ->
                viewModel.setAccountPurpose(asset.id, purposes[position])
                dialog.dismiss()
            }
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
        val goldGramsInput = EditText(requireContext()).apply {
            hint = "مقدار طلا (گرم) - اختیاری"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            visibility = View.GONE
        }
        val goldHintText = android.widget.TextView(requireContext()).apply {
            text = "اگر گرم را وارد کنید، نیازی به مبلغ نیست: ارزش این دارایی هر روز خودکار با نرخ روز طلا به‌روزرسانی می‌شود."
            textSize = 11f
            setPadding(0, 4, 0, 12)
            visibility = View.GONE
        }
        val purposeSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                AccountPurpose.values().map { purposeLabels[it] ?: it.name }
            )
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(typeSpinner)
            addView(nameInput)
            addView(amountInput)
            addView(purposeSpinner)
            addView(goldGramsInput)
            addView(goldHintText)
        }
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                val isGold = types[position] == AssetType.GOLD
                goldGramsInput.visibility = if (isGold) View.VISIBLE else View.GONE
                goldHintText.visibility = if (isGold) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن دارایی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val type = types[typeSpinner.selectedItemPosition]
                val purpose = AccountPurpose.values()[purposeSpinner.selectedItemPosition]
                val grams = goldGramsInput.text.toString().toDoubleOrNull()
                if (type == AssetType.GOLD && grams != null && grams > 0) {
                    viewModel.addGoldAsset(name, grams, purpose)
                } else {
                    val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                    viewModel.addAsset(type, name, amount, purpose = purpose)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
