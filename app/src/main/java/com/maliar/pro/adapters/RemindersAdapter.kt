package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemindersAdapter(
    private val onItemClick: (ReminderEntity) -> Unit,
    private val onDeleteClick: (ReminderEntity) -> Unit,
    private val onCompleteClick: (ReminderEntity) -> Unit
) : ListAdapter<ReminderEntity, RemindersAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reminder: ReminderEntity) {
            binding.titleText.text = reminder.title
            binding.descriptionText.text = reminder.description
            binding.timeText.text = formatTime(reminder.triggerTime)
            binding.categoryText.text = reminder.category
            
            val prio = reminder.priority
            when {
                prio.contains("HIGH", ignoreCase = true) || prio == "HIGH" -> binding.priorityBadge.setBackgroundColor(0xFFFF6B6B.toInt())
                prio.contains("MEDIUM", ignoreCase = true) || prio == "MEDIUM" -> binding.priorityBadge.setBackgroundColor(0xFFFFD93D.toInt())
                else -> binding.priorityBadge.setBackgroundColor(0xFF6BCB77.toInt())
            }

            if (reminder.isCompleted) {
                binding.completedBadge.visibility = View.VISIBLE
                binding.completeButton.visibility = View.GONE
            } else {
                binding.completedBadge.visibility = View.GONE
                binding.completeButton.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onItemClick(reminder) }
            binding.deleteButton.setOnClickListener { onDeleteClick(reminder) }
            binding.completeButton.setOnClickListener { onCompleteClick(reminder) }
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR"))
            return sdf.format(Date(timestamp))
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<ReminderEntity>() {
        override fun areItemsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity): Boolean {
            return oldItem == newItem
        }
    }
}
