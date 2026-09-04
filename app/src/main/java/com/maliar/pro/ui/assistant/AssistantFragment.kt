package com.maliar.pro.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.R
import com.maliar.pro.adapters.ChatAdapter
import com.maliar.pro.viewmodels.AssistantViewModel
import com.maliar.pro.viewmodels.AssistantViewModelFactory
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantFragment : Fragment() {
    private lateinit var viewModel: AssistantViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_assistant, container, false)

        // Use factory for AssistantViewModel (dependencies)
        val factory = AssistantViewModelFactory(
            requireContext().applicationContext,
            AccountingManager(requireContext()),
            ReminderManager(requireContext()),
            FinancialStatusManager(requireContext())
        )
        viewModel = ViewModelProvider(this, factory)[AssistantViewModel::class.java]

        // Full GAPGPT integration with connection to other modules
        val recyclerView: RecyclerView = view.findViewById(R.id.chatRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        chatAdapter = ChatAdapter()
        recyclerView.adapter = chatAdapter

        val input: TextInputEditText = view.findViewById(R.id.messageInput)
        val sendBtn: MaterialButton = view.findViewById(R.id.sendButton)
        bindSmartCards(view)

        sendBtn.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                val preview = viewModel.previewQuickTransaction(message)
                if (preview != null) {
                    val kind = if (preview.isIncome) "درآمد" else "هزینه"
                    val amount = com.maliar.pro.utils.CurrencyFormatter.format(preview.amount, "")
                    AlertDialog.Builder(requireContext())
                        .setTitle("تأیید ثبت $kind")
                        .setMessage("مبلغ: $amount تومان\nتوضیح: ${preview.description}\n\nآیا این تراکنش در حسابداری ثبت شود؟")
                        .setPositiveButton("ثبت") { _, _ ->
                            viewModel.confirmQuickTransaction(preview)
                            input.text = null
                        }
                        .setNegativeButton("لغو", null)
                        .show()
                } else {
                    lifecycleScope.launch {
                        viewModel.sendMessage(message) // Connected to accounting, reminders
                        input.text = null
                    }
                }
            }
        }

        // Fixed StateFlow collect (instead of observe)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.chatMessages.collect { messages ->
                    chatAdapter.submitList(messages)
                }
            }
        }

        // If we were opened from the market-rate-swing notification (see
        // PendingAssistantQuestion / MainActivity.handleAssistantDeepLink), send that
        // question automatically so the user lands straight on the analysis they tapped
        // for, instead of an empty chat.
        com.maliar.pro.utils.PendingAssistantQuestion.consume()?.let { question ->
            viewModel.sendMessage(question)
        }

        return view
    }

    private fun bindSmartCards(view: View) {
        val suggestionsCount: TextView = view.findViewById(R.id.suggestionsCountText)
        val alertsCount: TextView = view.findViewById(R.id.alertsCountText)
        val quickAnalysis: TextView = view.findViewById(R.id.quickAnalysisText)
        val suggestionsButton: MaterialButton = view.findViewById(R.id.suggestionsButton)
        val alertsButton: MaterialButton = view.findViewById(R.id.alertsButton)
        val quickAnalysisButton: MaterialButton = view.findViewById(R.id.quickAnalysisButton)

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                val accounting = AccountingManager(appContext)
                val reminders = ReminderManager(appContext)
                val financial = FinancialStatusManager(appContext)

                val monthlyIncome = accounting.getMonthlyIncome()
                val monthlyExpense = accounting.getMonthlyExpense()
                val balance = accounting.getBalance()
                val activeReminders = reminders.getActiveRemindersList()
                val dueChecks = accounting.getDueChecks()
                val activeInstallments = accounting.getActiveInstallments()
                val activeGoals = financial.getActiveGoals()

                val suggestions = listOfNotNull(
                    if (monthlyExpense > monthlyIncome && monthlyIncome > 0) "هزینه این ماه از درآمد بیشتر است؛ بودجه را بازبینی کنید." else null,
                    if (activeGoals.isNotEmpty()) "برای ${activeGoals.size} هدف مالی فعال، پیشرفت ماهانه ثبت کنید." else null,
                    if (balance > 0 && monthlyIncome > monthlyExpense) "بخشی از مازاد ماهانه را برای پس‌انداز یا بدهی‌ها کنار بگذارید." else null
                )
                val alerts = dueChecks.size + activeInstallments.size + activeReminders.count { it.triggerTime <= System.currentTimeMillis() + 24 * 60 * 60 * 1000L }
                val status = when {
                    monthlyIncome <= 0.0 && monthlyExpense <= 0.0 -> "داده کافی نیست"
                    monthlyExpense > monthlyIncome -> "نیاز به توجه"
                    balance >= 0 -> "وضعیت خوب"
                    else -> "مانده منفی"
                }
                Triple(suggestions.size, alerts, status)
            }

            suggestionsCount.text = if (state.first > 0) "${state.first} پیشنهاد واقعی" else "فعلاً پیشنهادی نیست"
            alertsCount.text = if (state.second > 0) "${state.second} هشدار واقعی" else "هشداری ندارید"
            quickAnalysis.text = state.third
        }

        suggestionsButton.setOnClickListener { viewModel.sendMessage("بر اساس داده‌های واقعی برنامه، پیشنهادهای امروز من را توضیح بده") }
        alertsButton.setOnClickListener { viewModel.sendMessage("هشدارهای واقعی امروز، یادآوری‌ها، چک‌ها و اقساط را بررسی کن") }
        quickAnalysisButton.setOnClickListener { viewModel.sendMessage("یک تحلیل سریع از وضعیت مالی فعلی من بده") }
    }

}
