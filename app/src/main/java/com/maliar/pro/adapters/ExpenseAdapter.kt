package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Expense
import com.maliar.pro.databinding.ItemExpenseBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseAdapter(
    private val onItemClick: (Expense) -> Unit,
    private val onDeleteClick: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExpenseViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            binding.categoryText.text = expense.category
            binding.amountText.text = formatCurrency(expense.amount)
            binding.dateText.text = formatDate(expense.date)
            binding.descriptionText.text = expense.description

            binding.root.setOnClickListener { onItemClick(expense) }
            binding.deleteButton.setOnClickListener { onDeleteClick(expense) }
        }

        private fun formatCurrency(amount: Double): String {
            return String.format("%,.0f تومان", amount)
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
            return sdf.format(Date(timestamp))
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem == newItem
        }
    }
}
