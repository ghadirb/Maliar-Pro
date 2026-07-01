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
import com.maliar.pro.database.ContactManager
import com.maliar.pro.models.AIProvider
import com.maliar.pro.utils.PreferencesManager
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

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
            
            // Try online AI with priority: GAPGPT -> Liara -> local processing
            val response = try {
                val gapgptResponse = callGapgptAI(message)
                if (gapgptResponse != null) gapgptResponse
                else {
                    val liaraResponse = callLiaraAI(message)
                    if (liaraResponse != null) liaraResponse
                    else processCommand(message)
                }
            } catch (e: Exception) {
                processCommand(message)
            }
            
            _chatMessages.value = _chatMessages.value + ChatMessage((System.currentTimeMillis() + 1).toString(), response, false)
            _isProcessing.value = false
        }
    }

    private suspend fun getActiveKeys(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesManager(androidAppContext)
            val keys = prefs.getAPIKeys()
            keys.filter { it.isActive }.map { 
                val baseUrl = it.baseUrl ?: when (it.provider) {
                    AIProvider.GAPGPT -> "https://api.gapgpt.app/v1"
                    AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                    AIProvider.OPENAI -> "https://api.openai.com/v1"
                    else -> "https://api.openai.com/v1"
                }
                Pair(baseUrl, it.key)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getPreferredModelForProvider(baseUrl: String): String {
        return when {
            baseUrl.contains("gapgpt.app") -> "gpt-4o-mini"
            baseUrl.contains("liara.ir") -> "openai/gpt-4o-mini"
            baseUrl.contains("openai.com") -> "gpt-3.5-turbo"
            else -> "gpt-3.5-turbo"
        }
    }

    private suspend fun callGapgptAI(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val keys = getActiveKeys()
            // Priority: GAPGPT first
            val gapgptKey = keys.firstOrNull { it.first.contains("gapgpt.app") } 
                ?: keys.firstOrNull { !it.first.contains("liara.ir") }
                ?: keys.firstOrNull()
                ?: return@withContext null

            val url = URL("${gapgptKey.first}/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${gapgptKey.second}")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val model = getPreferredModelForProvider(gapgptKey.first)

            // Get financial context
            val balance = accountingManager.getBalance()
            val totalIncome = accountingManager.getTotalIncome()
            val totalExpense = accountingManager.getTotalExpense()
            val monthlyIncome = accountingManager.getMonthlyIncome()
            val monthlyExpense = accountingManager.getMonthlyExpense()
            val activeReminders = reminderManager.getActiveRemindersList()
            val uncashedChecks = accountingManager.getUncashedChecks()
            val activeInstallments = accountingManager.getActiveInstallments()

            val systemPrompt = """
                شما یک دستیار هوشمند مالی و شخصی به نام "مالیار" هستید.
                اطلاعات کاربر:
                - تراز کل: ${String.format("%,.0f", balance)} تومان
                - کل درآمد: ${String.format("%,.0f", totalIncome)} تومان
                - کل هزینه: ${String.format("%,.0f", totalExpense)} تومان
                - درآمد این ماه: ${String.format("%,.0f", monthlyIncome)} تومان
                - هزینه این ماه: ${String.format("%,.0f", monthlyExpense)} تومان
                - یادآوری‌های فعال: ${activeReminders.size} عدد
                - چک‌های وصول نشده: ${uncashedChecks.size} عدد
                - اقساط فعال: ${activeInstallments.size} عدد
                
                شما می‌توانید به سوالات مالی، برنامه‌ریزی، یادآوری و مشاوره پاسخ دهید.
                لطفاً به زبان فارسی پاسخ دهید.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("max_tokens", 500)
                put("temperature", 0.7)
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    return@withContext choice.getJSONObject("message").getString("content").trim()
                }
            } else {
                val reader = BufferedReader(InputStreamReader(connection.errorStream))
                val error = reader.readText()
                reader.close()
                android.util.Log.e("AssistantVM", "GAPGPT API error: $error")
            }
        } catch (e: Exception) {
            android.util.Log.e("AssistantVM", "Error calling GAPGPT AI", e)
        }
        return@withContext null
    }

    private suspend fun callLiaraAI(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val keys = getActiveKeys()
            val liaraKey = keys.firstOrNull { it.first.contains("liara.ir") } ?: return@withContext null

            val url = URL("${liaraKey.first}/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${liaraKey.second}")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val requestBody = JSONObject().apply {
                put("model", "openai/gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "شما یک دستیار هوشمند مالی و شخصی به نام مالیار هستید. به فارسی پاسخ دهید.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("max_tokens", 500)
                put("temperature", 0.7)
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    return@withContext choice.getJSONObject("message").getString("content").trim()
                }
            } else {
                val reader = BufferedReader(InputStreamReader(connection.errorStream))
                val error = reader.readText()
                reader.close()
                android.util.Log.e("AssistantVM", "Liara API error: $error")
            }
        } catch (e: Exception) {
            android.util.Log.e("AssistantVM", "Error calling Liara AI", e)
        }
        return@withContext null
    }

    private suspend fun processCommand(message: String): String {
        val lower = message.lowercase()
        
        // Call contact by name: "تماس با مامان" or "call mom"
        if (lower.contains("تماس") || lower.contains("call") || lower.contains("زنگ بزن")) {
            val contactName = message.substringAfter("تماس").trim()
                .substringAfter("زنگ بزن").trim()
                .substringAfter("call").trim()
            if (contactName.isNotEmpty()) {
                try {
                    val contactManager = ContactManager(androidAppContext)
                    val contacts = contactManager.getAllContactsList()
                    val matched = contacts.firstOrNull { 
                        it.name.contains(contactName, ignoreCase = true) || 
                        contactName.contains(it.name, ignoreCase = true)
                    }
                    if (matched != null && matched.phone.isNotEmpty()) {
                        val success = VoiceCallHelper.makeCall(androidAppContext, matched.phone)
                        return if (success) "📞 در حال برقراری تماس با ${matched.name}..."
                        else "❌ خطا در برقراری تماس"
                    } else {
                        val allNames = contacts.joinToString("، ") { it.name }
                        return "⚠️ مخاطب '$contactName' پیدا نشد. مخاطبین شما: $allNames"
                    }
                } catch (e: Exception) {
                    return "❌ خطا در دسترسی به مخاطبین: ${e.message}"
                }
            }
        }

        // Add income: "افزودن درآمد 1000000 حقوق"
        if (lower.contains("افزودن درآمد") || lower.contains("add income")) {
            val parts = message.split(" ")
            val amount = parts.find { it.toDoubleOrNull() != null }?.toDoubleOrNull() ?: 0.0
            val category = parts.lastOrNull { it.toDoubleOrNull() == null } ?: "عمومی"
            if (amount > 0) {
                accountingManager.addIncome(Income(category = category, amount = amount, description = message, date = Date().time))
                return "✅ درآمد ${String.format("%,.0f", amount)} تومان با دسته‌بندی $category اضافه شد"
            }
            return "⚠️ لطفاً مبلغ را مشخص کنید"
        }

        // Add expense: "افزودن هزینه 500000 خرید"
        if (lower.contains("افزودن هزینه") || lower.contains("add expense")) {
            val parts = message.split(" ")
            val amount = parts.find { it.toDoubleOrNull() != null }?.toDoubleOrNull() ?: 0.0
            val category = parts.lastOrNull { it.toDoubleOrNull() == null } ?: "عمومی"
            if (amount > 0) {
                accountingManager.addExpense(Expense(category = category, amount = amount, description = message, date = Date().time))
                return "✅ هزینه ${String.format("%,.0f", amount)} تومان با دسته‌بندی $category اضافه شد"
            }
            return "⚠️ لطفاً مبلغ را مشخص کنید"
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
                return "✅ یادآوری '$title' برای فردا اضافه شد"
            }
            return "⚠️ لطفاً عنوان یادآوری را مشخص کنید"
        }

        return when {
            lower.contains("تراز") || lower.contains("balance") || lower.contains("موجودی") -> {
                val balance = accountingManager.getBalance()
                "💰 تراز فعلی شما: ${String.format("%,.0f", balance)} تومان"
            }
            lower.contains("درآمد") || lower.contains("income") -> {
                val income = accountingManager.getTotalIncome()
                val monthlyIncome = accountingManager.getMonthlyIncome()
                "📈 کل درآمد: ${String.format("%,.0f", income)} تومان\n📊 درآمد این ماه: ${String.format("%,.0f", monthlyIncome)} تومان"
            }
            lower.contains("هزینه") || lower.contains("expense") || lower.contains("خرج") -> {
                val expense = accountingManager.getTotalExpense()
                val monthlyExpense = accountingManager.getMonthlyExpense()
                "📉 کل هزینه: ${String.format("%,.0f", expense)} تومان\n📊 هزینه این ماه: ${String.format("%,.0f", monthlyExpense)} تومان"
            }
            lower.contains("دارایی") || lower.contains("assets") -> {
                val assets = financialManager.getTotalAssets()
                "🏠 کل دارایی‌ها: ${String.format("%,.0f", assets)} تومان"
            }
            lower.contains("بدهی") || lower.contains("debt") || lower.contains("بدهکار") -> {
                val debts = financialManager.getTotalUnpaidDebts()
                "💳 کل بدهی‌ها: ${String.format("%,.0f", debts)} تومان"
            }
            lower.contains("خالص") || lower.contains("net") || lower.contains("ثروت") -> {
                val assets = financialManager.getTotalAssets()
                val debts = financialManager.getTotalUnpaidDebts()
                val netWorth = assets - debts
                "💎 خالص دارایی: ${String.format("%,.0f", netWorth)} تومان"
            }
            lower.contains("یادآوری") || lower.contains("reminder") -> {
                val reminders = reminderManager.getActiveRemindersList()
                if (reminders.isEmpty()) "📝 شما یادآوری فعالی ندارید"
                else "🔔 شما ${reminders.size} یادآوری فعال دارید:\n${reminders.take(5).joinToString("\n") { "• ${it.title}" }}"
            }
            lower.contains("چک") || lower.contains("check") -> {
                val checks = accountingManager.getUncashedChecks()
                if (checks.isEmpty()) "✅ هیچ چک وصول نشده‌ای ندارید"
                else "📋 شما ${checks.size} چک وصول نشده دارید"
            }
            lower.contains("قسط") || lower.contains("installment") -> {
                val installments = accountingManager.getActiveInstallments()
                if (installments.isEmpty()) "✅ هیچ قسط فعالی ندارید"
                else "💳 شما ${installments.size} قسط فعال دارید"
            }
            lower.contains("تحلیل") || lower.contains("analysis") || lower.contains("گزارش") -> {
                val income = accountingManager.getMonthlyIncome()
                val expense = accountingManager.getMonthlyExpense()
                val balance = accountingManager.getBalance()
                val ratio = if (income > 0) String.format("%.1f%%", (expense / income) * 100) else "0%"
                """
📊 تحلیل مالی شما:
📈 درآمد این ماه: ${String.format("%,.0f", income)} تومان
📉 هزینه این ماه: ${String.format("%,.0f", expense)} تومان
💰 تراز کل: ${String.format("%,.0f", balance)} تومان
📊 نسبت هزینه به درآمد: $ratio
${if (ratio.toDoubleOrNull() ?: 0.0 > 80.0) "⚠️ هشدار: هزینه‌های شما بالاست!" else "✅ وضعیت مالی شما مناسب است"}
                """.trimIndent()
            }
            lower.contains("سلام") || lower.contains("hi") || lower.contains("hello") -> {
                "👋 سلام! من دستیار هوشمند مالیار هستم.\nمی‌توانم در موارد زیر به شما کمک کنم:\n• 📞 تماس با مخاطبین\n• 💰 بررسی تراز و موجودی\n• 📊 تحلیل مالی\n• ➕ ثبت درآمد و هزینه\n• 🔔 مدیریت یادآوری‌ها\n• 📋 بررسی چک‌ها و اقساط\n• 💡 مشاوره مالی"
            }
            else -> """
🤖 دستیار هوشمند مالیار
دستورات:
━━━━━━━━━━━━━━━
📞 تماس با [نام] / call [name]
💰 تراز / موجودی / درآمد / هزینه
📊 تحلیل / گزارش مالی
➕ افزودن درآمد [مبلغ] [دسته]
➖ افزودن هزینه [مبلغ] [دسته]
🔔 افزودن یادآوری [عنوان]
📋 چک / قسط / یادآوری
🏠 دارایی / بدهی / خالص
            """.trimIndent()
        }
    }

    companion object {
        private lateinit var androidAppContext: android.content.Context
        
        fun init(context: android.content.Context) {
            androidAppContext = context.applicationContext
        }
    }
}