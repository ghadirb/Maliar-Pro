package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Expense
import com.maliar.pro.databinding.ItemExpenseBinding
import com.maliar.pro.databinding.ItemMonthHeaderBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper

/**
 * Groups expense entries into Jalali month sections with subtotals - see IncomeAdapter,
 * which this mirrors exactly. Expenses are usually the highest-volume accounting category
 * (day-to-day spending logged often), so this is the screen where an ungrouped flat list
 * would get unusable first. [submitList]'s signature is unchanged from before.
 */
class ExpenseAdapter(
    private val onItemClick: (Expense) -> Unit,
    private val onDeleteClick: (Expense) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<GroupedRow<Expense>> = emptyList()

    fun submitList(items: List<Expense>) {
        rows = groupByJalaliMonth(items, timestampOf = { it.date }, amountOf = { it.amount })
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is GroupedRow.Header -> VIEW_TYPE_HEADER
        is GroupedRow.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemMonthHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            ExpenseViewHolder(ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GroupedRow.Header -> (holder as HeaderViewHolder).bind(row)
            is GroupedRow.Item -> (holder as ExpenseViewHolder).bind(row.data)
        }
    }

    class HeaderViewHolder(private val binding: ItemMonthHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: GroupedRow.Header) {
            binding.monthLabel.text = header.label
            binding.monthSubtotal.text = "جمع: ${CurrencyFormatter.format(header.subtotal)}"
        }
    }

    inner class ExpenseViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            binding.categoryText.text = expense.category
            binding.amountText.text = CurrencyFormatter.format(expense.amount)
            binding.dateText.text = formatJalaliDate(expense.date)
            binding.descriptionText.text = expense.description

            binding.root.setOnClickListener { onItemClick(expense) }
            binding.deleteButton.setOnClickListener { onDeleteClick(expense) }
        }

        // Was SimpleDateFormat("yyyy/MM/dd", Locale("fa","IR")) before - see IncomeAdapter's
        // identical comment for why that quietly showed Gregorian, not Jalali, dates.
        private fun formatJalaliDate(timestamp: Long): String {
            val (year, month, day) = PersianCalendarHelper.gregorianMillisToJalali(timestamp)
            return PersianCalendarHelper.formatJalali(year, month, day)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
