package com.maliar.pro.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.ChatAdapter
import com.maliar.pro.databinding.FragmentAssistantBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.viewmodels.AssistantViewModel
import com.maliar.pro.viewmodels.AssistantViewModelFactory
import kotlinx.coroutines.launch

class AssistantFragment : Fragment() {

    private lateinit var binding: FragmentAssistantBinding
    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: AssistantViewModel by viewModels {
        AssistantViewModelFactory(
            AccountingManager(requireContext()),
            ReminderManager(requireContext()),
            FinancialStatusManager(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSmartCards()
        setupSuggestedQuestions()
        setupChat()
        observeViewModel()
    }

    private fun setupSmartCards() {
        // Smart cards will be populated by AI in the future
    }

    private fun setupSuggestedQuestions() {
        binding.question1.setOnClickListener {
            viewModel.sendMessage("پولم کجا خرج شد؟")
        }
        binding.question2.setOnClickListener {
            viewModel.sendMessage("تحلیل این ماه")
        }
        binding.question3.setOnClickListener {
            viewModel.sendMessage("پیشنهاد سرمایه‌گذاری")
        }
        binding.question4.setOnClickListener {
            viewModel.sendMessage("آیا می‌توانم این خرید را انجام دهم؟")
        }
        binding.question5.setOnClickListener {
            viewModel.sendMessage("برنامه غذایی")
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecyclerView.adapter = chatAdapter

        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotBlank()) {
                viewModel.sendMessage(message)
                binding.messageInput.text?.clear()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.chatMessages.collect { messages ->
                chatAdapter.submitList(messages)
            }
        }
        lifecycleScope.launch {
            viewModel.isProcessing.collect { isProcessing ->
                binding.sendButton.isEnabled = !isProcessing
            }
        }
    }
}
