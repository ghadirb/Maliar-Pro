package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.DebtorDirection
import com.maliar.pro.databinding.ItemDebtorBinding
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.DebtorWithBalance

class DebtorAdapter(
    private val onItemClick: (DebtorWithBalance) -> Unit,
    private val onDeleteClick: (DebtorWithBalance) -> Unit
) : ListAdapter<DebtorWithBalance, DebtorAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDebtorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDebtorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DebtorWithBalance) {
            val debtor = item.debtor
            binding.nameText.text = debtor.name
            binding.directionText.text = if (debtor.direction == DebtorDirection.THEY_OWE_ME)
                "🤝 بدهکار به شما" else "🧾 شما بدهکارید"
            binding.directionText.setTextColor(
                if (debtor.direction == DebtorDirection.THEY_OWE_ME) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
            )
            binding.remainingText.text = String.format("%,.0f تومان", item.remaining)

            if (debtor.dueDate != null) {
                val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(debtor.dueDate)
                binding.dueDateText.text = "سررسید: ${PersianCalendarHelper.formatJalali(y, m, d)}"
                binding.dueDateText.visibility = View.VISIBLE
            } else {
                binding.dueDateText.visibility = View.GONE
            }

            if (debtor.isSettled) {
                binding.statusBadge.text = "تسویه شده"
                binding.statusBadge.setBackgroundColor(0xFF4CAF50.toInt())
            } else {
                binding.statusBadge.text = "باز"
                binding.statusBadge.setBackgroundColor(0xFFFF9800.toInt())
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { onDeleteClick(item); true }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DebtorWithBalance>() {
        override fun areItemsTheSame(oldItem: DebtorWithBalance, newItem: DebtorWithBalance) =
            oldItem.debtor.id == newItem.debtor.id

        override fun areContentsTheSame(oldItem: DebtorWithBalance, newItem: DebtorWithBalance) = oldItem == newItem
    }
}
