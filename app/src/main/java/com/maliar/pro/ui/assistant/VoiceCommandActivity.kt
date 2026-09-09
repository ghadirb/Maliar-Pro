package com.maliar.pro.ui.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maliar.pro.R
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.ContactManager
import com.maliar.pro.database.Expense
import com.maliar.pro.database.ExpenseCategory
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.Priority
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class VoiceCommandActivity : AppCompatActivity() {

    private data class ExpenseResult(
        val amount: Double,
        val description: String,
        val category: String,
        val date: Long
    )

    private data class ReminderResult(
        val title: String,
        val triggerTime: Long
    )

    private var recorder: MediaRecorder? = null
    private var accountingManager: AccountingManager? = null
    private var financialStatusManager: FinancialStatusManager? = null
    private var smartReminderManager: SmartReminderManager? = null
    private var contactManager: ContactManager? = null
    private var outputFile: File? = null
    private var isRecording = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording() else {
            setStatus("برای دستور صوتی، دسترسی میکروفون لازم است.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_command)

        accountingManager = AccountingManager(this)
        financialStatusManager = FinancialStatusManager(this)
        smartReminderManager = SmartReminderManager(this)
        contactManager = ContactManager(this)

        val micButton = findViewById<FloatingActionButton>(R.id.micButton)
        setStatus("برای شروع، دکمه میکروفون را بزنید و بگویید مثلاً «امروز ۵۰ هزار تومن نون خریدم»")

        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<android.view.View>(R.id.closeButton).setOnClickListener { finish() }
    }

    private fun setStatus(text: String) {
        findViewById<TextView>(R.id.voiceStatusText).text = text
    }

    private fun toggleRecording() {
        if (isRecording) stopRecordingAndTranscribe() else startRecording()
    }

    private fun startRecording() {
        try {
            outputFile = File(cacheDir, "voice_command_.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            isRecording = true
            setStatus("🎙️ در حال ضبط… دوباره دکمه را بزنید تا تمام شود")
            findViewById<FloatingActionButton>(R.id.micButton)
                .setImageResource(R.drawable.ic_notification)
        } catch (e: Exception) {
            setStatus("❌ خطا در شروع ضبط صدا: ${e.message ?: "نامشخص"}")
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        findViewById<FloatingActionButton>(R.id.micButton).setImageResource(R.drawable.ic_notification)
        try {
            recorder?.apply { stop(); release() }
        } catch (e: Exception) {
        }
        recorder = null

        val file = outputFile ?: return
        setStatus("⏳ در حال تبدیل صدا به متن…")

        lifecycleScope.launch {
            val transcript = AIHelper.transcribeAudio(this@VoiceCommandActivity, file)
            file.delete()

            if (transcript.isNullOrBlank()) {
                setStatus("❌ متوجه نشدم چی گفتید. دوباره امتحان کنید یا از تب دستیار استفاده کنید.")
                return@launch
            }
            setStatus("📝 متوجه شدم: «${transcript}»")
            handleTranscript(transcript)
        }
    }

    private suspend fun handleTranscript(transcript: String) {
        val lower = transcript.lowercase()
        if (lower.contains("زنگ بزن") || lower.contains("تماس بگیر") || lower.contains("تماس با")) {
            handleCallTranscript(transcript)
            return
        }

        val expenseResults = parseExpenseTranscripts(transcript)
        if (!expenseResults.isNullOrEmpty()) {
            if (expenseResults.size == 1) {
                showExpenseConfirmDialog(expenseResults.first())
            } else {
                showMultiExpenseConfirmDialog(expenseResults)
            }
            return
        }

        val reminderResult = parseReminderTranscript(transcript)
        if (reminderResult != null) {
            showReminderConfirmDialog(reminderResult)
            return
        }

        showFallbackChoices(transcript)
    }

    /**
     * Multi-item aware version (spec: "جملات پیچیده" - e.g. "امروز ۲ میلیون برای سرویس
     * ماشین دادم، یک میلیون تعویض روغن و یک میلیون فیلتر"). Splits the transcript into
     * segments on commas and "و" (and), parses each segment with the same
     * amount+description pattern as before, and returns one [ExpenseResult] per segment
     * that parsed successfully.
     *
     * One special case: when the *first* segment's amount is (within 5%) the sum of all
     * the other segments' amounts, it's treated as a spoken total/summary rather than a
     * separate transaction (as in the example above: "۲ میلیون برای سرویس ماشین" is the
     * total of the "یک میلیون تعویض روغن" + "یک میلیون فیلتر" breakdown that follows it),
     * so only the itemized breakdown becomes real transactions - never both, which would
     * double the amount.
     *
     * Returns null when nothing parseable was found at all; returns a single-item list
     * for an ordinary one-transaction sentence, unchanged from the previous behavior.
     * Nothing here is ever saved directly - every result still goes through the
     * confirmation dialog (single or multi) before any [Expense] is created.
     */
    private fun parseExpenseTranscripts(transcript: String): List<ExpenseResult>? {
        val normalized = normalizeDigits(transcript)
        val now = System.currentTimeMillis()
        val date = when {
            transcript.contains("پریروز") -> now - 2 * 86400000L
            transcript.contains("دیروز") -> now - 86400000L
            else -> now
        }

        val segments = normalized.split(Regex("[،,]|\\sو\\s"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (segments.size < 2) {
            return parseSingleExpenseSegment(normalized, date)?.let { listOf(it) }
        }

        val parsedSegments = segments.mapNotNull { parseSingleExpenseSegment(it, date) }
        if (parsedSegments.size < 2) return parsedSegments.ifEmpty { null }

        val first = parsedSegments.first()
        val rest = parsedSegments.drop(1)
        val restSum = rest.sumOf { it.amount }
        val looksLikeTotalPlusBreakdown = rest.size >= 2 && restSum > 0.0 &&
            kotlin.math.abs(first.amount - restSum) <= first.amount * 0.05

        return if (looksLikeTotalPlusBreakdown) rest else parsedSegments
    }

    private fun parseSingleExpenseSegment(segment: String, date: Long): ExpenseResult? {
        val amountPattern = Regex("(\\d+[\\d,.]*)\\s*(هزار|میلیون|تومان|تومن|ریال)?\\s*(?:برای|بابت|خرج|خرید|پرداخت کردم|پرداخت)?\\s*(.+)")
        val match = amountPattern.find(segment) ?: return null

        val amountStr = match.groupValues[1].replace(",", "").replace(".", "")
        val unit = match.groupValues[2]
        val description = match.groupValues[3].trim()
        if (description.isBlank()) return null

        val baseAmount = amountStr.toDoubleOrNull() ?: return null
        val amount = when {
            unit.contains("میلیون") -> baseAmount * 1_000_000
            unit.contains("هزار") -> baseAmount * 1_000
            else -> baseAmount
        }
        if (amount <= 0.0) return null

        val category = detectCategory(description)
        return ExpenseResult(amount, description, category, date)
    }

    private fun normalizeDigits(text: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        var result = text
        persian.forEachIndexed { index, digit -> result = result.replace(digit, ('0' + index)) }
        arabic.forEachIndexed { index, digit -> result = result.replace(digit, ('0' + index)) }
        return result
    }

    private fun detectCategory(description: String): String {
        val lower = description.lowercase()
        return when {
            // Checked before food/transport so e.g. "تعویض روغن موتور" (a car category
            // phrase that happens to contain "روغن", also a food keyword below) is
            // correctly recognized as "خودرو", not "خوراک" - same category-mixup bug
            // already fixed for the budget screen's own food-matching logic.
            Regex(".*(روغن موتور|تعویض روغن|لاستیک|باتری ماشین|تعمیر ماشین|سرویس ماشین|صافکاری|جلوبندی|بیمه ماشین|بیمه خودرو|کارواش).*").matches(lower) -> ExpenseCategory.CAR
            Regex(".*(بنزین|سوخت|گازوئیل|پارکینگ|اتوبان|تاکسی|اسنپ|اتوبوس|مترو|قطار|پیک).*").matches(lower) -> ExpenseCategory.TRANSPORT
            Regex(".*(نان|برنج|مرغ|گوشت|میوه|سبزی|لبنیات|شیر|ماست|پنیر|روغن|سیب زمینی|گوجه|مواد غذایی|سوپرمارکت|بقالی).*").matches(lower) -> ExpenseCategory.FOOD
            Regex(".*(اجاره|رهن|ودیعه).*").matches(lower) -> ExpenseCategory.HOUSING
            Regex(".*(قبض|برق|گاز|آب|تلفن|شارژ|موبایل|اینترنت).*").matches(lower) -> ExpenseCategory.BILLS
            Regex(".*(دارو|دکتر|بیمارستان|درمان|آزمایش|دندان).*").matches(lower) -> ExpenseCategory.HEALTH
            Regex(".*(سینما|رستوران|کافه|تفریح|گردش|کتاب|فیلم).*").matches(lower) -> ExpenseCategory.ENTERTAINMENT
            Regex(".*(لباس|کفش|پوشاک).*").matches(lower) -> ExpenseCategory.CLOTHING
            else -> ExpenseCategory.OTHER
        }
    }

    private fun parseReminderTranscript(transcript: String): ReminderResult? {
        val triggers = listOf("یادآوری کن", "یادآوری", "یادداشت", "یادم باشه", "یادم بنداز")
        var description = ""
        for (trigger in triggers) {
            val idx = transcript.indexOf(trigger)
            if (idx >= 0) {
                description = transcript.substring(idx + trigger.length).trim()
                break
            }
        }
        if (description.isBlank()) return null

        val timePattern = Regex("ساعت\\s+(\\d{1,2})(?::(\\d{2}))?")
        val timeMatch = timePattern.find(description)
        val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 9
        val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            when {
                transcript.contains("پس فردا") || transcript.contains("بعد فردا") -> add(Calendar.DAY_OF_MONTH, 2)
                transcript.contains("فردا") -> add(Calendar.DAY_OF_MONTH, 1)
                transcript.contains("شنبه") -> setToNextDayOfWeek(Calendar.SUNDAY)
                transcript.contains("یکشنبه") -> setToNextDayOfWeek(Calendar.MONDAY)
                transcript.contains("دوشنبه") -> setToNextDayOfWeek(Calendar.TUESDAY)
                transcript.contains("سه شنبه") || transcript.contains("سه‌شنبه") -> setToNextDayOfWeek(Calendar.WEDNESDAY)
                transcript.contains("چهارشنبه") -> setToNextDayOfWeek(Calendar.THURSDAY)
                transcript.contains("پنج شنبه") || transcript.contains("پنج‌شنبه") -> setToNextDayOfWeek(Calendar.FRIDAY)
                transcript.contains("جمعه") -> setToNextDayOfWeek(Calendar.SATURDAY)
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return ReminderResult(description, cal.timeInMillis)
    }

    private fun Calendar.setToNextDayOfWeek(targetDay: Int) {
        val currentDay = get(Calendar.DAY_OF_WEEK)
        var diff = targetDay - currentDay
        if (diff <= 0) diff += 7
        add(Calendar.DAY_OF_MONTH, diff)
    }

    private fun showExpenseConfirmDialog(result: ExpenseResult) {
        lifecycleScope.launch {
            val accounts = financialStatusManager?.getAllAssetsList().orEmpty()
            val container = LinearLayout(this@VoiceCommandActivity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (20 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, 0)
            }
            fun input(hint: String, value: String, numeric: Boolean = false) = EditText(this@VoiceCommandActivity).apply {
                this.hint = hint
                setText(value)
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                container.addView(this)
            }
            val amountInput = input("مبلغ (تومان)", result.amount.toLong().toString(), true)
            val descriptionInput = input("عنوان یا توضیحات", result.description)
            val categoryInput = android.widget.AutoCompleteTextView(this@VoiceCommandActivity).apply {
                hint = "دسته‌بندی"
                setAdapter(android.widget.ArrayAdapter(this@VoiceCommandActivity, com.maliar.pro.R.layout.item_category_dropdown, android.R.id.text1, ExpenseCategory.ALL))
                setDropDownBackgroundResource(com.maliar.pro.R.drawable.bg_category_dropdown)
                threshold = 0
                setOnClickListener { showDropDown() }
                setText(result.category, false)
                container.addView(this)
            }
            val (year, month, day) = PersianCalendarHelper.gregorianMillisToJalali(result.date)
            val dateInput = input("تاریخ شمسی (مثلاً ۱۴۰۵/۰۶/۱۵)", "$year/$month/$day")
            val accountSpinner = android.widget.Spinner(this@VoiceCommandActivity).apply {
                adapter = android.widget.ArrayAdapter(
                    this@VoiceCommandActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("حساب پیش‌فرض") + accounts.map { it.title }
                )
                container.addView(this)
            }
            AlertDialog.Builder(this@VoiceCommandActivity)
                .setTitle("بررسی و تأیید هزینهٔ صوتی")
                .setMessage("مقادیر پیشنهادی‌اند؛ پیش از ثبت می‌توانید همه را اصلاح کنید.")
                .setView(container)
                .setPositiveButton("ثبت هزینه") { _, _ ->
                    val amount = normalizeDigits(amountInput.text.toString()).replace(",", "").replace("٬", "").toDoubleOrNull()
                    val date = parseJalaliDate(dateInput.text.toString())
                    val description = descriptionInput.text.toString().trim()
                    val category = categoryInput.text.toString().trim()
                    if (amount == null || amount <= 0 || date == null || description.isBlank() || category.isBlank()) {
                        setStatus("⚠️ مبلغ، عنوان، دسته و تاریخ را کامل و صحیح وارد کنید.")
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        accountingManager?.addExpense(Expense(
                            amount = amount,
                            description = description,
                            date = date,
                            category = category,
                            accountId = accountSpinner.selectedItemPosition.takeIf { it > 0 }?.let { accounts[it - 1].id }
                        ))
                        setStatus("✅ هزینه ${amount.toLong()} تومان ثبت شد")
                        findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
                    }
                }
                .setNegativeButton("لغو", null)
                .show()
        }
    }

    /**
     * Multi-item version of [showExpenseConfirmDialog] for a transcript that split into
     * several transactions (spec: "جملات پیچیده"). Amount/description/category stay
     * per-item and independently editable, exactly like the single-item dialog; date and
     * payment account are shared across all items since they all come from the same
     * spoken sentence about the same outing/payment. Nothing is saved until "ثبت همه" -
     * each row is validated the same way the single-item dialog validates its one row,
     * and any row left invalid blocks the whole confirmation rather than silently
     * skipping it.
     */
    private fun showMultiExpenseConfirmDialog(results: List<ExpenseResult>) {
        lifecycleScope.launch {
            val accounts = financialStatusManager?.getAllAssetsList().orEmpty()
            val scroll = android.widget.ScrollView(this@VoiceCommandActivity)
            val container = LinearLayout(this@VoiceCommandActivity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (20 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, 0)
            }
            scroll.addView(container)

            val (year, month, day) = PersianCalendarHelper.gregorianMillisToJalali(results.first().date)
            val dateInput = EditText(this@VoiceCommandActivity).apply {
                hint = "تاریخ شمسی (مثلاً ۱۴۰۵/۰۶/۱۵)"
                setText("$year/$month/$day")
                container.addView(this)
            }

            data class Row(val amount: EditText, val description: EditText, val category: android.widget.AutoCompleteTextView)
            val rows = results.mapIndexed { index, result ->
                TextView(this@VoiceCommandActivity).apply {
                    text = "تراکنش ${index + 1}"
                    setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
                    container.addView(this)
                }
                val amountInput = EditText(this@VoiceCommandActivity).apply {
                    hint = "مبلغ (تومان)"
                    setText(result.amount.toLong().toString())
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    container.addView(this)
                }
                val descriptionInput = EditText(this@VoiceCommandActivity).apply {
                    hint = "عنوان یا توضیحات"
                    setText(result.description)
                    container.addView(this)
                }
                val categoryInput = android.widget.AutoCompleteTextView(this@VoiceCommandActivity).apply {
                    hint = "دسته‌بندی"
                    setAdapter(android.widget.ArrayAdapter(this@VoiceCommandActivity, com.maliar.pro.R.layout.item_category_dropdown, android.R.id.text1, ExpenseCategory.ALL))
                    setDropDownBackgroundResource(com.maliar.pro.R.drawable.bg_category_dropdown)
                    threshold = 0
                    setOnClickListener { showDropDown() }
                    setText(result.category, false)
                    container.addView(this)
                }
                Row(amountInput, descriptionInput, categoryInput)
            }

            val accountSpinner = android.widget.Spinner(this@VoiceCommandActivity).apply {
                adapter = android.widget.ArrayAdapter(
                    this@VoiceCommandActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("حساب پیش‌فرض") + accounts.map { it.title }
                )
                container.addView(this)
            }

            AlertDialog.Builder(this@VoiceCommandActivity)
                .setTitle("بررسی و تأیید ${results.size} هزینهٔ صوتی")
                .setMessage("این جمله چند تراکنش به نظر می‌رسد؛ پیش از ثبت می‌توانید هر مورد را جدا اصلاح یا حذف کنید.")
                .setView(scroll)
                .setPositiveButton("ثبت همه") { _, _ ->
                    val date = parseJalaliDate(dateInput.text.toString())
                    if (date == null) {
                        setStatus("⚠️ تاریخ را به‌صورت صحیح وارد کنید.")
                        return@setPositiveButton
                    }
                    val expenses = rows.map { row ->
                        val amount = normalizeDigits(row.amount.text.toString()).replace(",", "").replace("٬", "").toDoubleOrNull()
                        val description = row.description.text.toString().trim()
                        val category = row.category.text.toString().trim()
                        Triple(amount, description, category)
                    }
                    if (expenses.any { (amount, description, category) -> amount == null || amount <= 0 || description.isBlank() || category.isBlank() }) {
                        setStatus("⚠️ مبلغ، عنوان و دسته همهٔ تراکنش‌ها را کامل و صحیح وارد کنید.")
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        val accountId = accountSpinner.selectedItemPosition.takeIf { it > 0 }?.let { accounts[it - 1].id }
                        var total = 0.0
                        for ((amount, description, category) in expenses) {
                            val validAmount = amount!!
                            accountingManager?.addExpense(Expense(
                                amount = validAmount,
                                description = description,
                                date = date,
                                category = category,
                                accountId = accountId
                            ))
                            total += validAmount
                        }
                        setStatus("✅ ${expenses.size} هزینه به مبلغ کل ${total.toLong()} تومان ثبت شد")
                        findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
                    }
                }
                .setNegativeButton("لغو", null)
                .show()
        }
    }


    private fun parseJalaliDate(value: String): Long? {
        val parts = normalizeDigits(value).trim().split(Regex("[/\\-]"))
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 1300..1600 || month !in 1..12 || day !in 1..PersianCalendarHelper.daysInJalaliMonth(year, month)) return null
        return PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
    }

    private fun showReminderConfirmDialog(result: ReminderResult) {
        val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(result.triggerTime)
        val cal = Calendar.getInstance().apply { timeInMillis = result.triggerTime }
        val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val dateStr = PersianCalendarHelper.formatJalali(y, m, d)

        AlertDialog.Builder(this)
            .setTitle("تأیید یادآوری")
            .setMessage("📋 ${result.title}\n📅 $dateStr ساعت $timeStr")
            .setPositiveButton("✅ ثبت یادآوری") { _, _ ->
                lifecycleScope.launch {
                    smartReminderManager?.addReminder(ReminderEntity(
                        title = result.title,
                        description = result.title,
                        triggerTime = result.triggerTime,
                        alertType = AlertType.NOTIFICATION.name,
                        priority = Priority.MEDIUM.name
                    ))
                    setStatus("✅ یادآوری «${result.title}» ثبت شد")
                    findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
                }
            }
            .setNegativeButton("✏️ ویرایش", null)
            .show()
    }

    private fun showFallbackChoices(transcript: String) {
        setStatus("📝 متن تشخیص داده شده: «${transcript}»")
        AlertDialog.Builder(this)
            .setTitle("انتخاب عملیات")
            .setMessage("متن تشخیص داده شده:\n" + transcript)
            .setPositiveButton("💰 ثبت هزینه") { _, _ -> showManualExpenseEntry(transcript) }
            .setNeutralButton("🔔 ثبت یادآوری") { _, _ -> showManualReminderEntry(transcript) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showManualExpenseEntry(transcript: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }
        val amountInput = EditText(this).apply {
            hint = "مبلغ (تومان)"
            inputType = InputType.TYPE_CLASS_NUMBER
            container.addView(this)
        }
        val descInput = EditText(this).apply {
            hint = "توضیحات"
            setText(transcript)
            container.addView(this)
        }
        val catInput = android.widget.AutoCompleteTextView(this).apply {
            hint = "دسته‌بندی"
            setAdapter(android.widget.ArrayAdapter(this@VoiceCommandActivity, com.maliar.pro.R.layout.item_category_dropdown, android.R.id.text1, ExpenseCategory.ALL))
            setDropDownBackgroundResource(com.maliar.pro.R.drawable.bg_category_dropdown)
            threshold = 0
            setOnClickListener { showDropDown() }
            container.addView(this)
        }
        AlertDialog.Builder(this)
            .setTitle("ثبت هزینه")
            .setView(container)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val desc = descInput.text.toString().trim().ifBlank { transcript }
                val cat = catInput.text.toString().trim().ifBlank { "عمومی" }
                lifecycleScope.launch {
                    accountingManager?.addExpense(Expense(amount = amount, description = desc, date = System.currentTimeMillis(), category = cat))
                    setStatus("✅ هزینه ثبت شد")
                    findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showManualReminderEntry(transcript: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }
        val titleInput = EditText(this).apply {
            hint = "عنوان یادآوری"
            setText(transcript)
            container.addView(this)
        }
        AlertDialog.Builder(this)
            .setTitle("ثبت یادآوری")
            .setView(container)
            .setPositiveButton("ثبت") { _, _ ->
                val title = titleInput.text.toString().trim().ifBlank { transcript }
                lifecycleScope.launch {
                    smartReminderManager?.addReminder(ReminderEntity(
                        title = title,
                        description = title,
                        triggerTime = System.currentTimeMillis() + 3600000L,
                        alertType = AlertType.NOTIFICATION.name,
                        priority = Priority.MEDIUM.name
                    ))
                    setStatus("✅ یادآوری ثبت شد")
                    findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private suspend fun handleCallTranscript(transcript: String) {
        val nameGuess = Regex("(?:به|با)\\s+([\\p{L}\\s]{2,20}?)\\s*(?:زنگ بزن|تماس بگیر|تماس)")
            .find(transcript)?.groupValues?.get(1)?.trim()
        if (nameGuess.isNullOrBlank()) {
            setStatus("برای تماس، بگویید مثلاً «به علی زنگ بزن» یا «با سارا تماس بگیر»")
            return
        }
        val contacts = try {
            contactManager?.getAllContactsList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        val matched = contacts.firstOrNull {
            it.name.contains(nameGuess, ignoreCase = true) || nameGuess.contains(it.name, ignoreCase = true)
        }
        val contactName = matched?.name ?: ""
        val contactPhone = matched?.phoneNumber ?: ""
        if (matched == null) {
            setStatus("⚠️ مخاطبی به نام «${nameGuess}» در برنامه پیدا نشد.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تایید تماس")
            .setMessage("آیا می‌خواهید با ${contactName} (${contactPhone}) تماس بگیرید؟")
            .setPositiveButton("بله، تماس بگیر") { _, _ ->
                val result = VoiceCallHelper.makeCallWithResult(this, matched.phoneNumber)
                setStatus(when (result) {
                    VoiceCallHelper.CallResult.CALLED_DIRECTLY -> "📞 در حال تماس با …"
                    VoiceCallHelper.CallResult.OPENED_DIALER_NO_PERMISSION -> "📲 شماره‌گیر با شماره ${contactPhone} باز شد."
                    VoiceCallHelper.CallResult.FAILED -> "❌ خطا در برقراری تماس"
                })
                findViewById<View>(R.id.micButton).postDelayed({ finish() }, 1500)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isRecording) { recorder?.stop(); recorder?.release() }
        } catch (e: Exception) { }
        outputFile?.delete()
    }
}


