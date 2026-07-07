package com.maliar.pro.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.BankAccountManager
import com.maliar.pro.database.Expense
import com.maliar.pro.database.Income
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.BankSmsParser
import com.maliar.pro.utils.ParsedBankSms
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Opt-in only (see PreferencesManager.isSmsReadingEnabled) - reads incoming SMS, and for
 * anything that looks like a bank transaction notification, records it as a normal,
 * editable Income/Expense row (see BankSmsParser) and updates that bank's tracked balance.
 * Messages that are definitely not bank-related are dropped locally. Ambiguous bank-like
 * messages can be sent to the configured AI model for JSON-only classification.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!PreferencesManager(context).isSmsReadingEnabled()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val appContext = context.applicationContext

        for (message in messages) {
            val sender = message.originatingAddress ?: ""
            val body = message.messageBody ?: ""
            val timestamp = message.timestampMillis
            val analysis = BankSmsParser.analyze(sender, body)

            if (analysis.definitelyNotBank) continue

            val smsId = hashOf("$sender|$body|$timestamp")

            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val bankAccountManager = BankAccountManager(appContext)
                    if (bankAccountManager.isSmsAlreadyProcessed(smsId)) return@launch

                    val localParsed = BankSmsParser.parse(sender, body)
                    val parsed = localParsed ?: askOnlineBankSmsParser(appContext, sender, body)
                    if (parsed == null) {
                        bankAccountManager.markSmsProcessed(smsId)
                        return@launch
                    }
                    val accountingManager = AccountingManager(appContext)

                    if (parsed.isDeposit) {
                        accountingManager.addIncome(
                            Income(amount = parsed.amountToman, description = parsed.suggestedTitle, date = timestamp)
                        )
                    } else {
                        accountingManager.addExpense(
                            Expense(amount = parsed.amountToman, description = parsed.suggestedTitle, date = timestamp)
                        )
                    }

                    if (parsed.balanceToman != null) {
                        bankAccountManager.upsertBalance(parsed.bankName, parsed.lastDigits, parsed.balanceToman)
                    }

                    bankAccountManager.markSmsProcessed(smsId)
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Failed to process a bank SMS", e)
                }
            }
        }
    }

    private fun hashOf(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun askOnlineBankSmsParser(context: Context, sender: String, body: String): ParsedBankSms? {
        val systemPrompt = """
            You classify Iranian bank SMS messages for an accounting app.
            Local rules were unsure, so inspect only this SMS.
            Return JSON only, no markdown.
            Reject OTP-only, ads, login codes, internet packages, and messages that do not report a completed money transaction.
            Amounts must be in Toman. If the SMS says Rial, divide by 10. If no unit is shown, assume Toman for Iranian banking short messages.
            For signed amounts, + means deposit/income and - means withdrawal/expense.
            JSON schema:
            {
              "isBankSms": true|false,
              "confidence": 0.0-1.0,
              "isDeposit": true|false,
              "amountToman": number,
              "balanceToman": number|null,
              "bankName": "string",
              "lastDigits": "string",
              "suggestedTitle": "string"
            }
        """.trimIndent()
        val userPrompt = "sender:\n$sender\n\nsms:\n$body"

        val raw = AIHelper.generateText(context, systemPrompt, userPrompt) ?: return null
        val jsonText = raw.substringAfter("{", "").substringBeforeLast("}", "").takeIf { it.isNotBlank() }
            ?.let { "{$it}" }
            ?: return null

        return try {
            val json = JSONObject(jsonText)
            if (!json.optBoolean("isBankSms", false)) return null
            if (json.optDouble("confidence", 0.0) < 0.70) return null

            val amount = json.optDouble("amountToman", 0.0)
            if (amount <= 0.0) return null

            val bankName = json.optString("bankName").ifBlank { "بانک نامشخص" }
            val isDeposit = json.optBoolean("isDeposit", true)
            ParsedBankSms(
                isDeposit = isDeposit,
                amountToman = amount,
                balanceToman = if (json.isNull("balanceToman")) null else json.optDouble("balanceToman"),
                bankName = bankName,
                lastDigits = json.optString("lastDigits").filter { it.isDigit() }.takeLast(4),
                suggestedTitle = json.optString("suggestedTitle").ifBlank {
                    if (isDeposit) "واریز - $bankName" else "برداشت/خرید - $bankName"
                }
            )
        } catch (e: Exception) {
            Log.w("SmsReceiver", "Online bank SMS parser returned invalid JSON: $raw", e)
            null
        }
    }
}
