package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.R
import com.maliar.pro.database.Debtor
import com.maliar.pro.database.DebtorDirection
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddDebtorDialog(
    private val context: Context,
    private val debtorManager: DebtorManager,
    private val onSaved: () -> Unit = {}
) {
    private var selectedDueDate: Long? = null
    private var selectedJalali: Triple<Int, Int, Int>? = null

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("افزودن بدهکار / طلبکار")

        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_add_debtor, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        val phoneInput = view.findViewById<TextInputEditText>(R.id.phoneInput)
        val amountInput = view.findViewById<TextInputEditText>(R.id.amountInput)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.descriptionInput)
        val directionRadioGroup = view.findViewById<RadioGroup>(R.id.directionRadioGroup)
        val dueDateButton = view.findViewById<Button>(R.id.dueDateButton)

        dueDateButton.setOnClickListener {
            val today = PersianCalendarHelper.getCurrentJalaliDate()
            PersianDatePickerDialog(
                context,
                initialYear = selectedJalali?.first ?: today.first,
                initialMonth = selectedJalali?.second ?: today.second,
                initialDay = selectedJalali?.third ?: today.third
            ) { year, month, day ->
                selectedJalali = Triple(year, month, day)
                selectedDueDate = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
                dueDateButton.text = PersianCalendarHelper.formatJalali(year, month, day)
            }.show()
        }

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val name = nameInput.text.toString().trim()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (name.isNotBlank() && amount > 0) {
                val direction = if (directionRadioGroup.checkedRadioButtonId == R.id.iOweThemRadioButton)
                    DebtorDirection.I_OWE_THEM else DebtorDirection.THEY_OWE_ME
                val debtor = Debtor(
                    name = name,
                    phoneNumber = phoneInput.text.toString().trim(),
                    direction = direction,
                    amount = amount,
                    dueDate = selectedDueDate,
                    description = descriptionInput.text.toString().trim()
                )
                CoroutineScope(Dispatchers.IO).launch {
                    debtorManager.addDebtor(debtor)
                    CoroutineScope(Dispatchers.Main).launch { onSaved() }
                }
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
