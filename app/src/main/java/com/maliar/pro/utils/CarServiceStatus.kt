package com.maliar.pro.utils

import com.maliar.pro.database.CarServiceItem

enum class CarServiceUrgency { OVERDUE, SOON, OK, UNSCHEDULED }

data class CarServiceStatus(
    val item: CarServiceItem,
    val urgency: CarServiceUrgency,
    /** Negative once overdue. Null if this item isn't tracked by distance. */
    val remainingKm: Int?,
    /** Negative once overdue. Null if this item isn't tracked by calendar time. */
    val remainingDays: Long?
)

/**
 * Pure computation, deliberately with no DB/Context/AI access: km and date math are exact
 * and must never depend on anything that could be unavailable offline (per the "AI نباید
 * مسئول محاسبات حساس و قطعی باشد" requirement). Used identically by the car detail screen,
 * the "مواردی که باید حواسم باشد" screen, and DueDateReminderWorker so all three always
 * agree on whether something is overdue.
 */
object CarServiceStatusCalculator {

    /** [soonKmThreshold]/[soonDaysThreshold]: how close to due counts as "🟠 نزدیک" rather
     *  than "🟢 خوب". For an item tracked by both km and days ("هرکدام زودتر رسید"), it's
     *  OVERDUE if *either* trigger has already passed, and SOON if either is within its
     *  threshold and neither has passed yet - matching the "هرکدام زودتر" rule throughout. */
    fun compute(
        item: CarServiceItem,
        currentOdometerKm: Int,
        soonKmThreshold: Int = 1000,
        soonDaysThreshold: Long = 14,
        now: Long = System.currentTimeMillis()
    ): CarServiceStatus {
        val nextKm = item.nextDueOdometerKm
        val nextDate = item.nextDueDate

        if (nextKm == null && nextDate == null) {
            return CarServiceStatus(item, CarServiceUrgency.UNSCHEDULED, null, null)
        }

        val remainingKm = nextKm?.let { it - currentOdometerKm }
        val remainingDays = nextDate?.let { (it - now) / (24L * 60 * 60 * 1000) }

        val overdue = (remainingKm != null && remainingKm <= 0) || (remainingDays != null && remainingDays <= 0)
        val soon = !overdue && (
            (remainingKm != null && remainingKm <= soonKmThreshold) ||
                (remainingDays != null && remainingDays <= soonDaysThreshold)
            )

        val urgency = when {
            overdue -> CarServiceUrgency.OVERDUE
            soon -> CarServiceUrgency.SOON
            else -> CarServiceUrgency.OK
        }
        return CarServiceStatus(item, urgency, remainingKm, remainingDays)
    }
}
