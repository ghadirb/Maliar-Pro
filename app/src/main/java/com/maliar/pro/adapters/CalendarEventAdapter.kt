package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemCalendarEventBinding
import com.maliar.pro.models.CalendarEvent
import com.maliar.pro.models.CalendarEventType

class CalendarEventAdapter : RecyclerView.Adapter<CalendarEventAdapter.ViewHolder>() {

    private var events: List<CalendarEvent> = emptyList()

    fun submitList(newEvents: List<CalendarEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(events[position])
    override fun getItemCount() = events.size

    class ViewHolder(private val binding: ItemCalendarEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: CalendarEvent) {
            binding.eventIconText.text = when (event.type) {
                CalendarEventType.INSTALLMENT -> "📊"
                CalendarEventType.CHECK -> "📋"
                CalendarEventType.DEBT -> "🏦"
                CalendarEventType.DEBTOR -> "🤝"
                CalendarEventType.INCOME -> "💰"
                CalendarEventType.REMINDER -> "⏰"
            }
            binding.eventTitleText.text = event.title
            binding.eventSubtitleText.text = event.subtitle
            if (event.amount != null) {
                binding.eventAmountText.text = String.format("%,.0f تومان", event.amount)
                binding.eventAmountText.visibility = android.view.View.VISIBLE
            } else {
                binding.eventAmountText.visibility = android.view.View.GONE
            }
        }
    }
}
