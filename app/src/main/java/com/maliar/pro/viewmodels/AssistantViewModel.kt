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

    // ... (other methods same as before)

    private suspend fun processCommand(message: String): String {
        val lower = message.lowercase()
        
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
                    if (matched != null && !matched.phoneNumber.isNullOrEmpty()) {
                        val success = VoiceCallHelper.makeCall(androidAppContext, matched.phoneNumber)
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

        // rest of processCommand...
        return "پاسخ پیش‌فرض"
    }

    companion object {
        private lateinit var androidAppContext: android.content.Context
        fun init(context: android.content.Context) {
            androidAppContext = context.applicationContext
        }
    }
}
