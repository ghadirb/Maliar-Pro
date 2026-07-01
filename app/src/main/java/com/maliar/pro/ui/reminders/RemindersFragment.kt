package com.maliar.pro.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.databinding.FragmentRemindersBinding
import com.maliar.pro.database.AlertType
import com.maliar.pro.database.Priority
import com.maliar.pro.database.ReminderEntity
import com.maliar.pro.database.ReminderType
import com.maliar.pro.database.RepeatPattern
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.viewmodels.RemindersViewModel
import com.maliar.pro.viewmodels.RemindersViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar

class RemindersFragment : Fragment() {

    private lateinit var binding: FragmentRemindersBinding
    private lateinit var adapter: RemindersAdapter
    private lateinit var smartManager: SmartReminderManager
    private val viewModel: RemindersViewModel by viewModels {
        RemindersViewModelFactory(SmartReminderManager(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        smartManager = SmartReminderManager(requireContext())
        setupRecyclerView()
        setupFab()
        setupFilterChips()
        observeViewModel()
        loadStats()
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            onItemClick = { reminder -> showReminderDetails(reminder) },
            onDeleteClick = { reminder -> viewModel.deleteReminder(reminder) },
            onCompleteClick = { reminder -> viewModel.completeReminder(reminder) }
        )
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.remindersRecyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.addReminderFab.setOnClickListener {
            showAddReminderTypeDialog()
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { viewModel.setFilter("all") }
        binding.chipTimeBased.setOnClickListener { viewModel.setFilter("time") }
        binding.chipRecurring.setOnClickListener { viewModel.setFilter("recurring") }
        binding.chipHighPriority.setOnClickListener { viewModel.setFilter("high_priority") }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.reminders.collect { reminders ->
                adapter.submitList(reminders)
                binding.emptyState.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val stats = smartManager.getReminderStats()
                binding.totalRemindersText.text = stats.totalReminders.toString()
                binding.activeRemindersText.text = stats.activeReminders.toString()
                binding.completedRemindersText.text = stats.completedReminders.toString()
                binding.todayRemindersText.text = stats.todayReminders.toString()
            } catch (e: Exception) {
                // Stats not available
            }
        }
    }

    private fun showAddReminderTypeDialog() {
        val options = arrayOf(
            "⏰ یادآوری زمانی",
            "🔁 یادآوری تکراری",
            "📍 یادآوری مکانی",
            "⚙️ یادآوری شرطی"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("نوع یادآوری")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTimeBasedReminderDialog()
                    1 -> showRecurringReminderDialog()
                    2 -> showLocationBasedReminderDialog()
                    3 -> showConditionalReminderDialog()
                }
            }
            .show()
    }

    private fun showTimeBasedReminderDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.maliar.pro.R.layout.dialog_add_reminder, null)
        
        val titleInput = dialogView.findViewById<TextInputEditText>(com.maliar.pro.R.id.reminderTitleInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(com.maliar.pro.R.id.reminderDescriptionInput)
        val dateButton = dialogView.findViewById<android.widget.Button>(com.maliar.pro.R.id.reminderDateButton)
        val timeButton = dialogView.findViewById<android.widget.Button>(com.maliar.pro.R.id.reminderTimeButton)
        val priorityGroup = dialogView.findViewById<ChipGroup>(com.maliar.pro.R.id.priorityChipGroup)
        val alertTypeGroup = dialogView.findViewById<ChipGroup>(com.maliar.pro.R.id.alertTypeChipGroup)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(com.maliar.pro.R.id.categorySpinner)

        val categories = arrayOf("شخصی", "کاری", "خانوادگی", "مالی", "سلامت", "خرید", "سایر")
        categorySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val calendar = Calendar.getInstance()
        var selectedDate = calendar.timeInMillis
        var selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute = calendar.get(Calendar.MINUTE)

        dateButton.text = "${calendar.get(Calendar.YEAR)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
        timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)

        dateButton.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, day)
                    selectedDate = cal.timeInMillis
                    dateButton.text = "$year/${month + 1}/$day"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    timeButton.text = String.format("%02d:%02d", hour, minute)
                },
                selectedHour, selectedMinute, true
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("➕ یادآوری زمانی")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text?.toString()?.trim() ?: ""
                val description = descriptionInput.text?.toString()?.trim() ?: ""
                val category = categories[categorySpinner.selectedItemPosition]

                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val priority = when (priorityGroup.checkedChipId) {
                    com.maliar.pro.R.id.chipLowPriority -> Priority.LOW
                    com.maliar.pro.R.id.chipHighPriority -> Priority.HIGH
                    else -> Priority.MEDIUM
                }

                val alertType = when (alertTypeGroup.checkedChipId) {
                    com.maliar.pro.R.id.chipAlertFullScreen -> AlertType.FULL_SCREEN
                    com.maliar.pro.R.id.chipAlertSmart -> AlertType.SMART
                    else -> AlertType.NOTIFICATION
                }

                val cal = Calendar.getInstance()
                cal.timeInMillis = selectedDate
                cal.set(Calendar.HOUR_OF_DAY, selectedHour)
                cal.set(Calendar.MINUTE, selectedMinute)
                cal.set(Calendar.SECOND, 0)

                val reminder = ReminderEntity(
                    title = "$category - $title",
                    description = description,
                    reminderType = ReminderType.SIMPLE.name,
                    priority = priority.name,
                    alertType = alertType.name,
                    triggerTime = cal.timeInMillis,
                    category = category
                )

                lifecycleScope.launch {
                    smartManager.addReminder(reminder)
                    Toast.makeText(requireContext(), "✅ یادآوری ذخیره شد", Toast.LENGTH_SHORT).show()
                    loadStats()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showRecurringReminderDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(com.maliar.pro.R.layout.dialog_recurring_reminder, null)

        val titleInput = dialogView.findViewById<TextInputEditText>(com.maliar.pro.R.id.recurringTitleInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(com.maliar.pro.R.id.recurringDescriptionInput)
        val patternSpinner = dialogView.findViewById<android.widget.Spinner>(com.maliar.pro.R.id.recurringPatternSpinner)
        val timeButton = dialogView.findViewById<android.widget.Button>(com.maliar.pro.R.id.recurringSelectTimeButton)
        val daysButton = dialogView.findViewById<android.widget.Button>(com.maliar.pro.R.id.recurringSelectDaysButton)
        val alertTypeGroup = dialogView.findViewById<ChipGroup>(com.maliar.pro.R.id.recurringAlertTypeChipGroup)

        val patterns = arrayOf("روزانه", "هفتگی", "ماهانه", "سالانه", "انتخاب روزهای خاص")
        patternSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, patterns)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        var selectedHour = 9
        var selectedMinute = 0
        val selectedDays = mutableSetOf<Int>()

        timeButton.text = "انتخاب ساعت"
        timeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    timeButton.text = String.format("%02d:%02d", hour, minute)
                },
                selectedHour, selectedMinute, true
            ).show()
        }

        daysButton.isEnabled = false
        daysButton.setOnClickListener {
            val dayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
            val checkedDays = BooleanArray(7) { selectedDays.contains(it) }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب روزهای هفته")
                .setMultiChoiceItems(dayNames, checkedDays) { _, which, isChecked ->
                    if (isChecked) selectedDays.add(which)
                    else selectedDays.remove(which)
                }
                .setPositiveButton("تأیید") { _, _ ->
                    val selectedDayNames = selectedDays.sorted().map { dayNames[it] }.joinToString("، ")
                    daysButton.text = "روزهای انتخاب شده: $selectedDayNames"
                }
                .setNegativeButton("لغو", null)
                .show()
        }

        patternSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                daysButton.isEnabled = position == 4
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔁 یادآوری تکراری")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text?.toString()?.trim() ?: ""
                val description = descriptionInput.text?.toString()?.trim() ?: ""

                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                val pattern = when (patternSpinner.selectedItemPosition) {
                    0 -> RepeatPattern.DAILY
                    1 -> RepeatPattern.WEEKLY
                    2 -> RepeatPattern.MONTHLY
                    3 -> RepeatPattern.YEARLY
                    4 -> {
                        if (selectedDays.isEmpty()) {
                            Toast.makeText(requireContext(), "⚠️ حداقل یک روز را انتخاب کنید", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        RepeatPattern.CUSTOM
                    }
                    else -> RepeatPattern.DAILY
                }

                val alertType = when (alertTypeGroup.checkedChipId) {
                    com.maliar.pro.R.id.chipRecurringAlertFullScreen -> AlertType.FULL_SCREEN
                    com.maliar.pro.R.id.chipRecurringAlertSmart -> AlertType.SMART
                    else -> AlertType.NOTIFICATION
                }

                val reminder = ReminderEntity(
                    title = title,
                    description = description,
                    reminderType = ReminderType.RECURRING.name,
                    priority = Priority.MEDIUM.name,
                    alertType = alertType.name,
                    triggerTime = calendar.timeInMillis,
                    repeatPattern = pattern.name,
                    customRepeatDays = if (pattern == RepeatPattern.CUSTOM) selectedDays.joinToString(",") else ""
                )

                lifecycleScope.launch {
                    smartManager.addReminder(reminder)
                    Toast.makeText(requireContext(), "✅ یادآوری تکراری ذخیره شد", Toast.LENGTH_SHORT).show()
                    loadStats()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showLocationBasedReminderDialog() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = android.widget.EditText(requireContext()).apply {
            hint = "عنوان یادآوری مکانی"
        }
        val placeInput = android.widget.EditText(requireContext()).apply {
            hint = "نام مکان (مثلاً خانه، محل کار)"
        }
        val latInput = android.widget.EditText(requireContext()).apply {
            hint = "عرض جغرافیایی (lat)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val lngInput = android.widget.EditText(requireContext()).apply {
            hint = "طول جغرافیایی (lng)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        container.addView(titleInput)
        container.addView(placeInput)
        container.addView(latInput)
        container.addView(lngInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📍 یادآوری مکانی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val placeName = placeInput.text.toString().trim()
                val latText = latInput.text.toString().trim()
                val lngText = lngInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (latText.isEmpty() || lngText.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ مختصات را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()
                if (lat == null || lng == null) {
                    Toast.makeText(requireContext(), "⚠️ مختصات نامعتبر است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val reminder = ReminderEntity(
                    title = title,
                    description = if (placeName.isNotEmpty()) "مکان: $placeName" else "",
                    reminderType = ReminderType.LOCATION_BASED.name,
                    priority = Priority.MEDIUM.name,
                    alertType = AlertType.NOTIFICATION.name,
                    triggerTime = System.currentTimeMillis(),
                    locationLat = lat,
                    locationLng = lng,
                    locationName = placeName
                )

                lifecycleScope.launch {
                    smartManager.addReminder(reminder)
                    Toast.makeText(requireContext(), "✅ یادآوری مکانی ذخیره شد", Toast.LENGTH_SHORT).show()
                    loadStats()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showConditionalReminderDialog() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = android.widget.EditText(requireContext()).apply {
            hint = "عنوان یادآوری شرطی"
        }
        val conditionInput = android.widget.EditText(requireContext()).apply {
            hint = "شرط را بنویسید (مثال: اگر موجودی زیر ۱۰۰ هزار شد...)"
        }

        container.addView(titleInput)
        container.addView(conditionInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚙️ یادآوری شرطی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val condition = conditionInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (condition.isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ شرط را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val reminder = ReminderEntity(
                    title = title,
                    description = condition,
                    reminderType = ReminderType.CONDITIONAL.name,
                    priority = Priority.MEDIUM.name,
                    alertType = AlertType.NOTIFICATION.name,
                    triggerTime = 0L,
                    tags = "شرط: $condition"
                )

                lifecycleScope.launch {
                    smartManager.addReminder(reminder)
                    Toast.makeText(requireContext(), "✅ یادآوری شرطی ذخیره شد", Toast.LENGTH_SHORT).show()
                    loadStats()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showReminderDetails(reminder: ReminderEntity) {
        val details = buildString {
            appendLine("عنوان: ${reminder.title}")
            if (reminder.description.isNotEmpty()) appendLine("توضیحات: ${reminder.description}")
            appendLine("اولویت: ${reminder.priority}")
            appendLine("نوع هشدار: ${reminder.alertType}")
            appendLine("وضعیت: ${if (reminder.isCompleted) "✅ انجام شده" else "⏳ در انتظار"}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("جزئیات یادآوری")
            .setMessage(details)
            .setPositiveButton("بستن", null)
            .setNegativeButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    smartManager.deleteReminder(reminder)
                    Toast.makeText(requireContext(), "🗑️ حذف شد", Toast.LENGTH_SHORT).show()
                    loadStats()
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }
}