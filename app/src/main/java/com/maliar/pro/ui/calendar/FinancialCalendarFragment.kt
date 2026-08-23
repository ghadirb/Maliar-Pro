package com.maliar.pro.ui.calendar

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.maliar.pro.viewmodels.CalendarUiState
import com.maliar.pro.viewmodels.FinancialCalendarViewModel
import com.maliar.pro.viewmodels.FinancialCalendarViewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.abs

/** "تقویم مالی": a Jalali month grid where installments, checks, debts, debtor due-dates
 *  and reminders are all marked with a dot; tapping a day lists everything due that day.
 *  Months can be changed with the header arrows or by swiping left/right anywhere on the
 *  grid. */
class FinancialCalendarFragment : Fragment() {

    private lateinit var binding: FragmentFinancialCalendarBinding
    private lateinit var dayAdapter: CalendarDayAdapter
    private lateinit var eventAdapter: CalendarEventAdapter
    private lateinit var gestureDetector: GestureDetector
    private var selectedDay: Int? = null
    private var lastRenderedState: CalendarUiState? = null

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
            lastRenderedState?.let { renderSelectedDayEvents(it) }
        }
        binding.calendarGridRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarGridRecyclerView.adapter = dayAdapter

        eventAdapter = CalendarEventAdapter()
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = eventAdapter

        binding.prevMonthButton.setOnClickListener { goToPreviousMonth() }
        binding.nextMonthButton.setOnClickListener { goToNextMonth() }

        setupSwipeNavigation()

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                lastRenderedState = state
                renderGrid(state)
            }
        }
    }

    private fun goToPreviousMonth() {
        selectedDay = null
        viewModel.previousMonth()
    }

    private fun goToNextMonth() {
        selectedDay = null
        viewModel.nextMonth()
    }

    /** Swipe navigation: a left swipe moves forward to next month, a right swipe moves back
     *  to the previous month - the same left/right = forward/back convention most calendar
     *  and gallery apps use regardless of the RTL text layout.
     *
     *  A plain View.OnTouchListener on the RecyclerView wouldn't reliably see these swipes:
     *  the day cells are individually clickable, so they consume ACTION_DOWN/UP before the
     *  parent's touch listener ever runs. RecyclerView.OnItemTouchListener is the API meant
     *  for exactly this - it gets first look at every touch stream via
     *  onInterceptTouchEvent(), and only needs to return true (stealing the rest of that
     *  gesture from the children, which cancels any in-progress click) once a fling has
     *  actually been recognized - regular taps on a day cell are completely unaffected. */
    private fun setupSwipeNavigation() {
        var swipeHandledThisGesture = false

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val startEvent = e1 ?: return false
                val diffX = e2.x - startEvent.x
                val diffY = e2.y - startEvent.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_DISTANCE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    swipeHandledThisGesture = true
                    if (diffX < 0) goToNextMonth() else goToPreviousMonth()
                    return true
                }
                return false
            }
        })

        binding.calendarGridRecyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) swipeHandledThisGesture = false
                gestureDetector.onTouchEvent(e)
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    val wasHandled = swipeHandledThisGesture
                    swipeHandledThisGesture = false
                    return wasHandled
                }
                return swipeHandledThisGesture
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: MotionEvent) {
                gestureDetector.onTouchEvent(e)
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun renderGrid(state: CalendarUiState) {
        val year = state.year
        val month = state.month
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
        val events = state.eventsByDay

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
        renderSelectedDayEvents(state)
    }

    private fun renderSelectedDayEvents(state: CalendarUiState) {
        val day = selectedDay
        if (day == null) {
            binding.selectedDayLabel.text = "روزی را انتخاب کنید"
            eventAdapter.submitList(emptyList())
            binding.noEventsText.visibility = View.VISIBLE
            binding.eventsRecyclerView.visibility = View.GONE
            return
        }
        val dayName = "$day ${PersianCalendarHelper.PERSIAN_MONTH_NAMES.getOrElse(state.month - 1) { "" }}"
        binding.selectedDayLabel.text = "رویدادهای $dayName"

        val events = state.eventsByDay[day].orEmpty()
        eventAdapter.submitList(events)
        binding.noEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        binding.eventsRecyclerView.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
