package com.maliar.pro.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.CalendarDayAdapter
import com.maliar.pro.adapters.CalendarDayCell
import com.maliar.pro.adapters.CalendarEventAdapter
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.databinding.FragmentFinancialCalendarBinding
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.FinancialCalendarViewModel
import com.maliar.pro.viewmodels.FinancialCalendarViewModelFactory
import kotlinx.coroutines.launch

/** "تقویم مالی": a Jalali month grid where installments, checks, debts, debtor due-dates
 *  and reminders are all marked with a dot; tapping a day lists everything due that day. */
class FinancialCalendarFragment : Fragment() {

    private lateinit var binding: FragmentFinancialCalendarBinding
    private lateinit var dayAdapter: CalendarDayAdapter
    private lateinit var eventAdapter: CalendarEventAdapter
    private var selectedDay: Int? = null

    private val viewModel: FinancialCalendarViewModel by viewModels {
        FinancialCalendarViewModelFactory(
            AccountingManager(requireContext()),
            FinancialStatusManager(requireContext()),
            DebtorManager(requireContext()),
            ReminderManager(requireContext())
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFinancialCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dayAdapter = CalendarDayAdapter { day ->
            selectedDay = day
            renderSelectedDayEvents()
        }
        binding.calendarGridRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarGridRecyclerView.adapter = dayAdapter

        eventAdapter = CalendarEventAdapter()
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        binding.prevMonthButton.setOnClickListener {
            selectedDay = null
            viewModel.previousMonth()
        }
        binding.nextMonthButton.setOnClickListener {
            selectedDay = null
            viewModel.nextMonth()
        }

        lifecycleScope.launch {
            viewModel.eventsByDay.collect { renderGrid() }
        }
    }

    private fun renderGrid() {
        val year = viewModel.year.value
        val month = viewModel.month.value
        binding.monthYearText.text =
            "${PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(month - 1) { "" }} $year"

        val today = PersianCalendarHelper.getCurrentJalaliDate()
        val firstDayGregorian = PersianCalendarHelper.jalaliToGregorianMillis(year, month, 1)
        // Iran's week starts on Saturday; Calendar.DAY_OF_WEEK: Sat=7,Sun=1,...Fri=6.
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = firstDayGregorian
        val gregorianDow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
        val leadingBlanks = (gregorianDow - java.util.Calendar.SATURDAY + 7) % 7

        val daysInMonth = PersianCalendarHelper.daysInJalaliMonth(year, month)
        val events = viewModel.eventsByDay.value

        val cells = mutableListOf<CalendarDayCell>()
        repeat(leadingBlanks) { cells += CalendarDayCell(null, false, false) }
        for (day in 1..daysInMonth) {
            val isToday = today.first == year && today.second == month && today.third == day
            cells += CalendarDayCell(day, events.containsKey(day), isToday)
        }

        if (selectedDay == null && events.isNotEmpty()) {
            // Default to today if it has events, otherwise the first day that does.
            selectedDay = if (today.first == year && today.second == month && events.containsKey(today.third))
                today.third else events.keys.min()
        }
        dayAdapter.submitCells(cells, selectedDay)
        renderSelectedDayEvents()
    }

    private fun renderSelectedDayEvents() {
        val day = selectedDay
        val year = viewModel.year.value
        val month = viewModel.month.value
        if (day == null) {
            binding.selectedDayLabel.text = "روزی را انتخاب کنید"
            eventAdapter.submitList(emptyList())
            binding.noEventsText.visibility = View.VISIBLE
            return
        }
        val dayName = "${day} ${PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(month - 1) { "" }}"
        binding.selectedDayLabel.text = "رویدادهای $dayName"

        val events = viewModel.eventsByDay.value[day].orEmpty()
        eventAdapter.submitList(events)
        binding.noEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        binding.eventsRecyclerView.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }
}
