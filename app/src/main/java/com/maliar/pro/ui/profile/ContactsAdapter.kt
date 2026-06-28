package com.maliar.pro.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.database.Contact
import com.maliar.pro.databinding.ItemContactBinding

class ContactsAdapter(
    private val onItemClick: (Contact) -> Unit,
    private val onCallClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.rowNumberText.text = contact.rowNumber.toString()
            binding.nameText.text = contact.name
            binding.phoneText.text = contact.phoneNumber

            binding.root.setOnClickListener {
                onItemClick(contact)
            }

            binding.callButton.setOnClickListener {
                onCallClick(contact)
            }

            binding.deleteButton.setOnClickListener {
                onDeleteClick(contact)
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}
