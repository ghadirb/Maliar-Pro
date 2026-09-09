package com.maliar.pro.database

import android.content.Context
import com.maliar.pro.utils.FoodCatalog
import kotlinx.coroutines.flow.Flow

class FoodPriceManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).foodPriceDao()

    fun getAll(): Flow<List<UserFoodPrice>> = dao.getAll()

    suspend fun find(name: String): UserFoodPrice? {
        val requestedKey = normalize(name)
        if (requestedKey.isBlank()) return null
        val requestedItem = FoodCatalog.matchName(name)
        return dao.getAllList().firstOrNull { price ->
            val priceItem = FoodCatalog.matchName(price.name)
            (requestedItem != null && priceItem?.name == requestedItem.name) ||
                normalize(price.name) == requestedKey ||
                // tolerate punctuation, half-space, plural suffixes and a longer
                // shopping-list description (e.g. «سیب‌زمینی تازه»).
                requestedKey.contains(normalize(price.name)) ||
                normalize(price.name).contains(requestedKey)
        }
    }

    suspend fun upsert(
        name: String,
        pricePerUnit: Double,
        unitLabel: String,
        existingId: Long = 0
    ): Boolean {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isBlank() || !pricePerUnit.isFinite() || pricePerUnit <= 0) return false
        val sameItem = find(cleanName)
        dao.upsert(
            UserFoodPrice(
                id = existingId.takeIf { it > 0 } ?: sameItem?.id ?: 0,
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
        fun normalize(value: String): String = FoodCatalog.canonicalKey(value)
    }
}
