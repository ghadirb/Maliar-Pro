package com.maliar.pro.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemChatMessageBinding
import com.maliar.pro.viewmodels.AssistantViewModel

class ChatAdapter : ListAdapter<AssistantViewModel.ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: AssistantViewModel.ChatMessage) {
            binding.messageText.text = message.text
            if (message.isUser) {
                binding.messageContainer.setBackgroundResource(android.R.color.holo_blue_light)
            } else {
                binding.messageContainer.setBackgroundResource(android.R.color.darker_gray)
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<AssistantViewModel.ChatMessage>() {
        override fun areItemsTheSame(oldItem: AssistantViewModel.ChatMessage, newItem: AssistantViewModel.ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AssistantViewModel.ChatMessage, newItem: AssistantViewModel.ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
