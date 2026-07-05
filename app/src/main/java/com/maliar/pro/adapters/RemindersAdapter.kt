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

            // Previously this badge only ever changed color while its text stayed the
            // fixed placeholder word "اولویت" - so the actual chosen priority (کم/متوسط/
            // زیاد) was never visible on the list, only inferable from a color swatch.
            val prio = reminder.priority
            val (priorityLabel, priorityColor) = when {
                prio.contains("HIGH", ignoreCase = true) -> "زیاد" to 0xFFFF6B6B.toInt()
                prio.contains("LOW", ignoreCase = true) -> "کم" to 0xFF6BCB77.toInt()
                else -> "متوسط" to 0xFFFFD93D.toInt()
            }
            binding.priorityBadge.text = priorityLabel
            binding.priorityBadge.setBackgroundColor(priorityColor)

            // The alert-type chosen when creating/editing the reminder (نوتیفیکیشن/تمام
            // صفحه/هوشمند) previously wasn't shown anywhere on the list at all.
            val alertType = reminder.alertType
            binding.alertTypeBadge.text = when {
                alertType.contains("FULL_SCREEN", ignoreCase = true) -> "📱 تمام صفحه"
                alertType.contains("SMART", ignoreCase = true) -> "🧠 هوشمند"
                else -> "🔔 نوتیفیکیشن"
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
