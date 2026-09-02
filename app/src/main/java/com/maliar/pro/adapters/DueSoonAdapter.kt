package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemDueSoonBinding
import com.maliar.pro.models.DueItem
import com.maliar.pro.models.DueItemType
import com.maliar.pro.utils.PersianCalendarHelper

class DueSoonAdapter(
    private val onItemClick: (DueItem) -> Unit = {}
) : ListAdapter<DueItem, DueSoonAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDueSoonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDueSoonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DueItem) {
            val icon = when (item.type) {
                DueItemType.CHECK -> "📋"
                DueItemType.INSTALLMENT -> "📊"
                DueItemType.DEBTOR_THEY_OWE_ME -> "🤝"
                DueItemType.DEBTOR_I_OWE_THEM -> "🧾"
            }
            binding.titleText.text = "$icon ${item.title}"
            binding.subtitleText.text = item.subtitle
            binding.amountText.text = com.maliar.pro.utils.CurrencyFormatter.format(item.amount)

            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(item.dueDate)
            val jalaliDate = PersianCalendarHelper.formatJalali(y, m, d)

            when {
                item.isOverdue -> {
                    binding.dueDateText.text = "$jalaliDate (${-item.daysLeft} روز گذشته)"
                    binding.dueDateText.setTextColor(0xFFF44336.toInt())
                    binding.urgencyStripe.setBackgroundColor(0xFFF44336.toInt())
                }
                item.daysLeft <= 3 -> {
                    binding.dueDateText.text = "$jalaliDate (${item.daysLeft} روز مانده)"
                    binding.dueDateText.setTextColor(0xFFFF9800.toInt())
                    binding.urgencyStripe.setBackgroundColor(0xFFFF9800.toInt())
                }
                else -> {
                    binding.dueDateText.text = "$jalaliDate (${item.daysLeft} روز مانده)"
                    binding.dueDateText.setTextColor(0xFF2196F3.toInt())
                    binding.urgencyStripe.setBackgroundColor(0xFF2196F3.toInt())
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DueItem>() {
        override fun areItemsTheSame(oldItem: DueItem, newItem: DueItem) =
            oldItem.type == newItem.type && oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DueItem, newItem: DueItem) = oldItem == newItem
    }
}
