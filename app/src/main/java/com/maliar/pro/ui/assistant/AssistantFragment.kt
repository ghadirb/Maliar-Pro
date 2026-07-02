package com.maliar.pro.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.R
import com.maliar.pro.adapters.ChatAdapter
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.viewmodels.AssistantViewModel
import com.maliar.pro.viewmodels.AssistantViewModelFactory
import kotlinx.coroutines.launch

class AssistantFragment : Fragment() {

    private lateinit var viewModel: AssistantViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_assistant, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accountingManager = AccountingManager(requireContext())
        val reminderManager = ReminderManager(requireContext())
        val financialManager = FinancialStatusManager(requireContext())

        val factory = AssistantViewModelFactory(accountingManager, reminderManager, financialManager)
        viewModel = ViewModelProvider(this, factory)[AssistantViewModel::class.java]

        val recyclerView: RecyclerView = view.findViewById(R.id.chatRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        chatAdapter = ChatAdapter()
        recyclerView.adapter = chatAdapter

        val input: TextInputEditText = view.findViewById(R.id.messageInput)
        val sendBtn: MaterialButton = view.findViewById(R.id.sendButton)

        sendBtn.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.sendMessage(message)
                }
                input.text = null
            }
        }

        viewModel.chatMessages.observe(viewLifecycleOwner, { messages ->
            chatAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                recyclerView.scrollToPosition(messages.size - 1)
            }
        })
    }
}