package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    // Cars
    @Query("SELECT * FROM cars ORDER BY createdAt ASC")
    fun getAllCars(): Flow<List<Car>>

    @Query("SELECT * FROM cars ORDER BY createdAt ASC")
    suspend fun getAllCarsList(): List<Car>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getCarById(id: Long): Car?

    @Query("SELECT * FROM cars WHERE id = :id")
    fun getCarByIdFlow(id: Long): Flow<Car?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: Car): Long

    @Update
    suspend fun updateCar(car: Car)

    @Delete
    suspend fun deleteCar(car: Car)

    // Odometer logs
    @Query("SELECT * FROM car_odometer_logs WHERE carId = :carId ORDER BY date DESC")
    fun getOdometerLogs(carId: Long): Flow<List<CarOdometerLog>>

    @Query("SELECT * FROM car_odometer_logs WHERE carId = :carId ORDER BY date DESC")
    suspend fun getOdometerLogsList(carId: Long): List<CarOdometerLog>

    @Insert
    suspend fun insertOdometerLog(log: CarOdometerLog): Long

    // Service items (the schedule / "what's tracked")
    @Query("SELECT * FROM car_service_items WHERE carId = :carId ORDER BY name ASC")
    fun getServiceItems(carId: Long): Flow<List<CarServiceItem>>

    @Query("SELECT * FROM car_service_items WHERE carId = :carId ORDER BY name ASC")
    suspend fun getServiceItemsList(carId: Long): List<CarServiceItem>

    @Query("SELECT * FROM car_service_items")
    suspend fun getAllServiceItemsList(): List<CarServiceItem>

    @Query("SELECT * FROM car_service_items WHERE id = :id")
    suspend fun getServiceItemById(id: Long): CarServiceItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceItem(item: CarServiceItem): Long

    @Update
    suspend fun updateServiceItem(item: CarServiceItem)

    @Delete
    suspend fun deleteServiceItem(item: CarServiceItem)

    // Service logs (the history)
    @Query("SELECT * FROM car_service_logs WHERE carId = :carId ORDER BY date DESC")
    fun getServiceLogs(carId: Long): Flow<List<CarServiceLog>>

    @Query("SELECT * FROM car_service_logs WHERE carId = :carId ORDER BY date DESC")
    suspend fun getServiceLogsList(carId: Long): List<CarServiceLog>

    @Insert
    suspend fun insertServiceLog(log: CarServiceLog): Long

    @Delete
    suspend fun deleteServiceLog(log: CarServiceLog)

    @Query("SELECT COALESCE(SUM(cost), 0) FROM car_service_logs WHERE carId = :carId AND date >= :since")
    suspend fun getTotalCostSince(carId: Long, since: Long): Double
}
