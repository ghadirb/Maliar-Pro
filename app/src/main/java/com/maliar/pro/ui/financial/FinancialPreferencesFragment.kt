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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maliar.pro.database.FinancialPreferences
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.PurchasePreference
import com.maliar.pro.database.RiskTolerance
import com.maliar.pro.databinding.FragmentFinancialPreferencesBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.viewmodels.FinancialPreferencesViewModel
import com.maliar.pro.viewmodels.FinancialPreferencesViewModelFactory
import kotlinx.coroutines.launch

class FinancialPreferencesFragment : Fragment() {

    private lateinit var binding: FragmentFinancialPreferencesBinding
    private val financialManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: FinancialPreferencesViewModel by viewModels {
        FinancialPreferencesViewModelFactory(financialManager)
    }

    private val riskLabels = mapOf(
        RiskTolerance.LOW to "کم",
        RiskTolerance.MEDIUM to "متوسط",
        RiskTolerance.HIGH to "زیاد"
    )
    private val purchaseLabels = mapOf(
        PurchasePreference.CASH to "نقدی",
        PurchasePreference.INSTALLMENT to "قسطی"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editPreferencesButton.setOnClickListener {
            showEditDialog(viewModel.preferences.value)
        }

        lifecycleScope.launch {
            viewModel.preferences.collect { prefs ->
                if (prefs != null) {
                    binding.emptyPreferencesText.visibility = View.GONE
                    binding.emergencyFundText.text = CurrencyFormatter.format(prefs.emergencyFundTarget)
                    binding.savingGoalText.text = CurrencyFormatter.format(prefs.monthlySavingGoal)
                    binding.riskToleranceText.text = riskLabels[prefs.riskTolerance] ?: "متوسط"
                    binding.purchasePreferenceText.text = purchaseLabels[prefs.purchasePreference] ?: "نقدی"
                } else {
                    binding.emptyPreferencesText.visibility = View.VISIBLE
                    binding.emergencyFundText.text = CurrencyFormatter.format(0.0)
                    binding.savingGoalText.text = CurrencyFormatter.format(0.0)
                    binding.riskToleranceText.text = riskLabels[RiskTolerance.MEDIUM]
                    binding.purchasePreferenceText.text = purchaseLabels[PurchasePreference.CASH]
                }
            }
        }
    }

    private fun showEditDialog(current: FinancialPreferences?) {
        val emergencyInput = EditText(requireContext()).apply {
            hint = "هدف صندوق اضطراری (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.emergencyFundTarget?.toLong()?.toString() ?: "")
        }
        val savingInput = EditText(requireContext()).apply {
            hint = "هدف پس‌انداز ماهانه (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.monthlySavingGoal?.toLong()?.toString() ?: "")
        }
        val riskValues = RiskTolerance.values()
        val riskSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                riskValues.map { riskLabels[it] ?: it.name }
            )
            setSelection(riskValues.indexOf(current?.riskTolerance ?: RiskTolerance.MEDIUM))
        }
        val purchaseValues = PurchasePreference.values()
        val purchaseSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                purchaseValues.map { purchaseLabels[it] ?: it.name }
            )
            setSelection(purchaseValues.indexOf(current?.purchasePreference ?: PurchasePreference.CASH))
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(emergencyInput)
            addView(savingInput)
            addView(riskSpinner)
            addView(purchaseSpinner)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات مالی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val emergency = emergencyInput.text.toString().toDoubleOrNull() ?: 0.0
                val saving = savingInput.text.toString().toDoubleOrNull() ?: 0.0
                val risk = riskValues[riskSpinner.selectedItemPosition]
                val purchase = purchaseValues[purchaseSpinner.selectedItemPosition]
                viewModel.savePreferences(emergency, saving, risk, purchase)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
