package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One row per calendar day (Gregorian, midnight-aligned) with that day's gold/currency
 * rates, so a 30-day trend chart can be drawn (see the "روند نرخ طلا و دلار" section on
 * the reports screen). Written by [com.maliar.pro.utils.FinancialInsightWorker]'s daily
 * run via [com.maliar.pro.database.FinancialStatusManager.recordMarketRateSnapshot];
 * inserting with the same [date] again (e.g. a second run the same day) replaces the
 * existing row rather than duplicating it, thanks to the unique index below.
 */
@Entity(tableName = "market_rate_history", indices = [Index(value = ["date"], unique = true)])
data class MarketRateHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Midnight (00:00) of the Gregorian calendar day this snapshot represents, in millis. */
    val date: Long,
    val gold: Double? = null,
    val currency: Double? = null,
    val coinEmami: Double? = null,
    val coinHalf: Double? = null,
    val coinQuarter: Double? = null,
    val recordedAt: Long = System.currentTimeMillis()
)

@Dao
interface MarketRateHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MarketRateHistory): Long

    @Query("SELECT * FROM market_rate_history WHERE date >= :sinceDate ORDER BY date ASC")
    suspend fun getSince(sinceDate: Long): List<MarketRateHistory>

    @Query("DELETE FROM market_rate_history WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: Long)
}
