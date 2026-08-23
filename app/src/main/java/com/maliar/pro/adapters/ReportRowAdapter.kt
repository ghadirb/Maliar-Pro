package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemReportRowBinding
import com.maliar.pro.utils.PersianCalendarHelper

data class ReportRowItem(val description: String, val category: String, val date: Long, val amount: Double)

class ReportRowAdapter(private val amountColor: Int) : RecyclerView.Adapter<ReportRowAdapter.ViewHolder>() {

    private var items: List<ReportRowItem> = emptyList()

    fun submitList(newItems: List<ReportRowItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemReportRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReportRowItem) {
            binding.descriptionText.text = item.description.ifBlank { "بدون توضیح" }
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(item.date)
            val dateStr = "$d ${PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(m - 1) { "" }}"
            binding.categoryDateText.text = if (item.category.isNotBlank()) "${item.category} · $dateStr" else dateStr
            binding.amountText.text = String.format("%,.0f تومان", item.amount)
            binding.amountText.setTextColor(amountColor)
        }
    }
}
