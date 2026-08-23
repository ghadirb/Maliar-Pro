package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorDirection
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.models.DueItem
import com.maliar.pro.models.DueItemType
import com.maliar.pro.utils.FinanceCalendarUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Combines checks (unaśhed), active installments (projected to their next Jalali payment
 * day), unpaid loans/debts and person debtors/creditors into one chronologically-sorted
 * list. Used both by the small "نزدیک به تاریخ" summary card at the top of the accounting
 * tab and by the full [com.maliar.pro.ui.accounting.DueSoonFragment] list.
 */
class DueSoonViewModel(
    private val accountingManager: AccountingManager,
    private val financialStatusManager: FinancialStatusManager,
    private val debtorManager: DebtorManager
) : ViewModel() {

    val dueItems = combine(
        accountingManager.getAllChecks(),
        accountingManager.getAllInstallments(),
        financialStatusManager.getAllDebts(),
        debtorManager.getAllDebtors()
    ) { checks, installments, debts, debtors ->
        val items = mutableListOf<DueItem>()

        checks.filter { !it.isCashed }.forEach { check ->
            items += DueItem(
                id = check.id,
                type = DueItemType.CHECK,
                title = "چک ${check.checkNumber}".trim(),
                amount = check.amount,
                dueDate = check.dueDate,
                subtitle = check.recipient.ifBlank { check.issuer }
            )
        }

        installments.forEach { installment ->
            val due = FinanceCalendarUtils.nextInstallmentDueDate(installment) ?: return@forEach
            items += DueItem(
                id = installment.id,
                type = DueItemType.INSTALLMENT,
                title = installment.title,
                amount = installment.installmentAmount,
                dueDate = due,
                subtitle = "قسط ${installment.paidInstallments + 1} از ${installment.totalInstallments}"
            )
        }

        debts.filter { !it.isPaid && it.endDate != null }.forEach { debt ->
            items += DueItem(
                id = debt.id,
                type = DueItemType.INSTALLMENT,
                title = debt.title,
                amount = debt.installmentAmount ?: debt.amount,
                dueDate = debt.endDate!!,
                subtitle = "بدهی"
            )
        }

        debtors.filter { !it.isSettled && it.dueDate != null }.forEach { debtor ->
            val type = if (debtor.direction == DebtorDirection.THEY_OWE_ME)
                DueItemType.DEBTOR_THEY_OWE_ME else DueItemType.DEBTOR_I_OWE_THEM
            items += DueItem(
                id = debtor.id,
                type = type,
                title = debtor.name,
                amount = debtor.amount,
                dueDate = debtor.dueDate!!,
                subtitle = if (debtor.direction == DebtorDirection.THEY_OWE_ME) "بدهکار به شما" else "شما بدهکارید"
            )
        }

        items.sortedBy { it.dueDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Items due within the next [days] days (default one week), including overdue ones -
     *  used for the compact summary card. */
    fun itemsDueWithin(days: Int = 7): List<DueItem> {
        val cutoff = System.currentTimeMillis() + days.toLong() * 24 * 60 * 60 * 1000
        return dueItems.value.filter { it.dueDate <= cutoff }
    }
}
