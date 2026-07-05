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

    private val smartReminderManager by lazy { com.maliar.pro.database.SmartReminderManager(appContext) }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _chatMessages.value = _chatMessages.value + ChatMessage(System.currentTimeMillis().toString(), message, true)

            // Deterministic local execution FIRST: if this message is a recognizable
            // accounting or reminder command, actually perform it against the real
            // database/AlarmManager right here. Previously the AI model would just
            // *claim* in its chat reply that it "recorded the income", because the
            // online chat model has no way to call app functions - it was only ever
            // describing data already in the system prompt, never writing anything.
            val localActionResult = try {
                tryExecuteAccountingCommand(message)
                    ?: tryExecuteReminderCommand(message)
                    ?: tryExecuteFinancialStatusCommand(message)
            } catch (e: Exception) {
                // Swallowing this silently used to mean any real bug here (a DB error, a
                // threading issue, anything) looked identical to "not a command" - the
                // message would fall through to the online chat model, which would then
                // describe success in a reply while nothing was actually written anywhere.
                // Logging it doesn't change the fallback behavior, but makes that failure
                // mode visible in logcat instead of invisible.
                android.util.Log.e("AssistantVM", "Local command execution failed for: $message", e)
                null
            }

            if (localActionResult != null) {
                _chatMessages.value = _chatMessages.value + ChatMessage((System.currentTimeMillis() + 1).toString(), localActionResult, false)
                _isProcessing.value = false
                return@launch
            }

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

    /** Converts Persian/Arabic-Indic digits in a string to plain ASCII digits. */
    private fun normalizeDigits(text: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        var result = text
        persian.forEachIndexed { i, ch -> result = result.replace(ch, ('0' + i)) }
        arabic.forEachIndexed { i, ch -> result = result.replace(ch, ('0' + i)) }
        return result
    }

    /** Parses amounts like "500 هزار", "2 میلیون", "500000", "5,000,000 تومان". */
    private fun parsePersianAmount(text: String): Double? {
        val normalized = normalizeDigits(text)
        val regex = Regex("([0-9][0-9,.]*)\\s*(میلیون|هزار)?")
        val match = regex.findAll(normalized).firstOrNull { it.groupValues[1].isNotBlank() }
        if (match != null) {
            val digitAmount = match.groupValues[1].replace(",", "").toDoubleOrNull()
            if (digitAmount != null) {
                return when (match.groupValues[2]) {
                    "هزار" -> digitAmount * 1000
                    "میلیون" -> digitAmount * 1_000_000
                    else -> digitAmount
                }
            }
        }
        // No digits in the message at all - a dictated voice command very often comes
        // through with the amount spoken as words ("پنجاه هزار تومان") rather than digits,
        // and that used to mean the amount silently failed to parse, the local accounting
        // handler returned null, and the online chat model took over and just *claimed*
        // the amount was recorded without anything real being saved.
        return parsePersianWordAmount(text)
    }

    /**
     * Parses a Persian number spelled out in words, e.g. "پنجاه هزار", "دویست و ده هزار",
     * "دو میلیون و پانصد هزار", "نیم میلیون". Stops at the first word that isn't part of a
     * number so it doesn't accidentally pick up unrelated numbers later in the sentence.
     */
    private fun parsePersianWordAmount(text: String): Double? {
        val tokens = text.replace("،", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        var total = 0.0
        var current = 0.0
        var matchedAny = false
        for (token in tokens) {
            when {
                token == "و" -> continue
                token == "نیم" -> {
                    current = 0.5
                    matchedAny = true
                }
                PERSIAN_NUMBER_WORDS.containsKey(token) -> {
                    current += PERSIAN_NUMBER_WORDS.getValue(token)
                    matchedAny = true
                }
                PERSIAN_NUMBER_MULTIPLIERS.containsKey(token) -> {
                    val multiplier = PERSIAN_NUMBER_MULTIPLIERS.getValue(token)
                    total += (if (current == 0.0) 1.0 else current) * multiplier
                    current = 0.0
                    matchedAny = true
                }
                matchedAny -> break
            }
        }
        total += current
        return if (matchedAny && total > 0) total else null
    }

    /**
     * Detects an income/expense recording command and, if found, actually writes it to
     * the accounting database and returns a confirmation with the real updated balance.
     * Returns null (no side effect) if the message isn't a recognizable command, so the
     * caller can fall through to normal AI chat / queries.
     */
    private suspend fun tryExecuteAccountingCommand(message: String): String? {
        val incomeKeywords = listOf("درآمد", "حقوق", "دریافت کردم", "دریافتی", "واریز شد", "واریز کردم", "فروش")
        val expenseKeywords = listOf("هزینه", "خرج کردم", "خرج شد", "پرداخت کردم", "خریدم", "خرید کردم", "پرداختی")

        val isIncome = incomeKeywords.any { message.contains(it) }
        val isExpense = expenseKeywords.any { message.contains(it) }

        // Ambiguous (matches both, or neither) - this doesn't look like a single, clear
        // accounting command, so let it fall through to normal AI chat / queries.
        if (isIncome == isExpense) return null

        val amount = parsePersianAmount(message)
        if (amount == null || amount <= 0) {
            // We're confident this IS an accounting command (a clear income/expense
            // keyword matched) but couldn't read a numeric amount from it. Stopping here
            // with an explicit ask - instead of returning null and letting the online chat
            // model take over - is what prevents the "دستیار گفت ثبت شد ولی در حسابداری
            // چیزی ثبت نشده بود" symptom: the chat model has no way to actually write to
            // the database, so if it answers at all here it can only ever be describing a
            // record that was never really saved.
            val kind = if (isIncome) "درآمد" else "هزینه"
            return "⚠️ متوجه شدم می‌خواهید یک $kind ثبت کنید، اما مبلغ آن را متوجه نشدم.\n" +
                "لطفاً مبلغ را واضح‌تر بگویید، مثلاً «۵۰ هزار تومان $kind» یا «پانصد هزار تومان $kind»."
        }

        val description = message.trim()
        val formattedAmount = String.format("%,.0f", amount)

        return if (isIncome) {
            accountingManager.addIncome(Income(amount = amount, description = description, date = Date().time))
            val newBalance = accountingManager.getBalance()
            "✅ مبلغ $formattedAmount تومان به‌عنوان درآمد در حسابداری ثبت شد.\n💰 موجودی جدید: ${String.format("%,.0f", newBalance)} تومان"
        } else {
            accountingManager.addExpense(Expense(amount = amount, description = description, date = Date().time))
            val newBalance = accountingManager.getBalance()
            "✅ مبلغ $formattedAmount تومان به‌عنوان هزینه در حسابداری ثبت شد.\n💰 موجودی جدید: ${String.format("%,.0f", newBalance)} تومان"
        }
    }

    /**
     * Detects a "وضعیت مالی" (Financial Status tab) command - adding an asset, a debt, or a
     * financial goal - and actually writes it via FinancialStatusManager. Without this, a
     * request like "یک دارایی ۵۰ میلیونی ثبت کن" had no matching handler at all and silently
     * fell through to the online chat model, which would just *say* "ثبت شد" in its reply
     * without anything real being saved - exactly the "writes a reply but nothing shows up
     * in the other tab" symptom.
     */
    private suspend fun tryExecuteFinancialStatusCommand(message: String): String? {
        val amount = parsePersianAmount(message) ?: return null
        if (amount <= 0) return null

        val assetKeywords = listOf("دارایی")
        val debtKeywords = listOf("بدهی", "بدهکارم", "بدهکار شدم", "بدهکار هستم")
        val goalKeywords = listOf("هدف مالی", "هدف پس‌انداز", "هدف پس انداز", "هدف")

        val isAsset = assetKeywords.any { message.contains(it) }
        val isDebt = debtKeywords.any { message.contains(it) }
        val isGoal = goalKeywords.any { message.contains(it) }

        // Ambiguous or no match at all - don't guess, let it fall through.
        val matchCount = listOf(isAsset, isDebt, isGoal).count { it }
        if (matchCount != 1) return null

        val title = message.trim()
        val formattedAmount = String.format("%,.0f", amount)

        return when {
            isAsset -> {
                financialManager.addAsset(title, amount)
                val total = financialManager.getTotalAssets()
                "✅ دارایی به مبلغ $formattedAmount تومان در «وضعیت مالی» ثبت شد.\n📊 کل دارایی‌ها اکنون: ${String.format("%,.0f", total)} تومان"
            }
            isDebt -> {
                financialManager.addDebt(title, amount)
                val total = financialManager.getTotalUnpaidDebts()
                "✅ بدهی به مبلغ $formattedAmount تومان در «وضعیت مالی» ثبت شد.\n📊 کل بدهی‌های پرداخت‌نشده اکنون: ${String.format("%,.0f", total)} تومان"
            }
            isGoal -> {
                financialManager.addFinancialGoal(title, amount)
                "✅ هدف مالی «$title» با مبلغ هدف $formattedAmount تومان در «وضعیت مالی» ثبت شد."
            }
            else -> null
        }
    }

    /**
     * Detects a natural-language reminder request ("یادم بنداز فردا ساعت ۵ ...") and, if the
     * day/time can be parsed, actually schedules it with SmartReminderManager (real AlarmManager
     * alarm, not just a chat reply). Returns null if this doesn't look like a reminder request
     * or the time couldn't be confidently parsed.
     */
    private suspend fun tryExecuteReminderCommand(message: String): String? {
        val reminderTriggers = listOf(
            "یادم بنداز", "یادآوری کن", "بهم یادآوری کن", "یاداوری کن",
            "یادآوری بذار", "یادآوری بگذار", "یه یادآوری", "یک یادآوری",
            "برام یادآوری", "بهم بگو"
        )
        if (reminderTriggers.none { message.contains(it) }) return null

        val normalized = normalizeDigits(message)

        // 1) Absolute time: "ساعت 5", "ساعت 5:30"
        val hourMatch = Regex("ساعت\\s*([0-9]{1,2})(?::([0-9]{2}))?").find(normalized)
        // 2) Relative time: "نیم ساعت دیگه", "20 دقیقه دیگه", "2 ساعت دیگه/بعد"
        val relMinutesMatch = Regex("([0-9]{1,3})\\s*دقیقه\\s*(دیگه|دیگر|بعد)").find(normalized)
        val relHoursMatch = Regex("([0-9]{1,2})\\s*ساعت\\s*(دیگه|دیگر|بعد)").find(normalized)
        val halfHourRelative = Regex("نیم\\s*ساعت\\s*(دیگه|دیگر|بعد)").containsMatchIn(normalized)

        val cal = java.util.Calendar.getInstance()
        var matchedSpan: String? = null

        when {
            hourMatch != null -> {
                var hour = hourMatch.groupValues[1].toIntOrNull() ?: return clarifyReminder(message)
                var minute = hourMatch.groupValues[2].toIntOrNull() ?: 0
                if (normalized.contains("و نیم")) minute = 30
                if (hour !in 0..23) return clarifyReminder(message)

                when {
                    message.contains("پس فردا") || message.contains("پس‌فردا") -> cal.add(java.util.Calendar.DAY_OF_MONTH, 2)
                    message.contains("فردا") -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    // "امروز" or no day keyword: keep today, but if that time already passed, assume tomorrow
                }
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                cal.set(java.util.Calendar.MINUTE, minute)
                cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                matchedSpan = hourMatch.value
            }
            relMinutesMatch != null -> {
                val mins = relMinutesMatch.groupValues[1].toIntOrNull() ?: return clarifyReminder(message)
                cal.add(java.util.Calendar.MINUTE, mins)
                matchedSpan = relMinutesMatch.value
            }
            halfHourRelative -> {
                cal.add(java.util.Calendar.MINUTE, 30)
                matchedSpan = Regex("نیم\\s*ساعت\\s*(دیگه|دیگر|بعد)").find(normalized)?.value
            }
            relHoursMatch != null -> {
                val hrs = relHoursMatch.groupValues[1].toIntOrNull() ?: return clarifyReminder(message)
                cal.add(java.util.Calendar.HOUR_OF_DAY, hrs)
                matchedSpan = relHoursMatch.value
            }
            else -> {
                // A reminder was clearly requested, but no time could be understood.
                // Don't silently fall through to the AI chat reply here, since it would
                // just describe the reminder in text without anything actually being
                // saved/scheduled - which is exactly the "writes it but doesn't really
                // register it" problem. Ask for a clear time instead.
                return clarifyReminder(message)
            }
        }

        var title = message
        (reminderTriggers + listOf("امروز", "فردا", "پس فردا", "پس‌فردا")).forEach { title = title.replace(it, "") }
        matchedSpan?.let { title = title.replace(it, "") }
        title = title.replace("و نیم", "").trim(' ', ',', '،', ':')
        if (title.isBlank()) title = "یادآوری"

        // If the reminder mentions calling someone ("با علی تماس بگیرم"), try to match it
        // against the app's own Contacts so the "action" notification mode can offer a
        // real one-tap call button - not just a text reminder that says to call someone.
        var matchedContactName = ""
        var matchedContactPhone = ""
        val callMention = Regex("با\\s+([\\p{L}\\s]{2,20}?)\\s*تماس").find(message)
            ?: Regex("([\\p{L}\\s]{2,20}?)\\s*(?:رو|را)?\\s*(?:صدا بزن|زنگ بزن)").find(message)
        if (callMention != null) {
            val nameGuess = callMention.groupValues[1].trim()
            if (nameGuess.isNotBlank()) {
                try {
                    val contacts = ContactManager(appContext).getAllContactsList()
                    val matched = contacts.firstOrNull {
                        it.name.contains(nameGuess, ignoreCase = true) || nameGuess.contains(it.name, ignoreCase = true)
                    }
                    if (matched != null) {
                        matchedContactName = matched.name
                        matchedContactPhone = matched.phoneNumber
                    }
                } catch (e: Exception) { /* no contact match, ignore */ }
            }
        }

        // If the person explicitly asked for a "smart" reminder, schedule it as one so it
        // gets the spoken TTS treatment instead of a plain notification.
        val requestedAlertType = if (message.contains("هوشمند")) {
            com.maliar.pro.database.AlertType.SMART.name
        } else {
            com.maliar.pro.database.AlertType.NOTIFICATION.name
        }

        val reminder = com.maliar.pro.database.ReminderEntity(
            title = title,
            description = "",
            reminderType = com.maliar.pro.database.ReminderType.SIMPLE.name,
            priority = com.maliar.pro.database.Priority.MEDIUM.name,
            alertType = requestedAlertType,
            triggerTime = cal.timeInMillis,
            repeatPattern = com.maliar.pro.database.RepeatPattern.ONCE.name,
            category = "دستیار",
            contactName = matchedContactName,
            contactPhoneNumber = matchedContactPhone
        )
        val savedId = smartReminderManager.addReminder(reminder)

        val timeStr = String.format(
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
        return if (savedId > 0) {
            val contactNote = if (matchedContactPhone.isNotBlank())
                "\n📞 در حالت نوتیفیکیشن «با اکشن»، دکمه تماس مستقیم با $matchedContactName هم نمایش داده می‌شود."
            else ""
            "✅ یادآوری «$title» برای ساعت $timeStr ثبت و زمان‌بندی شد (شناسه #$savedId).$contactNote"
        } else {
            "❌ ذخیره‌سازی یادآوری با خطا مواجه شد، لطفاً دوباره تلاش کنید."
        }
    }

    /**
     * Returned when a reminder was clearly requested but the time couldn't be confidently
     * parsed, so nothing was saved. Being explicit about this (instead of quietly falling
     * through to a generic AI reply that might *sound* like it registered something) is
     * what prevents the "assistant writes a reply but nothing was actually saved" issue.
     */
    private fun clarifyReminder(message: String): String {
        return "⚠️ متوجه شدم می‌خواهید یادآوری تنظیم کنید، اما نتوانستم زمان دقیق آن را تشخیص دهم.\n" +
            "لطفاً با یکی از این فرمت‌ها دوباره بنویسید:\n" +
            "• «یادم بنداز فردا ساعت 5 قرص بخورم»\n" +
            "• «یادآوری کن نیم ساعت دیگه با علی تماس بگیرم»\n" +
            "• «یادآوری کن 20 دقیقه دیگه ...»"
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

            // Pull data from the "Financial Status" tab too (assets, debts, goals) - not
            // just accounting/reminders - so an analysis/summary request the user makes in
            // the assistant tab can actually see across all tabs instead of only two of them.
            val totalAssets = financialManager.getTotalAssets()
            val totalDebts = financialManager.getTotalUnpaidDebts()
            val activeGoals = financialManager.getActiveGoals()

            val systemPrompt = """
                شما یک دستیار هوشمند مالی و شخصی به نام "مالیار" هستید و به اطلاعات همه بخش‌های برنامه (حسابداری، یادآوری‌ها، وضعیت مالی) دسترسی دارید.
                اطلاعات کاربر:
                - تراز کل: ${String.format("%,.0f", balance)} تومان
                - کل درآمد: ${String.format("%,.0f", totalIncome)} تومان
                - کل هزینه: ${String.format("%,.0f", totalExpense)} تومان
                - درآمد این ماه: ${String.format("%,.0f", monthlyIncome)} تومان
                - هزینه این ماه: ${String.format("%,.0f", monthlyExpense)} تومان
                - یادآوری‌های فعال: ${activeReminders.size} عدد
                - چک‌های وصول نشده: ${uncashedChecks.size} عدد
                - اقساط فعال: ${activeInstallments.size} عدد
                - کل دارایی‌ها (وضعیت مالی): ${String.format("%,.0f", totalAssets)} تومان
                - کل بدهی‌های پرداخت‌نشده (وضعیت مالی): ${String.format("%,.0f", totalDebts)} تومان
                - اهداف مالی فعال: ${activeGoals.size} عدد${if (activeGoals.isNotEmpty()) " (" + activeGoals.joinToString("، ") { it.title } + ")" else ""}
                
                شما می‌توانید به سوالات مالی، برنامه‌ریزی، یادآوری و مشاوره پاسخ دهید و در صورت درخواست تحلیل یا خلاصه وضعیت، از اطلاعات همه بخش‌های بالا استفاده کنید.
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

            // Same cross-tab context as the GAPGPT path, so Liara answers are equally
            // aware of accounting, reminders, and financial-status data instead of
            // falling back to a context-free generic prompt.
            val balance = accountingManager.getBalance()
            val totalIncome = accountingManager.getTotalIncome()
            val totalExpense = accountingManager.getTotalExpense()
            val activeReminders = reminderManager.getActiveRemindersList()
            val totalAssets = financialManager.getTotalAssets()
            val totalDebts = financialManager.getTotalUnpaidDebts()
            val activeGoals = financialManager.getActiveGoals()

            val systemPrompt = """
                شما یک دستیار هوشمند مالی و شخصی به نام "مالیار" هستید و به اطلاعات همه بخش‌های برنامه (حسابداری، یادآوری‌ها، وضعیت مالی) دسترسی دارید.
                اطلاعات کاربر:
                - تراز کل: ${String.format("%,.0f", balance)} تومان
                - کل درآمد: ${String.format("%,.0f", totalIncome)} تومان
                - کل هزینه: ${String.format("%,.0f", totalExpense)} تومان
                - یادآوری‌های فعال: ${activeReminders.size} عدد
                - کل دارایی‌ها (وضعیت مالی): ${String.format("%,.0f", totalAssets)} تومان
                - کل بدهی‌های پرداخت‌نشده (وضعیت مالی): ${String.format("%,.0f", totalDebts)} تومان
                - اهداف مالی فعال: ${activeGoals.size} عدد

                لطفاً به زبان فارسی پاسخ دهید.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "openai/gpt-4o-mini")
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
                        return when (VoiceCallHelper.makeCallWithResult(appContext, matched.phoneNumber)) {
                            VoiceCallHelper.CallResult.CALLED_DIRECTLY -> "📞 در حال برقراری تماس با ${matched.name}..."
                            VoiceCallHelper.CallResult.OPENED_DIALER_NO_PERMISSION ->
                                "📲 مجوز تماس مستقیم داده نشده، برای همین صفحه‌ی شماره‌گیر با شماره‌ی ${matched.name} باز شد؛ فقط کافیه دکمه‌ی تماس را بزنید.\n" +
                                "برای اینکه بعداً دستیار خودش مستقیم تماس بگیرد، از تنظیمات گوشی → برنامه‌ها → مالیار پرو → مجوزها، «تماس تلفنی» را فعال کنید."
                            VoiceCallHelper.CallResult.FAILED -> "❌ خطا در برقراری تماس"
                        }
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

    companion object {
        /** Persian number words used by [parsePersianWordAmount] for dictated amounts. */
        private val PERSIAN_NUMBER_WORDS: Map<String, Double> = mapOf(
            "صفر" to 0.0, "یک" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0,
            "پنج" to 5.0, "شش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0,
            "ده" to 10.0, "یازده" to 11.0, "دوازده" to 12.0, "سیزده" to 13.0,
            "چهارده" to 14.0, "پانزده" to 15.0, "شانزده" to 16.0, "هفده" to 17.0,
            "هجده" to 18.0, "نوزده" to 19.0,
            "بیست" to 20.0, "سی" to 30.0, "چهل" to 40.0, "پنجاه" to 50.0,
            "شصت" to 60.0, "هفتاد" to 70.0, "هشتاد" to 80.0, "نود" to 90.0,
            "صد" to 100.0, "یکصد" to 100.0, "دویست" to 200.0, "سیصد" to 300.0,
            "چهارصد" to 400.0, "پانصد" to 500.0, "ششصد" to 600.0, "هفتصد" to 700.0,
            "هشتصد" to 800.0, "نهصد" to 900.0
        )
        private val PERSIAN_NUMBER_MULTIPLIERS: Map<String, Double> = mapOf(
            "هزار" to 1_000.0, "میلیون" to 1_000_000.0, "میلیارد" to 1_000_000_000.0
        )
    }
}
