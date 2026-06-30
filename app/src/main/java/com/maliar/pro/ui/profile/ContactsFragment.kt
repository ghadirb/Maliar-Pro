package com.maliar.pro.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentContactsBinding
import com.maliar.pro.database.ContactManager
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.launch

class ContactsFragment : Fragment() {

    private lateinit var binding: FragmentContactsBinding
    private lateinit var adapter: ContactsAdapter
    private val contactManager by lazy { ContactManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        loadContacts()
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(
            onItemClick = { contact ->
                // Handle contact click - show details or edit
            },
            onCallClick = { contact ->
                // Handle call button click
                makeCall(contact.phoneNumber)
            },
            onDeleteClick = { contact ->
                // Handle delete button click
                deleteContact(contact)
            }
        )
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRecyclerView.adapter = adapter
    }
    
    private fun makeCall(phoneNumber: String) {
        VoiceCallHelper.openDialer(requireContext(), phoneNumber)
    }
    
    private fun deleteContact(contact: com.maliar.pro.database.Contact) {
        lifecycleScope.launch {
            contactManager.deleteContact(contact)
            loadContacts()
        }
    }

    private fun setupFab() {
        binding.addContactFab.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            contactManager.getAllContacts().collect { contacts ->
                adapter.submitList(contacts)
            }
        }
    }

    private fun showAddContactDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("افزودن مخاطب")

        val view = android.view.LayoutInflater.from(requireContext())
            .inflate(com.maliar.pro.R.layout.dialog_add_contact, null)
        val nameInput = view.findViewById<android.widget.EditText>(com.maliar.pro.R.id.contactNameInput)
        val phoneInput = view.findViewById<android.widget.EditText>(com.maliar.pro.R.id.contactPhoneInput)

        builder.setView(view)
        builder.setPositiveButton("ذخیره") { _, _ ->
            val name = nameInput.text.toString()
            val phone = phoneInput.text.toString()

            if (name.isNotBlank() && phone.isNotBlank()) {
                lifecycleScope.launch {
                    val rowNumber = contactManager.getNextRowNumber()
                    val contact = com.maliar.pro.database.Contact(
                        rowNumber = rowNumber,
                        name = name,
                        phoneNumber = phone
                    )
                    contactManager.addContact(contact)
                    loadContacts()
                }
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
}
