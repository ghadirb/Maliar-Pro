package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class FoodPriceManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).foodPriceDao()

    fun getAll(): Flow<List<UserFoodPrice>> = dao.getAll()

    suspend fun find(name: String): UserFoodPrice? =
        dao.findByNormalizedName(normalize(name))

    suspend fun upsert(
        name: String,
        pricePerUnit: Double,
        unitLabel: String,
        existingId: Long = 0
    ): Boolean {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isBlank() || !pricePerUnit.isFinite() || pricePerUnit <= 0) return false
        dao.upsert(
            UserFoodPrice(
                id = existingId,
                name = cleanName,
                normalizedName = normalize(cleanName),
                pricePerUnit = pricePerUnit,
                unitLabel = unitLabel.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun delete(item: UserFoodPrice) = dao.delete(item)

    companion object {
        fun normalize(value: String): String = value.trim()
            .lowercase(Locale.ROOT)
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("\\s+"), " ")
    }
}
