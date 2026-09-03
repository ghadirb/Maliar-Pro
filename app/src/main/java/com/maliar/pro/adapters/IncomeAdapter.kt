package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Income
import com.maliar.pro.databinding.ItemIncomeBinding
import com.maliar.pro.databinding.ItemMonthHeaderBinding
import com.maliar.pro.utils.CurrencyFormatter
import com.maliar.pro.utils.PersianCalendarHelper

/**
 * Groups income entries into Jalali month sections with subtotals (see [GroupedRow] /
 * [groupByJalaliMonth]) instead of one flat, ever-growing list - this is what keeps the
 * income screen readable after years of entries pile up. [submitList] keeps the exact same
 * signature it always had (a flat, newest-first [Income] list); the grouping happens
 * internally, so nothing at the call site (IncomeListFragment) needed to change.
 */
class IncomeAdapter(
    private val onItemClick: (Income) -> Unit,
    private val onDeleteClick: (Income) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<GroupedRow<Income>> = emptyList()

    fun submitList(items: List<Income>) {
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
            IncomeViewHolder(ItemIncomeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GroupedRow.Header -> (holder as HeaderViewHolder).bind(row)
            is GroupedRow.Item -> (holder as IncomeViewHolder).bind(row.data)
        }
    }

    class HeaderViewHolder(private val binding: ItemMonthHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: GroupedRow.Header) {
            binding.monthLabel.text = header.label
            binding.monthSubtotal.text = "جمع: ${CurrencyFormatter.format(header.subtotal)}"
        }
    }

    inner class IncomeViewHolder(private val binding: ItemIncomeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(income: Income) {
            binding.sourceText.text = income.category
            binding.amountText.text = CurrencyFormatter.format(income.amount)
            binding.dateText.text = formatJalaliDate(income.date)
            binding.descriptionText.text = income.description

            binding.root.setOnClickListener { onItemClick(income) }
            binding.deleteButton.setOnClickListener { onDeleteClick(income) }
        }

        // Was SimpleDateFormat("yyyy/MM/dd", Locale("fa","IR")) before, which despite the
        // "fa" locale still formats using the Gregorian calendar on this project's target
        // API levels - so entries were quietly showing Gregorian dates while the new month
        // section headers above them show Jalali. Using the same PersianCalendarHelper the
        // rest of the app already relies on keeps this screen internally consistent.
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
