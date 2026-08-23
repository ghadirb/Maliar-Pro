package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.R
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.DebtorPayment
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddDebtorPaymentDialog(
    private val context: Context,
    private val debtorManager: DebtorManager,
    private val debtorId: Long,
    private val onSaved: () -> Unit = {}
) {
    private var selectedDate: Long = System.currentTimeMillis()

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("ثبت پرداخت")

        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_add_debtor_payment, null)
        val amountInput = view.findViewById<TextInputEditText>(R.id.paymentAmountInput)
        val noteInput = view.findViewById<TextInputEditText>(R.id.paymentNoteInput)
        val dateButton = view.findViewById<Button>(R.id.paymentDateButton)

        val today = PersianCalendarHelper.getCurrentJalaliDate()
        dateButton.text = PersianCalendarHelper.formatJalali(today.first, today.second, today.third)

        dateButton.setOnClickListener {
            PersianDatePickerDialog(
                context,
                initialYear = today.first,
                initialMonth = today.second,
                initialDay = today.third
            ) { year, month, day ->
                selectedDate = PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
                dateButton.text = PersianCalendarHelper.formatJalali(year, month, day)
            }.show()
        }

        builder.setView(view)
        builder.setPositiveButton("ثبت") { _, _ ->
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                val payment = DebtorPayment(
                    debtorId = debtorId,
                    amount = amount,
                    date = selectedDate,
                    note = noteInput.text.toString().trim()
                )
                CoroutineScope(Dispatchers.IO).launch {
                    debtorManager.addPayment(payment)
                    CoroutineScope(Dispatchers.Main).launch { onSaved() }
                }
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
