package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.Income
import com.maliar.pro.database.Expense
import com.maliar.pro.database.Reminder
import com.maliar.pro.database.Priority
import com.maliar.pro.database.RecurringType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

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
        
        // Add income: "افزودن درآمد 1000000 حقوق"
        if (lower.contains("افزودن درآمد") || lower.contains("add income")) {
            val parts = message.split(" ")
            val amount = parts.find { it.toDoubleOrNull() != null }?.toDoubleOrNull() ?: 0.0
            val category = parts.lastOrNull { it.toDoubleOrNull() == null } ?: "عمومی"
            if (amount > 0) {
                accountingManager.addIncome(Income(category = category, amount = amount, description = message, date = Date().time))
                return "درآمد $amount تومان با دسته‌بندی $category اضافه شد"
            }
            return "لطفاً مبلغ را مشخص کنید"
        }

        // Add expense: "افزودن هزینه 500000 خرید"
        if (lower.contains("افزودن هزینه") || lower.contains("add expense")) {
            val parts = message.split(" ")
            val amount = parts.find { it.toDoubleOrNull() != null }?.toDoubleOrNull() ?: 0.0
            val category = parts.lastOrNull { it.toDoubleOrNull() == null } ?: "عمومی"
            if (amount > 0) {
                accountingManager.addExpense(Expense(category = category, amount = amount, description = message, date = Date().time))
                return "هزینه $amount تومان با دسته‌بندی $category اضافه شد"
            }
            return "لطفاً مبلغ را مشخص کنید"
        }

        // Add reminder: "افزودن یادآوری پرداخت قبض فردا"
        if (lower.contains("افزودن یادآوری") || lower.contains("add reminder")) {
            val title = message.substringAfter("یادآوری").trim()
            if (title.isNotBlank()) {
                reminderManager.addReminder(Reminder(
                    title = title,
                    description = message,
                    reminderTime = System.currentTimeMillis() + 86400000,
                    priority = Priority.MEDIUM,
                    isRecurring = false,
                    recurringType = RecurringType.NONE,
                    recurringInterval = 1,
                    isCompleted = false,
                    category = ""
                ))
                return "یادآوری '$title' برای فردا اضافه شد"
            }
            return "لطفاً عنوان یادآوری را مشخص کنید"
        }

        // Query commands
        return when {
            lower.contains("تراز") || lower.contains("balance") -> {
                val balance = accountingManager.getBalance()
                "تراز فعلی شما: ${String.format("%,.0f", balance)} تومان"
            }
            lower.contains("درآمد") || lower.contains("income") -> {
                val income = accountingManager.getTotalIncome()
                val monthlyIncome = accountingManager.getMonthlyIncome()
                "کل درآمد: ${String.format("%,.0f", income)} تومان\nدرآمد این ماه: ${String.format("%,.0f", monthlyIncome)} تومان"
            }
            lower.contains("هزینه") || lower.contains("expense") -> {
                val expense = accountingManager.getTotalExpense()
                val monthlyExpense = accountingManager.getMonthlyExpense()
                "کل هزینه: ${String.format("%,.0f", expense)} تومان\nهزینه این ماه: ${String.format("%,.0f", monthlyExpense)} تومان"
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
                val reminderList = reminders.take(5).joinToString("\n") { "• ${it.title}" }
                "شما ${reminders.size} یادآوری فعال دارید:\n$reminderList"
            }
            lower.contains("چک") || lower.contains("check") -> {
                val checks = accountingManager.getUncashedChecks()
                "شما ${checks.size} چک وصول نشده دارید"
            }
            lower.contains("قسط") || lower.contains("installment") -> {
                val installments = accountingManager.getActiveInstallments()
                "شما ${installments.size} قسط فعال دارید"
            }
            lower.contains("تحلیل") || lower.contains("analysis") -> {
                val income = accountingManager.getMonthlyIncome()
                val expense = accountingManager.getMonthlyExpense()
                val balance = accountingManager.getBalance()
                """
                تحلیل مالی شما:
                - درآمد این ماه: ${String.format("%,.0f", income)} تومان
                - هزینه این ماه: ${String.format("%,.0f", expense)} تومان
                - تراز کل: ${String.format("%,.0f", balance)} تومان
                - نسبت هزینه به درآمد: ${if (income > 0) String.format("%.1f%%", (expense / income) * 100) else "0%"}
                """.trimIndent()
            }
            else -> "دستورات موجود:\n• تراز/درآمد/هزینه\n• افزودن درآمد [مبلغ] [دسته]\n• افزودن هزینه [مبلغ] [دسته]\n• افزودن یادآوری [عنوان]\n• تحلیل مالی\n• دارایی/بدهی/خالص\n• یادآوری/چک/قسط"
        }
    }
}
