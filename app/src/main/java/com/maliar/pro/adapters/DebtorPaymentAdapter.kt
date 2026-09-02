package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.DebtorPayment
import com.maliar.pro.databinding.ItemDebtorPaymentBinding
import com.maliar.pro.utils.PersianCalendarHelper

class DebtorPaymentAdapter : ListAdapter<DebtorPayment, DebtorPaymentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDebtorPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemDebtorPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(payment: DebtorPayment) {
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(payment.date)
            binding.paymentDateText.text = PersianCalendarHelper.formatJalali(y, m, d)
            binding.paymentNoteText.text = payment.note.ifBlank { "بدون یادداشت" }
            binding.paymentAmountText.text = com.maliar.pro.utils.CurrencyFormatter.format(payment.amount)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DebtorPayment>() {
        override fun areItemsTheSame(oldItem: DebtorPayment, newItem: DebtorPayment) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DebtorPayment, newItem: DebtorPayment) = oldItem == newItem
    }
}
