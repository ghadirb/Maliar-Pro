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

    private val _year = MutableStateFlow(today.first)
    val year = _year.asStateFlow()

    private val _month = MutableStateFlow(today.second)
    val month = _month.asStateFlow()

    private val _eventsByDay = MutableStateFlow<Map<Int, List<CalendarEvent>>>(emptyMap())
    val eventsByDay = _eventsByDay.asStateFlow()

    init {
        loadMonth(today.first, today.second)
    }

    fun nextMonth() {
        val (y, m) = if (_month.value == 12) _year.value + 1 to 1 else _year.value to _month.value + 1
        loadMonth(y, m)
    }

    fun previousMonth() {
        val (y, m) = if (_month.value == 1) _year.value - 1 to 12 else _year.value to _month.value - 1
        loadMonth(y, m)
    }

    fun loadMonth(year: Int, month: Int) {
        _year.value = year
        _month.value = month
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

            _eventsByDay.value = events
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
