package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemFinancialEntryBinding

/**
 * Generic row shown in the assets / debts / goals / fixed-incomes list screens.
 * [statusText]/[statusColor] are optional (e.g. "پرداخت شده" for a debt).
 */
data class FinancialEntryItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amountText: String,
    val amountColor: Int,
    val statusText: String? = null,
    val statusColor: Int? = null
)

class FinancialEntryAdapter(
    private val onItemClick: (FinancialEntryItem) -> Unit,
    private val onDeleteClick: (FinancialEntryItem) -> Unit
) : ListAdapter<FinancialEntryItem, FinancialEntryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFinancialEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemFinancialEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FinancialEntryItem) {
            binding.titleText.text = item.title
            if (item.subtitle.isNotEmpty()) {
                binding.subtitleText.text = item.subtitle
                binding.subtitleText.visibility = View.VISIBLE
            } else {
                binding.subtitleText.visibility = View.GONE
            }
            binding.amountText.text = item.amountText
            binding.amountText.setTextColor(item.amountColor)

            if (item.statusText != null) {
                binding.statusBadge.text = item.statusText
                binding.statusBadge.visibility = View.VISIBLE
                if (item.statusColor != null) {
                    binding.statusBadge.setBackgroundColor(item.statusColor)
                }
            } else {
                binding.statusBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.deleteIcon.setOnClickListener { onDeleteClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FinancialEntryItem>() {
        override fun areItemsTheSame(oldItem: FinancialEntryItem, newItem: FinancialEntryItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FinancialEntryItem, newItem: FinancialEntryItem) =
            oldItem == newItem
    }
}
