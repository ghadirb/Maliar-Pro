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
    private val appContext: android.content.Context,
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
                var keys = getActiveKeys()
                if (keys.isEmpty()) {
                    // Keys may not have finished auto-provisioning yet (it runs in the
                    // background from Application.onCreate); try once more before giving up
                    // so a slow network doesn't silently look like "the AI doesn't work".
                    com.maliar.pro.utils.AutoProvisioningManager.autoProvision(appContext)
                    keys = getActiveKeys()
                }

                if (keys.isEmpty()) {
                    "⚠️ هیچ کلید API فعالی پیدا نشد، برای همین دستیار آنلاین در دسترس نیست.\n" +
                        "لطفاً اتصال اینترنت را بررسی کنید یا از بخش تنظیمات → کلیدهای هوش مصنوعی، یک کلید معتبر اضافه کنید.\n\n" +
                        processCommand(message)
                } else {
                    val gapgptResponse = callGapgptAI(message)
                    if (gapgptResponse != null) gapgptResponse
                    else {
                        val liaraResponse = callLiaraAI(message)
                        liaraResponse ?: ("⚠️ اتصال به سرویس‌های هوش مصنوعی آنلاین برقرار نشد (شبکه یا کلید نامعتبر است).\n\n" + processCommand(message))
                    }
                }
            } catch (e: Exception) {
                "⚠️ خطا در ارتباط با دستیار آنلاین: ${e.message}\n\n" + processCommand(message)
            }

            _chatMessages.value = _chatMessages.value + ChatMessage((System.currentTimeMillis() + 1).toString(), response, false)
            _isProcessing.value = false
        }
    }

    private suspend fun getActiveKeys(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesManager(appContext)
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
            }
        } catch (e: Exception) {
            android.util.Log.e("AssistantVM", "Error calling Liara AI", e)
        }
        return@withContext null
    }

    private suspend fun processCommand(message: String): String {
        val lower = message.lowercase()
        
        if (lower.contains("تماس") || lower.contains("call") || lower.contains("زنگ بزن")) {
            val contactName = message.substringAfter("تماس").trim()
                .substringAfter("زنگ بزن").trim()
                .substringAfter("call").trim()
            if (contactName.isNotEmpty()) {
                try {
                    val contactManager = ContactManager(appContext)
                    val contacts = contactManager.getAllContactsList()
                    val matched = contacts.firstOrNull { 
                        it.name.contains(contactName, ignoreCase = true) || 
                        contactName.contains(it.name, ignoreCase = true)
                    }
                    if (matched != null && !matched.phoneNumber.isNullOrEmpty()) {
                        val success = VoiceCallHelper.makeCall(appContext, matched.phoneNumber)
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

        // Add other command handling...
        return when {
            lower.contains("تراز") || lower.contains("balance") || lower.contains("موجودی") -> {
                val balance = accountingManager.getBalance()
                "💰 تراز فعلی شما: ${String.format("%,.0f", balance)} تومان"
            }
            else -> "🤖 دستیار هوشمند مالیار آماده است. دستورات را امتحان کنید!"
        }
    }

}
