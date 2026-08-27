package com.maliar.pro.ui.reminders

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maliar.pro.R
import com.maliar.pro.adapters.RemindersAdapter
import com.maliar.pro.database.SmartReminderManager
import com.maliar.pro.dialogs.AddReminderDialog
import com.maliar.pro.dialogs.EditReminderDialog
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private lateinit var smartReminderManager: SmartReminderManager
    private lateinit var adapter: RemindersAdapter
    private lateinit var activeCountText: TextView
    private lateinit var nextReminderText: TextView
    private var pendingAudioSelection: ((String) -> Unit)? = null
    private val deviceAudioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pendingAudioSelection?.invoke(uri.toString())
        } catch (_: SecurityException) {
            // Some providers expose a readable URI without a persistable grant; it will
            // still work for the current installation and the app never requests storage access.
            pendingAudioSelection?.invoke(uri.toString())
        } finally {
            pendingAudioSelection = null
        }
    }

    private fun requestDeviceAudio(onPicked: (String) -> Unit) {
        pendingAudioSelection = onPicked
        deviceAudioPicker.launch(arrayOf("audio/*"))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminders, container, false)

        smartReminderManager = SmartReminderManager(requireContext())
        activeCountText = view.findViewById(R.id.activeRemindersCountText)
        nextReminderText = view.findViewById(R.id.nextReminderText)

        val recyclerView: RecyclerView = view.findViewById(R.id.remindersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = RemindersAdapter(
            onItemClick = { reminder ->
                EditReminderDialog(requireContext(), smartReminderManager, reminder, {
                    loadReminders()
                }, ::requestDeviceAudio).show()
            },
            onDeleteClick = { reminder ->
                lifecycleScope.launch {
                    smartReminderManager.deleteReminder(reminder)
                    loadReminders()
                }
            },
            onCompleteClick = { reminder ->
                lifecycleScope.launch {
                    smartReminderManager.completeReminder(reminder.id)
                    loadReminders()
                }
            }
        )
        recyclerView.adapter = adapter

        val fab: FloatingActionButton = view.findViewById(R.id.addReminderButton)
        fab.setOnClickListener {
            AddReminderDialog(requireContext(), smartReminderManager, {
                loadReminders()
            }, ::requestDeviceAudio).show()
        }

        loadReminders()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = smartReminderManager.getAllRemindersList()
            adapter.submitList(reminders)
            updateHeader()
        }
    }

    private fun updateHeader() {
        lifecycleScope.launch {
            val active = smartReminderManager.getActiveRemindersList()
            activeCountText.text = if (active.isEmpty()) {
                "یادآوری فعالی وجود ندارد"
            } else {
                "📌 ${active.size} یادآوری فعال"
            }

            val next = active.filter { it.triggerTime > System.currentTimeMillis() }
                .minByOrNull { it.triggerTime }

            if (next != null) {
                val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(next.triggerTime)
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = next.triggerTime }
                val timeStr = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                nextReminderText.text = "⏭ بعدی: ${next.title} — ${PersianCalendarHelper.formatJalali(y, m, d)} ساعت $timeStr"
                nextReminderText.visibility = View.VISIBLE
            } else {
                nextReminderText.visibility = View.GONE
            }
        }
    }
}
