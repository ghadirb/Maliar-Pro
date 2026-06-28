package com.maliar.pro.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.databinding.FragmentAssistantBinding

class AssistantFragment : Fragment() {

    private lateinit var binding: FragmentAssistantBinding

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
    }

    private fun setupSmartCards() {
        // Smart cards will be populated by AI in the future
        // For now, show placeholder cards
    }

    private fun setupSuggestedQuestions() {
        // Setup suggested question chips
        binding.question1.setOnClickListener {
            sendQuestion("پولم کجا خرج شد؟")
        }
        binding.question2.setOnClickListener {
            sendQuestion("تحلیل این ماه")
        }
        binding.question3.setOnClickListener {
            sendQuestion("پیشنهاد سرمایه‌گذاری")
        }
        binding.question4.setOnClickListener {
            sendQuestion("آیا می‌توانم این خرید را انجام دهم؟")
        }
        binding.question5.setOnClickListener {
            sendQuestion("برنامه غذایی")
        }
    }

    private fun setupChat() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotBlank()) {
                sendQuestion(message)
                binding.messageInput.text?.clear()
            }
        }

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun sendQuestion(question: String) {
        // Send question to AI assistant
    }
}
