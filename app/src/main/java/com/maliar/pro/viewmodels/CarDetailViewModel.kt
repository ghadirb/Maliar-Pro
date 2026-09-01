package com.maliar.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maliar.pro.database.Car
import com.maliar.pro.database.CarManager
import com.maliar.pro.database.CarServiceItem
import com.maliar.pro.database.CarServiceLog
import com.maliar.pro.utils.CarServiceStatusCalculator
import com.maliar.pro.utils.CarServiceUrgency
import com.maliar.pro.utils.PersianCalendarHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CarCostSummary(val thisMonth: Double = 0.0, val thisYear: Double = 0.0, val total: Double = 0.0)

class CarDetailViewModel(
    private val carManager: CarManager,
    private val carId: Long
) : ViewModel() {

    val car = carManager.getCarByIdFlow(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serviceItems = carManager.getServiceItems(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceLogs = carManager.getServiceLogs(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every service item paired with its computed status, sorted so the most urgent one
     *  (overdue, then soon, then ok, then never-scheduled) always shows first - exactly
     *  what both this screen and a future "مواردی که باید حواسم باشد" cross-car screen need. */
    val serviceStatuses = combine(serviceItems, car) { items, carValue ->
        val km = carValue?.currentOdometerKm ?: 0
        items.map { CarServiceStatusCalculator.compute(it, km) }
            .sortedBy { urgencySortOrder(it.urgency) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val costSummary = serviceLogs.map { logs -> buildCostSummary(logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CarCostSummary())

    private fun urgencySortOrder(urgency: CarServiceUrgency) = when (urgency) {
        CarServiceUrgency.OVERDUE -> 0
        CarServiceUrgency.SOON -> 1
        CarServiceUrgency.OK -> 2
        CarServiceUrgency.UNSCHEDULED -> 3
    }

    private fun buildCostSummary(logs: List<CarServiceLog>): CarCostSummary {
        val monthStart = PersianCalendarHelper.currentFinancialPeriodStartMillis(1)
        val (jy, _, _) = PersianCalendarHelper.getCurrentJalaliDate()
        val yearStart = PersianCalendarHelper.jalaliToGregorianMillis(jy, 1, 1)
        return CarCostSummary(
            thisMonth = logs.filter { it.date >= monthStart }.sumOf { it.cost },
            thisYear = logs.filter { it.date >= yearStart }.sumOf { it.cost },
            total = logs.sumOf { it.cost }
        )
    }

    fun updateCar(update: (Car) -> Car) {
        val current = car.value ?: return
        viewModelScope.launch { carManager.updateCar(update(current)) }
    }

    fun addOdometerReading(km: Int) {
        viewModelScope.launch { carManager.addOdometerReading(carId, km) }
    }

    fun addServiceItem(item: CarServiceItem) {
        viewModelScope.launch { carManager.addServiceItem(item.copy(carId = carId)) }
    }

    fun updateServiceItem(item: CarServiceItem) {
        viewModelScope.launch { carManager.updateServiceItem(item) }
    }

    fun deleteServiceItem(item: CarServiceItem) {
        viewModelScope.launch { carManager.deleteServiceItem(item) }
    }

    fun markServiceDone(item: CarServiceItem, odometerKm: Int?, cost: Double, notes: String) {
        viewModelScope.launch { carManager.markServiceDone(item, odometerKm = odometerKm, cost = cost, notes = notes) }
    }
}

class CarDetailViewModelFactory(
    private val carManager: CarManager,
    private val carId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CarDetailViewModel(carManager, carId) as T
}
