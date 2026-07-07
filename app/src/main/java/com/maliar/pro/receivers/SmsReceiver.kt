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
import com.maliar.pro.utils.BankSmsParser
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Opt-in only (see PreferencesManager.isSmsReadingEnabled) - reads incoming SMS, and for
 * anything that looks like a bank transaction notification, records it as a normal,
 * editable Income/Expense row (see BankSmsParser) and updates that bank's tracked balance.
 * Never touches non-bank-looking SMS content beyond checking whether it matches the
 * bank-transaction pattern at all.
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

            if (!BankSmsParser.looksLikeBankSms(body)) continue

            val smsId = hashOf("$sender|$body|$timestamp")

            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val bankAccountManager = BankAccountManager(appContext)
                    if (bankAccountManager.isSmsAlreadyProcessed(smsId)) return@launch

                    val parsed = BankSmsParser.parse(sender, body) ?: return@launch
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
}
