package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Check
import com.maliar.pro.databinding.ItemCheckBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckAdapter(
    private val onItemClick: (Check) -> Unit,
    private val onDeleteClick: (Check) -> Unit,
    private val onStatusClick: (Check) -> Unit
) : ListAdapter<Check, CheckAdapter.CheckViewHolder>(CheckDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckViewHolder {
        val binding = ItemCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CheckViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CheckViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CheckViewHolder(private val binding: ItemCheckBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(check: Check) {
            binding.checkNumberText.text = check.checkNumber
            binding.amountText.text = formatCurrency(check.amount)
            binding.dueDateText.text = formatDate(check.dueDate)
            binding.payeeText.text = check.recipient

            if (check.isCashed) {
                binding.statusBadge.text = "وصول شده"
                binding.statusBadge.setBackgroundColor(0xFF6BCB77.toInt())
                binding.statusButton.visibility = View.GONE
            } else {
                binding.statusBadge.text = "وصول نشده"
                binding.statusBadge.setBackgroundColor(0xFFFF6B6B.toInt())
                binding.statusButton.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onItemClick(check) }
            binding.deleteButton.setOnClickListener { onDeleteClick(check) }
            binding.statusButton.setOnClickListener { onStatusClick(check) }
        }

        private fun formatCurrency(amount: Double): String {
            return String.format("%,.0f تومان", amount)
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
            return sdf.format(Date(timestamp))
        }
    }

    class CheckDiffCallback : DiffUtil.ItemCallback<Check>() {
        override fun areItemsTheSame(oldItem: Check, newItem: Check): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Check, newItem: Check): Boolean {
            return oldItem == newItem
        }
    }
}
