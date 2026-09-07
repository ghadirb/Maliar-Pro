package com.maliar.pro.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "market_products")
data class MarketProduct(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val category: String = "", val brand: String = "", val model: String = "", val barcode: String = "", val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "market_price_quotes", indices = [Index(value = ["productId", "checkedAt"])])
data class MarketPriceQuote(@PrimaryKey(autoGenerate = true) val id: Long = 0, val productId: Long, val source: String, val priceType: String, val price: Double, val minPrice: Double? = null, val maxPrice: Double? = null, val checkedAt: Long = System.currentTimeMillis(), val confidence: Double = .5, val sourceUrl: String = "")

@Entity(tableName = "product_purchases", indices = [Index(value = ["productId", "purchasedAt"])])
data class ProductPurchase(@PrimaryKey(autoGenerate = true) val id: Long = 0, val productId: Long, val purchasePrice: Double, val quantity: Double = 1.0, val purchasedAt: Long = System.currentTimeMillis(), val supplier: String = "")

@Entity(tableName = "market_sources")
data class MarketSource(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val url: String, val priceType: String, val isEnabled: Boolean = true, val createdAt: Long = System.currentTimeMillis())

@Dao interface MarketAssistantDao {
    @Query("SELECT * FROM market_products ORDER BY createdAt DESC") fun products(): Flow<List<MarketProduct>>
    @Query("SELECT * FROM market_sources ORDER BY createdAt DESC") fun sources(): Flow<List<MarketSource>>
    @Query("SELECT * FROM market_price_quotes WHERE productId = :productId ORDER BY checkedAt DESC") fun quotes(productId: Long): Flow<List<MarketPriceQuote>>
    @Query("SELECT * FROM product_purchases WHERE productId = :productId ORDER BY purchasedAt DESC") fun purchases(productId: Long): Flow<List<ProductPurchase>>
    @Insert suspend fun addProduct(item: MarketProduct): Long
    @Insert suspend fun addQuote(item: MarketPriceQuote): Long
    @Insert suspend fun addPurchase(item: ProductPurchase): Long
    @Insert suspend fun addSource(item: MarketSource): Long
    @Update suspend fun updateProduct(item: MarketProduct)
    @Update suspend fun updateSource(item: MarketSource)
    @Query("DELETE FROM market_products WHERE id = :id") suspend fun deleteProduct(id: Long)
    @Query("DELETE FROM market_price_quotes WHERE productId = :id") suspend fun deleteQuotesForProduct(id: Long)
    @Query("DELETE FROM product_purchases WHERE productId = :id") suspend fun deletePurchasesForProduct(id: Long)
    @Query("DELETE FROM market_sources WHERE id = :id") suspend fun deleteSource(id: Long)
}

class MarketAssistantManager(context: Context) {
    private val dao = AppDatabase.getDatabase(context).marketAssistantDao()
    fun products() = dao.products(); fun sources() = dao.sources(); fun quotes(id: Long) = dao.quotes(id); fun purchases(id: Long) = dao.purchases(id)
    suspend fun addProduct(name: String, category: String, brand: String, model: String, barcode: String): Long = dao.addProduct(MarketProduct(name = name.trim(), category = category.trim(), brand = brand.trim(), model = model.trim(), barcode = barcode.trim()))
    suspend fun updateProduct(product: MarketProduct, name: String, category: String, brand: String, model: String, barcode: String) = dao.updateProduct(product.copy(name = name.trim(), category = category.trim(), brand = brand.trim(), model = model.trim(), barcode = barcode.trim()))
    suspend fun deleteProduct(productId: Long) { dao.deleteQuotesForProduct(productId); dao.deletePurchasesForProduct(productId); dao.deleteProduct(productId) }
    suspend fun addPurchase(productId: Long, price: Double, quantity: Double, supplier: String) = dao.addPurchase(ProductPurchase(productId = productId, purchasePrice = price, quantity = quantity, supplier = supplier.trim()))
    suspend fun addSource(name: String, url: String, priceType: String) = dao.addSource(MarketSource(name = name.trim(), url = url.trim(), priceType = priceType))
    suspend fun updateSource(source: MarketSource, name: String, url: String, priceType: String, isEnabled: Boolean) = dao.updateSource(source.copy(name = name.trim(), url = url.trim(), priceType = priceType, isEnabled = isEnabled))
    suspend fun deleteSource(sourceId: Long) = dao.deleteSource(sourceId)
    suspend fun addQuote(productId: Long, source: String, priceType: String, price: Double, min: Double? = null, max: Double? = null, confidence: Double = .6) = dao.addQuote(MarketPriceQuote(productId = productId, source = source.trim(), priceType = priceType, price = price, minPrice = min, maxPrice = max, confidence = confidence))
    companion object {
        fun recommendation(purchases: List<ProductPurchase>, quotes: List<MarketPriceQuote>): String {
            val wholesale = quotes.filter { it.priceType == "WHOLESALE" }
            val retail = quotes.filter { it.priceType == "RETAIL" }
            val last = purchases.firstOrNull()?.purchasePrice
            val market = (wholesale.ifEmpty { quotes }).map { it.price }.average().takeIf { !it.isNaN() }
            if (market == null) return "هنوز قیمت بازار ثبت نشده است. ابتدا یک قیمت یا پیام فروشنده اضافه کنید."
            val target = market * .97
            val retailAverage = retail.map { it.price }.average().takeIf { !it.isNaN() }
            return buildString {
                append("میانگین قیمت عمده: ${market.toLong()} تومان\nپیشنهاد خرید: حداکثر ${target.toLong()} تومان")
                if (last != null) append("\nآخرین خرید شما: ${last.toLong()} تومان (${if (last > market) "بالاتر" else "پایین‌تر"} از میانگین بازار)")
                if (retailAverage != null && retailAverage > market) append("\nحاشیه فروش خرده نسبت به عمده: ${(((retailAverage - market) / market) * 100).toInt()}٪")
            }
        }
        /** Safe first-pass parser for pasted channel messages; AI enrichment belongs on the proxy. */
        fun pricesFromText(text: String): List<Double> = Regex("(?<!\\d)([0-9۰-۹][0-9۰-۹,٫]*)(?:\\s*(?:تومان|ت))?").findAll(text).mapNotNull { m -> m.groupValues[1].replace("٫", "").replace(",", "").map { c -> if (c in '۰'..'۹') ('0'.code + c.code - '۰'.code).toChar() else c }.joinToString("").toDoubleOrNull() }.filter { it >= 1000 }.toList()
    }
}
