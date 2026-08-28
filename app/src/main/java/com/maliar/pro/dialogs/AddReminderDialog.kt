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
import com.maliar.pro.database.ReminderType
import com.maliar.pro.database.RepeatPattern
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.ui.common.PersianDatePickerDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.utils.ReminderSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private val REPEAT_PATTERN_LABELS = linkedMapOf(
    RepeatPattern.ONCE to "یک‌بار",
    RepeatPattern.DAILY to "روزانه",
    RepeatPattern.WEEKLY to "هفتگی",
    RepeatPattern.MONTHLY to "ماهانه",
    RepeatPattern.YEARLY to "سالانه",
    RepeatPattern.WEEKDAYS to "روزهای کاری",
    RepeatPattern.WEEKENDS to "آخر هفته",
    RepeatPattern.CUSTOM to "هر چند روز"
)

/**
 * Real, fully-functional "add reminder" dialog. Replaces the previous empty
 * FAB TODO in RemindersFragment. Uses SmartReminderManager so the reminder
 * is both stored in Room AND actually scheduled with AlarmManager.
 */
class AddReminderDialog(
    private val context: Context,
    private val smartReminderManager: SmartReminderManager,
    private val onSaved: () -> Unit,
    private val requestDeviceAudio: ((onPicked: (String) -> Unit) -> Unit)? = null
) {
    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    private var jalaliYear = today.first
    private var jalaliMonth = today.second
    private var jalaliDay = today.third
    private var hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private var minute = Calendar.getInstance().get(Calendar.MINUTE)

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
        val repeatIntervalInput = view.findViewById<TextInputEditText>(R.id.repeatIntervalInput)
        val contactButton = view.findViewById<Button>(R.id.reminderContactButton)
        val soundButton = view.findViewById<Button>(R.id.reminderSoundButton)
        var selectedSound = ReminderSound.DEFAULT_ALARM
        fun refreshSoundButtonText() { soundButton.text = ReminderSound.labelFor(selectedSound) }
        refreshSoundButtonText()
        soundButton.setOnClickListener {
            SoundPickerDialog.show(
                context,
                onDeviceAudioRequested = { onPicked -> requestDeviceAudio?.invoke(onPicked) },
                onSelected = { value -> selectedSound = value; refreshSoundButtonText() }
            )
        }

        var selectedContact: Contact? = null
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

        repeatPatternSpinner.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item, REPEAT_PATTERN_LABELS.values.toList()
        )

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
            .setTitle("افزودن یادآوری")
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
                val repeatPattern = REPEAT_PATTERN_LABELS.keys.toList()
                    .getOrElse(repeatPatternSpinner.selectedItemPosition) { RepeatPattern.ONCE }
                val repeatIntervalDays = if (repeatPattern == RepeatPattern.CUSTOM) {
                    repeatIntervalInput.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                } else 0

                val dateMillis = PersianCalendarHelper.jalaliToGregorianMillis(jalaliYear, jalaliMonth, jalaliDay)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                val reminder = ReminderEntity(
                    title = title,
                    description = description,
                    reminderType = ReminderType.SIMPLE.name,
                    priority = priority.name,
                    alertType = alertType.name,
                    triggerTime = cal.timeInMillis,
                    repeatPattern = repeatPattern.name,
                    repeatIntervalDays = repeatIntervalDays,
                    category = category,
                    contactName = selectedContact?.name.orEmpty(),
                    contactPhoneNumber = selectedContact?.phoneNumber.orEmpty(),
                    soundUri = selectedSound
                )

                CoroutineScope(Dispatchers.Main).launch {
                    smartReminderManager.addReminder(reminder)
                    onSaved()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
