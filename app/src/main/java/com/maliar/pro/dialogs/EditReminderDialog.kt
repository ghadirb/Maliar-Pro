package com.maliar.pro.dialogs

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.R
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.Contact
import com.maliar.pro.database.ContactManager
import com.maliar.pro.database.Priority
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.RepeatPattern
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.utils.ReminderSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private val REPEAT_PATTERN_LABELS_EDIT = linkedMapOf(
    RepeatPattern.ONCE to "یک‌بار",
    RepeatPattern.DAILY to "روزانه",
    RepeatPattern.WEEKLY to "هفتگی",
    RepeatPattern.MONTHLY to "ماهانه",
    RepeatPattern.YEARLY to "سالانه",
    RepeatPattern.WEEKDAYS to "روزهای کاری",
    RepeatPattern.WEEKENDS to "آخر هفته"
)

class EditReminderDialog(
    private val context: Context,
    private val smartReminderManager: SmartReminderManager,
    private val reminder: ReminderEntity,
    private val onSaved: () -> Unit,
    private val requestDeviceAudio: ((onPicked: (String) -> Unit) -> Unit)? = null
) {
    private val initialJalali = PersianCalendarHelper.gregorianMillisToJalali(reminder.triggerTime)
    private var jalaliYear = initialJalali.first
    private var jalaliMonth = initialJalali.second
    private var jalaliDay = initialJalali.third
    private var hour: Int
    private var minute: Int

    init {
        val cal = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }
        hour = cal.get(Calendar.HOUR_OF_DAY)
        minute = cal.get(Calendar.MINUTE)
    }

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_reminder, null)

        val titleInput = view.findViewById<TextInputEditText>(R.id.reminderTitleInput)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.reminderDescriptionInput)
        val categorySpinner = view.findViewById<Spinner>(R.id.categorySpinner)
        val dateButton = view.findViewById<Button>(R.id.reminderDateButton)
        val timeButton = view.findViewById<Button>(R.id.reminderTimeButton)
        val priorityChipGroup = view.findViewById<ChipGroup>(R.id.priorityChipGroup)
        val alertTypeChipGroup = view.findViewById<ChipGroup>(R.id.alertTypeChipGroup)
        val repeatPatternSpinner = view.findViewById<Spinner>(R.id.repeatPatternSpinner)
        val contactButton = view.findViewById<Button>(R.id.reminderContactButton)
        val soundButton = view.findViewById<Button>(R.id.reminderSoundButton)
        var selectedSound = reminder.soundUri
        fun refreshSoundButtonText() { soundButton.text = ReminderSound.labelFor(selectedSound) }
        refreshSoundButtonText()
        soundButton.setOnClickListener {
            val labels = ReminderSound.builtIns.map { it.label }.toMutableList().apply { add("انتخاب فایل یا موسیقی از گوشی") }
            AlertDialog.Builder(context).setTitle("انتخاب صدای یادآوری").setItems(labels.toTypedArray()) { _, which ->
                if (which < ReminderSound.builtIns.size) {
                    selectedSound = ReminderSound.builtIns[which].value
                    refreshSoundButtonText()
                } else requestDeviceAudio?.invoke { uri -> selectedSound = uri; refreshSoundButtonText() }
            }.show()
        }

        var selectedContact: Contact? = if (reminder.contactPhoneNumber.isNotBlank()) {
            Contact(rowNumber = 0, name = reminder.contactName, phoneNumber = reminder.contactPhoneNumber)
        } else null
        fun refreshContactButtonText() {
            contactButton.text = selectedContact?.let { "مخاطب: ${it.name}" } ?: "انتخاب مخاطب"
        }
        refreshContactButtonText()

        contactButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val contacts = ContactManager(context).getAllContactsList()
                if (contacts.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("مخاطبی وجود ندارد")
                        .setMessage("ابتدا از بخش «مخاطبین» یک مخاطب اضافه کنید.")
                        .setPositiveButton("باشه", null)
                        .show()
                    return@launch
                }
                val names = contacts.map { "${it.name} (${it.phoneNumber})" }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle("انتخاب مخاطب برای تماس")
                    .setItems(names) { _, which ->
                        selectedContact = contacts[which]
                        refreshContactButtonText()
                    }
                    .setNeutralButton("حذف انتخاب") { _, _ ->
                        selectedContact = null
                        refreshContactButtonText()
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        }

        val categories = listOf("عمومی", "مالی", "کاری", "شخصی", "سلامت", "خانواده")
        categorySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories)
        val categoryIndex = categories.indexOf(reminder.category).let { if (it >= 0) it else 0 }
        categorySpinner.setSelection(categoryIndex)

        val repeatLabels = REPEAT_PATTERN_LABELS_EDIT.values.toList()
        val repeatKeys = REPEAT_PATTERN_LABELS_EDIT.keys.toList()
        repeatPatternSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, repeatLabels)
        val currentPattern = runCatching { RepeatPattern.valueOf(reminder.repeatPattern) }.getOrDefault(RepeatPattern.ONCE)
        repeatPatternSpinner.setSelection(repeatKeys.indexOf(currentPattern).coerceAtLeast(0))

        titleInput.setText(reminder.title)
        descriptionInput.setText(reminder.description)

        when (runCatching { Priority.valueOf(reminder.priority) }.getOrDefault(Priority.MEDIUM)) {
            Priority.LOW -> priorityChipGroup.check(R.id.chipLowPriority)
            Priority.HIGH -> priorityChipGroup.check(R.id.chipHighPriority)
            else -> priorityChipGroup.check(R.id.chipMediumPriority)
        }
        when (runCatching { AlertType.valueOf(reminder.alertType) }.getOrDefault(AlertType.NOTIFICATION)) {
            AlertType.FULL_SCREEN -> alertTypeChipGroup.check(R.id.chipAlertFullScreen)
            AlertType.SMART -> alertTypeChipGroup.check(R.id.chipAlertSmart)
            else -> alertTypeChipGroup.check(R.id.chipAlertNotification)
        }

        fun refreshDateButtonText() {
            dateButton.text = PersianCalendarHelper.formatJalali(jalaliYear, jalaliMonth, jalaliDay)
        }
        fun refreshTimeButtonText() {
            timeButton.text = String.format("%02d:%02d", hour, minute)
        }
        refreshDateButtonText()
        refreshTimeButtonText()

        dateButton.setOnClickListener {
            PersianDatePickerDialog(
                context, initialYear = jalaliYear, initialMonth = jalaliMonth, initialDay = jalaliDay
            ) { y, m, d ->
                jalaliYear = y; jalaliMonth = m; jalaliDay = d
                refreshDateButtonText()
            }.show()
        }

        timeButton.setOnClickListener {
            com.maliar.pro.ui.common.MaliarTimePickerDialog.show(context, hour, minute) { h, min ->
                hour = h; minute = min
                refreshTimeButtonText()
            }
        }

        AlertDialog.Builder(context)
            .setTitle("ویرایش یادآوری")
            .setView(view)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text?.toString().orEmpty()
                if (title.isBlank()) return@setPositiveButton

                val description = descriptionInput.text?.toString().orEmpty()
                val category = categorySpinner.selectedItem?.toString().orEmpty()

                val priority = when (priorityChipGroup.checkedChipId) {
                    R.id.chipLowPriority -> Priority.LOW
                    R.id.chipHighPriority -> Priority.HIGH
                    else -> Priority.MEDIUM
                }
                val alertType = when (alertTypeChipGroup.checkedChipId) {
                    R.id.chipAlertFullScreen -> AlertType.FULL_SCREEN
                    R.id.chipAlertSmart -> AlertType.SMART
                    else -> AlertType.NOTIFICATION
                }
                val repeatPattern = repeatKeys.getOrElse(repeatPatternSpinner.selectedItemPosition) { RepeatPattern.ONCE }

                val dateMillis = PersianCalendarHelper.jalaliToGregorianMillis(jalaliYear, jalaliMonth, jalaliDay)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                val updated = reminder.copy(
                    title = title,
                    description = description,
                    priority = priority.name,
                    alertType = alertType.name,
                    triggerTime = cal.timeInMillis,
                    repeatPattern = repeatPattern.name,
                    category = category,
                    contactName = selectedContact?.name.orEmpty(),
                    contactPhoneNumber = selectedContact?.phoneNumber.orEmpty(),
                    soundUri = selectedSound
                )

                CoroutineScope(Dispatchers.Main).launch {
                    smartReminderManager.updateReminder(updated)
                    onSaved()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
