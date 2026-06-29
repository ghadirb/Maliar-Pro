package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Installment
import com.maliar.pro.databinding.ItemInstallmentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstallmentAdapter(
    private val onItemClick: (Installment) -> Unit,
    private val onDeleteClick: (Installment) -> Unit,
    private val onPayClick: (Installment) -> Unit
) : ListAdapter<Installment, InstallmentAdapter.InstallmentViewHolder>(InstallmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstallmentViewHolder {
        val binding = ItemInstallmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InstallmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InstallmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InstallmentViewHolder(private val binding: ItemInstallmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(installment: Installment) {
            binding.titleText.text = installment.title
            binding.amountText.text = formatCurrency(installment.monthlyAmount)
            binding.progressText.text = "${installment.paidInstallments}/${installment.installmentCount}"
            binding.recipientText.text = installment.lender
            binding.progressBar.progress = (installment.paidInstallments * 100 / installment.installmentCount)

            if (installment.paidInstallments >= installment.installmentCount) {
                binding.statusBadge.text = "تکمیل شده"
                binding.payButton.visibility = ViewGroup.GONE
            } else {
                binding.statusBadge.text = "فعال"
                binding.payButton.visibility = ViewGroup.VISIBLE
            }

            binding.root.setOnClickListener { onItemClick(installment) }
            binding.deleteButton.setOnClickListener { onDeleteClick(installment) }
            binding.payButton.setOnClickListener { onPayClick(installment) }
        }

        private fun formatCurrency(amount: Double): String {
            return String.format("%,.0f تومان", amount)
        }
    }

    class InstallmentDiffCallback : DiffUtil.ItemCallback<Installment>() {
        override fun areItemsTheSame(oldItem: Installment, newItem: Installment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Installment, newItem: Installment): Boolean {
            return oldItem == newItem
        }
    }
}
