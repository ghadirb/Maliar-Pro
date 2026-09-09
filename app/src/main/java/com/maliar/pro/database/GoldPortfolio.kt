package com.maliar.pro.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

enum class GoldAssetKind(val label: String, val isWeighted: Boolean) {
    GOLD_18("طلای ۱۸ عیار", true), GOLD_24("طلای ۲۴ عیار", true), MELTED_GOLD("طلای آب‌شده", true), GOLD_BAR("شمش طلا", true),
    FULL_COIN("سکه تمام (امامی)", false), HALF_COIN("نیم‌سکه", false), QUARTER_COIN("ربع‌سکه", false), GRAM_COIN("سکه گرمی", false),
    BAHAR_COIN("سکه بهار آزادی", false), PARSIAN_COIN("سکه پارسیان", false), OTHER("سایر (مثلاً سکه‌های خاص/سوت)", true)
}
enum class GoldTransactionType { BUY, SELL }

/** Immutable ledger: market refreshes never alter purchase prices or fees. */
@Entity(tableName = "gold_transactions")
data class GoldTransaction(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: GoldAssetKind, val type: GoldTransactionType, val quantity: Double, val unitPrice: Double, val fee: Double = 0.0, val date: Long = System.currentTimeMillis(), val notes: String = "", val accountId: Long? = null)
@Entity(tableName = "gold_price_alerts")
data class GoldPriceAlert(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: GoldAssetKind, val targetPrice: Double, val direction: String, val isActive: Boolean = true, val createdAt: Long = System.currentTimeMillis())

@Dao interface GoldPortfolioDao {
    @Query("SELECT * FROM gold_transactions ORDER BY date DESC") fun transactions(): Flow<List<GoldTransaction>>
    @Query("SELECT * FROM gold_transactions ORDER BY date DESC") suspend fun transactionsList(): List<GoldTransaction>
    @Insert suspend fun insert(transaction: GoldTransaction): Long
    @Delete suspend fun delete(transaction: GoldTransaction)
    @Query("SELECT * FROM gold_price_alerts WHERE isActive = 1") suspend fun activeAlerts(): List<GoldPriceAlert>
    @Insert suspend fun insertAlert(alert: GoldPriceAlert): Long
    @Query("UPDATE gold_price_alerts SET isActive = 0 WHERE id = :id") suspend fun deactivateAlert(id: Long)
}

data class GoldPosition(val kind: GoldAssetKind, val quantity: Double, val invested: Double, val currentUnitPrice: Double) {
    val currentValue get() = quantity * currentUnitPrice; val profit get() = currentValue - invested
    val returnPercent get() = if (invested > 0) profit / invested * 100 else 0.0; val averageCost get() = if (quantity > 0) invested / quantity else 0.0
}

class GoldPortfolioManager(context: Context) {
    private val appContext = context.applicationContext; private val database = AppDatabase.getDatabase(appContext)
    private val dao = database.goldPortfolioDao(); private val assets = database.financialStatusDao()
    fun transactions(): Flow<List<GoldTransaction>> = dao.transactions()
    suspend fun add(tx: GoldTransaction): Long { require(tx.quantity > 0 && tx.unitPrice >= 0); val id = dao.insert(tx); FinancialStatusManager(appContext).adjustAssetBalance(tx.accountId, if (tx.type == GoldTransactionType.BUY) -(tx.quantity * tx.unitPrice + tx.fee) else tx.quantity * tx.unitPrice - tx.fee); syncAsset(tx.kind); return id }
    suspend fun delete(tx: GoldTransaction) { dao.delete(tx); FinancialStatusManager(appContext).adjustAssetBalance(tx.accountId, if (tx.type == GoldTransactionType.BUY) tx.quantity * tx.unitPrice + tx.fee else -(tx.quantity * tx.unitPrice - tx.fee)); syncAsset(tx.kind) }
    suspend fun positions(): List<GoldPosition> = GoldAssetKind.values().mapNotNull { position(it) }
    suspend fun position(kind: GoldAssetKind): GoldPosition? {
        var quantity = 0.0; var invested = 0.0
        dao.transactionsList().filter { it.kind == kind }.sortedBy { it.date }.forEach { tx ->
            if (tx.type == GoldTransactionType.BUY) { quantity += tx.quantity; invested += tx.quantity * tx.unitPrice + tx.fee }
            else if (quantity > 0) { val sold = tx.quantity.coerceAtMost(quantity); invested -= invested / quantity * sold; quantity -= sold }
        }
        return if (quantity > 0) GoldPosition(kind, quantity, invested.coerceAtLeast(0.0), currentPrice(kind)) else null
    }
    suspend fun currentPrice(kind: GoldAssetKind): Double {
        val r = com.maliar.pro.utils.MarketRateClient(appContext).fetch(); val gold = (r?.gold ?: 0.0) / com.maliar.pro.utils.MarketRateClient.RIAL_TO_TOMAN
        val live = when (kind) {
            GoldAssetKind.FULL_COIN -> (r?.coinEmami ?: 0.0) / 10
            GoldAssetKind.HALF_COIN -> (r?.coinHalf ?: 0.0) / 10
            GoldAssetKind.QUARTER_COIN -> (r?.coinQuarter ?: 0.0) / 10
            GoldAssetKind.BAHAR_COIN -> (r?.coinBahar ?: 0.0) / 10
            GoldAssetKind.GRAM_COIN -> (r?.coinGerami ?: 0.0) / 10
            GoldAssetKind.GOLD_24 -> gold * 24 / 18
            GoldAssetKind.GOLD_18, GoldAssetKind.MELTED_GOLD, GoldAssetKind.GOLD_BAR -> gold
            GoldAssetKind.PARSIAN_COIN, GoldAssetKind.OTHER -> 0.0
        }
        // The public rate feed doesn't cover every coin (Parsian coins in particular aren't
        // published there at all). Rather than guessing with an unrelated gold/coin price,
        // fall back to the person's own most recent trade for that exact kind.
        return if (live > 0) live else lastKnownPrice(kind)
    }
    private suspend fun lastKnownPrice(kind: GoldAssetKind): Double =
        dao.transactionsList().filter { it.kind == kind }.maxByOrNull { it.date }?.unitPrice ?: 0.0
    suspend fun syncAllAssets() { GoldAssetKind.values().forEach { syncAsset(it) }; checkAlerts() }
    private suspend fun syncAsset(kind: GoldAssetKind) {
        val position = position(kind); val title = "کیف طلا: ${kind.label}"; val existing = assets.getAllAssetsList().firstOrNull { it.title == title }
        if (position == null) { if (existing != null) assets.deleteAsset(existing); return }
        if (existing == null) assets.insertAsset(Asset(type = AssetType.GOLD, title = title, value = position.currentValue, description = "خودکار از کیف طلا")) else assets.updateAsset(existing.copy(value = position.currentValue, updatedAt = System.currentTimeMillis()))
    }
    suspend fun addAlert(kind: GoldAssetKind, target: Double, above: Boolean) = dao.insertAlert(GoldPriceAlert(kind = kind, targetPrice = target, direction = if (above) "ABOVE" else "BELOW"))
    private suspend fun checkAlerts() { dao.activeAlerts().forEach { a -> val p = currentPrice(a.kind); val hit = if (a.direction == "ABOVE") p >= a.targetPrice else p <= a.targetPrice; if (hit) { com.maliar.pro.utils.NotificationHelper.notifyFinancialInsight(appContext, "هشدار ${a.kind.label}: قیمت به ${com.maliar.pro.utils.CurrencyFormatter.format(a.targetPrice)} رسید."); dao.deactivateAlert(a.id) } } }
}
