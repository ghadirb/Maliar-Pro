package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val brand: String = "",
    val model: String = "",
    val year: Int? = null,
    val plate: String = "",
    /** The single source of truth for "کیلومتر فعلی" everywhere in the app. Kept in sync by
     *  CarManager.addOdometerReading whenever the person logs a new reading - never edited
     *  directly so it can never drift behind the odometer-log history. */
    val currentOdometerKm: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** One point in a car's odometer history - every reading the person has ever entered,
 *  kept even after Car.currentOdometerKm moves past it, since it's also what powers the
 *  "average km per month" estimate for predicting the next service. */
@Entity(tableName = "car_odometer_logs")
data class CarOdometerLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val odometerKm: Int,
    val date: Long = System.currentTimeMillis()
)

/**
 * A trackable maintenance item for a car (e.g. "روغن موتور", "لاستیک"). Holds the
 * schedule rule (by km, by calendar days, or both - "هرکدام زودتر رسید") and where the
 * item currently stands (last performed at); the next-due point is always *derived* from
 * those two so it's never able to go stale relative to [lastServiceDate]/[lastServiceOdometerKm].
 */
@Entity(tableName = "car_service_items")
data class CarServiceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val name: String,
    val lastServiceDate: Long? = null,
    val lastServiceOdometerKm: Int? = null,
    /** Null = this item isn't tracked by distance. */
    val intervalKm: Int? = null,
    /** Null = this item isn't tracked by calendar time. */
    val intervalDays: Int? = null,
    val lastCost: Double = 0.0,
    /** Id of the ReminderEntity currently open for this item's next-due point, if any -
     *  same idea as ReminderEntity.linkedCheckId/linkedDebtId: lets DueDateReminderWorker
     *  avoid creating a duplicate reminder, and gets cleared (so a fresh one can be made)
     *  every time CarManager.markServiceDone() rolls the schedule forward. */
    val reminderId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val nextDueOdometerKm: Int?
        get() = if (intervalKm != null && lastServiceOdometerKm != null) lastServiceOdometerKm + intervalKm else null

    val nextDueDate: Long?
        get() = if (intervalDays != null && lastServiceDate != null)
            lastServiceDate + intervalDays.toLong() * 24 * 60 * 60 * 1000
        else null
}

/** One historical performed-service record for a car. Deliberately independent of
 *  [CarServiceItem] (only loosely referenced via [serviceItemId]) so the full service
 *  history for a car survives even if the tracked item is later renamed, re-scheduled, or
 *  removed entirely. */
@Entity(tableName = "car_service_logs")
data class CarServiceLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val carId: Long,
    val serviceItemId: Long? = null,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val odometerKm: Int? = null,
    val cost: Double = 0.0,
    val notes: String = "",
    /** Set once this cost is also recorded in the financial system as an Expense (category
     *  "خودرو") - the stage-2 link between this module and AccountingManager. */
    val linkedExpenseId: Long? = null
)
