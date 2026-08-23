package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemCalendarDayBinding

/** One cell of the financial calendar grid. [day] is null for the leading blank cells before
 *  the 1st of the month (so the grid lines up under the correct weekday column). */
data class CalendarDayCell(val day: Int?, val hasEvents: Boolean, val isToday: Boolean)

class CalendarDayAdapter(
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.ViewHolder>() {

    private var cells: List<CalendarDayCell> = emptyList()
    private var selectedDay: Int? = null

    fun submitCells(newCells: List<CalendarDayCell>, selected: Int?) {
        cells = newCells
        selectedDay = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(cells[position])
    override fun getItemCount() = cells.size

    inner class ViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cell: CalendarDayCell) {
            if (cell.day == null) {
                binding.dayNumberText.text = ""
                binding.eventDot.visibility = View.INVISIBLE
                binding.root.isClickable = false
                binding.root.setBackgroundColor(0x00000000)
                return
            }
            binding.dayNumberText.text = cell.day.toString()
            binding.eventDot.visibility = if (cell.hasEvents) View.VISIBLE else View.INVISIBLE
            binding.root.isClickable = true

            when {
                cell.day == selectedDay -> {
                    binding.root.setBackgroundColor(0xFF2196F3.toInt())
                    binding.dayNumberText.setTextColor(0xFFFFFFFF.toInt())
                }
                cell.isToday -> {
                    binding.root.setBackgroundColor(0x332196F3)
                    binding.dayNumberText.setTextColor(0xFF000000.toInt())
                }
                else -> {
                    binding.root.background = null
                    binding.dayNumberText.setTextColor(0xFF000000.toInt())
                }
            }

            binding.root.setOnClickListener { onDayClick(cell.day) }
        }
    }
}
