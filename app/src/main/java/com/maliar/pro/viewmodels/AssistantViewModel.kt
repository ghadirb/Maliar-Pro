package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.FinancialStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val accountingManager: AccountingManager,
    private val reminderManager: ReminderManager,
    private val financialManager: FinancialStatusManager
) : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    data class ChatMessage(val id: String, val text: String, val isUser: Boolean)

    fun sendMessage(message: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _chatMessages.value = _chatMessages.value + ChatMessage(System.currentTimeMillis().toString(), message, true)
            val response = processCommand(message)
            _chatMessages.value = _chatMessages.value + ChatMessage((System.currentTimeMillis() + 1).toString(), response, false)
            _isProcessing.value = false
        }
    }

    private suspend fun processCommand(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("تراز") || lower.contains("balance") -> {
                val balance = accountingManager.getBalance()
                "تراز فعلی شما: ${String.format("%,.0f", balance)} تومان"
            }
            lower.contains("درآمد") || lower.contains("income") -> {
                val income = accountingManager.getTotalIncome()
                "کل درآمد: ${String.format("%,.0f", income)} تومان"
            }
            lower.contains("هزینه") || lower.contains("expense") -> {
                val expense = accountingManager.getTotalExpense()
                "کل هزینه: ${String.format("%,.0f", expense)} تومان"
            }
            lower.contains("دارایی") || lower.contains("assets") -> {
                val assets = financialManager.getTotalAssets()
                "کل دارایی‌ها: ${String.format("%,.0f", assets)} تومان"
            }
            lower.contains("بدهی") || lower.contains("debt") -> {
                val debts = financialManager.getTotalUnpaidDebts()
                "کل بدهی‌ها: ${String.format("%,.0f", debts)} تومان"
            }
            lower.contains("خالص") || lower.contains("net") -> {
                val assets = financialManager.getTotalAssets()
                val debts = financialManager.getTotalUnpaidDebts()
                val netWorth = assets - debts
                "خالص دارایی: ${String.format("%,.0f", netWorth)} تومان"
            }
            lower.contains("یادآوری") || lower.contains("reminder") -> {
                val reminders = reminderManager.getActiveRemindersList()
                "شما ${reminders.size} یادآوری فعال دارید"
            }
            lower.contains("چک") || lower.contains("check") -> {
                val checks = accountingManager.getUncashedChecks()
                "شما ${checks.size} چک وصول نشده دارید"
            }
            lower.contains("قسط") || lower.contains("installment") -> {
                val installments = accountingManager.getActiveInstallments()
                "شما ${installments.size} قسط فعال دارید"
            }
            else -> "دستور شناخته نشد. می‌توانید درباره تراز، درآمد، هزینه، دارایی، بدهی، یادآوری، چک یا قسط بپرسید"
        }
    }
}
