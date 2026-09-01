package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CarManager(context: Context) {

    private val dao = AppDatabase.getDatabase(context).carDao()
    private val accountingManager = AccountingManager(context)

    /** Category shown in the general Expense list / financial reports for every cost this
     *  module links there - item #8 of the spec ("در دسته «خودرو» ثبت شود"). */
    companion object {
        const val EXPENSE_CATEGORY = "خودرو"
    }

    // Cars
    fun getAllCars(): Flow<List<Car>> = dao.getAllCars()
    suspend fun getAllCarsList(): List<Car> = dao.getAllCarsList()
    suspend fun getCarById(id: Long): Car? = dao.getCarById(id)
    fun getCarByIdFlow(id: Long): Flow<Car?> = dao.getCarByIdFlow(id)
    suspend fun addCar(car: Car): Long = dao.insertCar(car)
    suspend fun updateCar(car: Car) = dao.updateCar(car)
    suspend fun deleteCar(car: Car) = dao.deleteCar(car)

    // Odometer
    fun getOdometerLogs(carId: Long): Flow<List<CarOdometerLog>> = dao.getOdometerLogs(carId)
    suspend fun getOdometerLogsList(carId: Long): List<CarOdometerLog> = dao.getOdometerLogsList(carId)

    /** Records a new odometer reading and keeps Car.currentOdometerKm in sync, so every
     *  screen that shows "کیلومتر فعلی" can just read it straight off the Car row instead
     *  of re-deriving it from the log history each time. Ignores a reading lower than what's
     *  already on file (a typo, or an odometer replacement) rather than letting the car's
     *  current km go backwards. */
    suspend fun addOdometerReading(carId: Long, km: Int) {
        dao.insertOdometerLog(CarOdometerLog(carId = carId, odometerKm = km))
        val car = dao.getCarById(carId) ?: return
        if (km > car.currentOdometerKm) {
            dao.updateCar(car.copy(currentOdometerKm = km))
        }
    }

    /** Rough "km driven per month" estimate from the odometer history, used only for the
     *  "تخمین می‌زنیم حدود N هفته دیگر به سرویس بعدی می‌رسید" hint - never for the actual
     *  due calculation itself, which always stays exact (interval-based). Needs at least
     *  two readings spread across at least a day to produce a meaningful number. */
    suspend fun estimateKmPerMonth(carId: Long): Double? {
        val logs = dao.getOdometerLogsList(carId).sortedBy { it.date }
        if (logs.size < 2) return null
        val first = logs.first()
        val last = logs.last()
        val daySpan = (last.date - first.date) / (24.0 * 60 * 60 * 1000)
        if (daySpan < 1.0) return null
        val kmSpan = last.odometerKm - first.odometerKm
        if (kmSpan <= 0) return null
        return kmSpan / daySpan * 30.0
    }

    // Service items (schedule)
    fun getServiceItems(carId: Long): Flow<List<CarServiceItem>> = dao.getServiceItems(carId)
    suspend fun getServiceItemsList(carId: Long): List<CarServiceItem> = dao.getServiceItemsList(carId)
    suspend fun getAllServiceItemsList(): List<CarServiceItem> = dao.getAllServiceItemsList()
    suspend fun getServiceItemById(id: Long): CarServiceItem? = dao.getServiceItemById(id)
    suspend fun addServiceItem(item: CarServiceItem): Long = dao.insertServiceItem(item)
    suspend fun updateServiceItem(item: CarServiceItem) = dao.updateServiceItem(item)
    suspend fun deleteServiceItem(item: CarServiceItem) = dao.deleteServiceItem(item)

    // Service logs (history)
    fun getServiceLogs(carId: Long): Flow<List<CarServiceLog>> = dao.getServiceLogs(carId)
    suspend fun getTotalMaintenanceCostSince(carId: Long, since: Long): Double = dao.getTotalCostSince(carId, since)

    /** Deletes a history row and, if it was linked to a financial Expense, deletes that
     *  Expense too - keeps the two in sync in both directions. */
    suspend fun deleteServiceLog(log: CarServiceLog) {
        dao.deleteServiceLog(log)
        log.linkedExpenseId?.let { expenseId ->
            accountingManager.getAllExpensesList().find { it.id == expenseId }?.let {
                accountingManager.deleteExpense(it)
            }
        }
    }

    /** If [cost] > 0 and [linkToFinance] is true, records the same amount as a general
     *  Expense (category [EXPENSE_CATEGORY]) so it shows up in the accounting screen and
     *  financial reports too - item #8 of the spec. Returns the id to store as
     *  CarServiceLog.linkedExpenseId, or null if nothing was linked. */
    private suspend fun maybeLinkToFinance(title: String, date: Long, cost: Double, linkToFinance: Boolean): Long? {
        if (!linkToFinance || cost <= 0) return null
        return accountingManager.addExpense(
            Expense(amount = cost, description = title, date = date, category = EXPENSE_CATEGORY)
        )
    }

    /**
     * The core "انجام شد" action for a tracked service item: writes one history row (today,
     * or the [date]/[odometerKm] the person supplied), then rolls the item's own
     * last-service-date/km forward so its derived next-due point moves out automatically -
     * and clears its old [CarServiceItem.reminderId] so DueDateReminderWorker knows to
     * create a fresh reminder for the new due point rather than treating this item as
     * already covered by the (now stale) old one.
     */
    suspend fun markServiceDone(
        item: CarServiceItem,
        date: Long = System.currentTimeMillis(),
        odometerKm: Int? = null,
        cost: Double = 0.0,
        notes: String = "",
        linkToFinance: Boolean = true
    ): Long {
        val car = dao.getCarById(item.carId)
        val effectiveKm = odometerKm ?: car?.currentOdometerKm

        val expenseId = maybeLinkToFinance(item.name, date, cost, linkToFinance)
        val logId = dao.insertServiceLog(
            CarServiceLog(
                carId = item.carId,
                serviceItemId = item.id,
                title = item.name,
                date = date,
                odometerKm = effectiveKm,
                cost = cost,
                notes = notes,
                category = CarLogCategory.SERVICE.name,
                linkedExpenseId = expenseId
            )
        )

        dao.updateServiceItem(
            item.copy(
                lastServiceDate = date,
                lastServiceOdometerKm = effectiveKm ?: item.lastServiceOdometerKm,
                lastCost = if (cost > 0) cost else item.lastCost,
                reminderId = null
            )
        )

        if (effectiveKm != null && car != null && effectiveKm > car.currentOdometerKm) {
            dao.updateCar(car.copy(currentOdometerKm = effectiveKm))
        }

        return logId
    }

    /** A one-off cost not tied to any tracked schedule item - a repair, a part bought on
     *  its own, an insurance payment, etc (item #8 of the spec: "اگر کاربر هزینه‌ای را برای
     *  خودرو ثبت کند"). Same optional link-to-finance behaviour as [markServiceDone]. */
    suspend fun addManualCost(
        carId: Long,
        title: String,
        category: CarLogCategory,
        date: Long = System.currentTimeMillis(),
        odometerKm: Int? = null,
        cost: Double = 0.0,
        notes: String = "",
        linkToFinance: Boolean = true
    ): Long {
        val expenseId = maybeLinkToFinance(title, date, cost, linkToFinance)
        return dao.insertServiceLog(
            CarServiceLog(
                carId = carId,
                title = title,
                date = date,
                odometerKm = odometerKm,
                cost = cost,
                notes = notes,
                category = category.name,
                linkedExpenseId = expenseId
            )
        )
    }
}
