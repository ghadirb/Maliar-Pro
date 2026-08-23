package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorDirection
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.database.ReminderManager
import com.maliar.pro.models.CalendarEvent
import com.maliar.pro.models.CalendarEventType
import com.maliar.pro.utils.FinanceCalendarUtils
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** [revision] increments on every [FinancialCalendarViewModel.loadMonth] call so the state
 *  flow always emits a genuinely new value, even when navigating between two months that
 *  both happen to have zero events - `emptyMap() == emptyMap()` is true, and a
 *  MutableStateFlow silently drops emissions whose new value equals the old one, which
 *  otherwise made the prev/next-month buttons look completely broken on a test/empty
 *  dataset (the month number changed internally but the UI never re-collected). */
data class CalendarUiState(
    val year: Int,
    val month: Int,
    val eventsByDay: Map<Int, List<CalendarEvent>>,
    val revision: Long
)

/**
 * Backs the "تقویم مالی" screen: for whatever Jalali (year, month) is currently displayed,
 * builds a day -> events map covering installments, checks, debts, person debtors and
 * reminders - everything the user asked to see "روی تقویم" in one place.
 */
class FinancialCalendarViewModel(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager,
    private val debtorManager: DebtorManager,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val today = PersianCalendarHelper.getCurrentJalaliDate()
    private var revisionCounter = 0L

    private val _uiState = MutableStateFlow(CalendarUiState(today.first, today.second, emptyMap(), revisionCounter))
    val uiState = _uiState.asStateFlow()

    init {
        loadMonth(today.first, today.second)
    }

    fun nextMonth() {
        val current = _uiState.value
        val (y, m) = if (current.month == 12) current.year + 1 to 1 else current.year to current.month + 1
        loadMonth(y, m)
    }

    fun previousMonth() {
        val current = _uiState.value
        val (y, m) = if (current.month == 1) current.year - 1 to 12 else current.year to current.month - 1
        loadMonth(y, m)
    }

    fun loadMonth(year: Int, month: Int) {
        // Bump year/month immediately (with the previous events cleared) so the header and
        // grid respond the instant a nav button/swipe is triggered, without waiting on the
        // DB queries below - the day dots/event list simply fill in a moment later.
        revisionCounter++
        _uiState.value = CalendarUiState(year, month, emptyMap(), revisionCounter)

        viewModelScope.launch {
            val events = mutableMapOf<Int, MutableList<CalendarEvent>>()

            fun add(day: Int, event: CalendarEvent) {
                events.getOrPut(day) { mutableListOf() }.add(event)
            }

            val monthStart = PersianCalendarHelper.jalaliToGregorianMillis(year, month, 1)
            val daysInMonth = PersianCalendarHelper.daysInJalaliMonth(year, month)
            val monthEnd = PersianCalendarHelper.jalaliToGregorianMillis(year, month, daysInMonth) + 24 * 60 * 60 * 1000

            accountingManager.getAllChecksList().filter { it.dueDate in monthStart until monthEnd }.forEach { check ->
                val (_, _, d) = PersianCalendarHelper.gregorianMillisToJalali(check.dueDate)
                add(d, CalendarEvent("چک ${check.checkNumber}", check.recipient.ifBlank { check.issuer }, check.amount, CalendarEventType.CHECK, check.dueDate))
            }

            accountingManager.getAllInstallmentsList().forEach { installment ->
                val occurrence = FinanceCalendarUtils.installmentOccurrenceInMonth(installment, year, month) ?: return@forEach
                val (_, _, d) = PersianCalendarHelper.gregorianMillisToJalali(occurrence)
                add(d, CalendarEvent(installment.title, "قسط ${installment.paidInstallments + 1} از ${installment.totalInstallments}", installment.installmentAmount, CalendarEventType.INSTALLMENT, occurrence))
            }

            financialStatusManager.getAllDebtsList().filter { !it.isPaid && it.endDate != null && it.endDate in monthStart until monthEnd }.forEach { debt ->
                val (_, _, d) = PersianCalendarHelper.gregorianMillisToJalali(debt.endDate!!)
                add(d, CalendarEvent(debt.title, "بدهی", debt.installmentAmount ?: debt.amount, CalendarEventType.DEBT, debt.endDate))
            }

            debtorManager.getAllDebtors().let { flow ->
                // one-shot snapshot is enough for a calendar render
                val debtors = flow.first()
                debtors.filter { !it.isSettled && it.dueDate != null && it.dueDate in monthStart until monthEnd }.forEach { debtor ->
                    val (_, _, d) = PersianCalendarHelper.gregorianMillisToJalali(debtor.dueDate!!)
                    val subtitle = if (debtor.direction == DebtorDirection.THEY_OWE_ME) "بدهکار به شما" else "شما بدهکارید"
                    add(d, CalendarEvent(debtor.name, subtitle, debtor.amount, CalendarEventType.DEBTOR, debtor.dueDate))
                }
            }

            reminderManager.getAllRemindersList().filter { !it.isCompleted && it.triggerTime in monthStart until monthEnd }.forEach { reminder ->
                val (_, _, d) = PersianCalendarHelper.gregorianMillisToJalali(reminder.triggerTime)
                add(d, CalendarEvent(reminder.title, reminder.description, null, CalendarEventType.REMINDER, reminder.triggerTime))
            }

            // Only commit if this is still the most recent request - guards against a slow
            // query for month A finishing after the user has already swiped on to month B.
            if (revisionCounter == _uiState.value.revision) {
                _uiState.value = CalendarUiState(year, month, events, revisionCounter)
            }
        }
    }
}

class FinancialCalendarViewModelFactory(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager,
    private val debtorManager: DebtorManager,
    private val reminderManager: ReminderManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FinancialCalendarViewModel(accountingManager, financialStatusManager, debtorManager, reminderManager) as T
}
