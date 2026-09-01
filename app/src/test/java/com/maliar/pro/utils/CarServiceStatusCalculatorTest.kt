package com.maliar.pro.utils

import com.maliar.pro.database.CarServiceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CarServiceStatusCalculatorTest {

    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L // fixed reference instant

    private fun oilItem(
        lastKm: Int? = 220_000,
        lastDate: Long? = now - 10 * dayMs,
        intervalKm: Int? = 5_000,
        intervalDays: Int? = 180
    ) = CarServiceItem(
        id = 1, carId = 1, name = "روغن موتور",
        lastServiceOdometerKm = lastKm, lastServiceDate = lastDate,
        intervalKm = intervalKm, intervalDays = intervalDays
    )

    @Test
    fun `km based item still far from due is OK`() {
        val item = oilItem(intervalDays = null) // km-only, so date never triggers
        val status = CarServiceStatusCalculator.compute(item, currentOdometerKm = 221_000, now = now)
        assertEquals(CarServiceUrgency.OK, status.urgency)
        assertEquals(4_000, status.remainingKm)
        assertEquals(null, status.remainingDays)
    }

    @Test
    fun `km based item within threshold is SOON`() {
        val item = oilItem(intervalDays = null)
        val status = CarServiceStatusCalculator.compute(item, currentOdometerKm = 224_500, now = now, soonKmThreshold = 1000)
        assertEquals(CarServiceUrgency.SOON, status.urgency)
        assertEquals(500, status.remainingKm)
    }

    @Test
    fun `km based item past due point is OVERDUE`() {
        val item = oilItem(intervalDays = null)
        val status = CarServiceStatusCalculator.compute(item, currentOdometerKm = 226_000, now = now)
        assertEquals(CarServiceUrgency.OVERDUE, status.urgency)
        assertEquals(-1_000, status.remainingKm)
    }

    @Test
    fun `either-first rule- overdue by date even though km is fine`() {
        // Interval: 5000km or 180 days, whichever first. Last service 200 days ago (date
        // trigger already passed) but odometer barely moved (km trigger nowhere close).
        val item = oilItem(lastDate = now - 200 * dayMs, lastKm = 220_000, intervalKm = 5_000, intervalDays = 180)
        val status = CarServiceStatusCalculator.compute(item, currentOdometerKm = 220_100, now = now)
        assertEquals(CarServiceUrgency.OVERDUE, status.urgency)
    }

    @Test
    fun `item with no interval configured is UNSCHEDULED`() {
        val item = oilItem(intervalKm = null, intervalDays = null)
        val status = CarServiceStatusCalculator.compute(item, currentOdometerKm = 230_000, now = now)
        assertEquals(CarServiceUrgency.UNSCHEDULED, status.urgency)
        assertEquals(null, status.remainingKm)
        assertEquals(null, status.remainingDays)
    }

    @Test
    fun `next due values are derived correctly from last service plus interval`() {
        val item = oilItem(lastKm = 220_000, intervalKm = 5_000, lastDate = now - 10 * dayMs, intervalDays = 180)
        assertEquals(225_000, item.nextDueOdometerKm)
        assertEquals(now - 10 * dayMs + 180 * dayMs, item.nextDueDate)
    }
}
