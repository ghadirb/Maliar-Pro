package com.maliar.pro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [amount] always represents the *real cash* that moved into [accountId] - this is what
 * keeps account balances accurate, and is untouched by the fields below.
 *
 * [isProductSale] / [costOfGoods] let a "فروش کالا" (product sale, as opposed to plain
 * "درآمد خدمات") record its *profit* separately from the cash that came in: e.g. selling
 * a 150,000 toman item that cost 80,000 toman deposits the full 150,000 into the account
 * (via [amount], same as always) while only 70,000 of that is real profit. Both fields
 * default to "off" (false / 0), so every pre-existing row and every plain service income
 * behaves exactly as before - [profit] just equals [amount] for them.
 */
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val date: Long,
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val accountId: Long? = null,
    /** True when this income is a "فروش کالا" (product sale) rather than plain service
     *  income - controls whether [costOfGoods] participates in profit calculations. */
    val isProductSale: Boolean = false,
    /** بهای تمام‌شده کالا - only meaningful when [isProductSale] is true. Never affects
     *  [amount]/account balance; only reduces this entry's contribution to profit reports. */
    val costOfGoods: Double = 0.0,
    /** Explicit product-sale details. Defaults preserve all v1.9 entries. */
    val productName: String = "",
    val productQuantity: Double = 1.0,
    val listPrice: Double = 0.0,
    val discountAmount: Double = 0.0
) {
    /** سود واقعی این تراکنش: for a product sale this is amount - costOfGoods (can be
     *  negative on a loss-making sale); for plain service income it's the full amount,
     *  matching the app's behavior before this field existed. */
    val profit: Double
        get() = if (isProductSale) amount - costOfGoods else amount
}
