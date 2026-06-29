package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Income
import com.maliar.pro.databinding.ItemIncomeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncomeAdapter(
    private val onItemClick: (Income) -> Unit,
    private val onDeleteClick: (Income) -> Unit
) : ListAdapter<Income, IncomeAdapter.IncomeViewHolder>(IncomeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncomeViewHolder {
        val binding = ItemIncomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IncomeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IncomeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IncomeViewHolder(private val binding: ItemIncomeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(income: Income) {
            binding.sourceText.text = income.source
            binding.amountText.text = formatCurrency(income.amount)
            binding.dateText.text = formatDate(income.date)
            binding.descriptionText.text = income.description

            binding.root.setOnClickListener { onItemClick(income) }
            binding.deleteButton.setOnClickListener { onDeleteClick(income) }
        }

        private fun formatCurrency(amount: Double): String {
            return String.format("%,.0f تومان", amount)
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
            return sdf.format(Date(timestamp))
        }
    }

    class IncomeDiffCallback : DiffUtil.ItemCallback<Income>() {
        override fun areItemsTheSame(oldItem: Income, newItem: Income): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Income, newItem: Income): Boolean {
            return oldItem == newItem
        }
    }
}
