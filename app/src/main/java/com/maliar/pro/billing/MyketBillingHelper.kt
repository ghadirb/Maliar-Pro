package com.maliar.pro.billing

import android.app.Activity
import com.maliar.pro.BuildConfig

/** Billing adapter for the `myket` product flavor. */
object MyketBillingHelper {
    const val SKU_MONTHLY = "maliar_pro_monthly"
    const val SKU_YEARLY = "maliar_pro_yearly"

    private val delegate = StoreBillingHelper { BuildConfig.IAB_PUBLIC_KEY }

    fun connect(
        activity: Activity,
        onReady: (Boolean) -> Unit,
        onPendingPurchase: (PurchaseResult.Success) -> Unit
    ) = delegate.connect(activity, onReady, onPendingPurchase)

    fun disconnect() = delegate.disconnect()

    fun purchase(activity: Activity, sku: String, payload: String, onResult: (PurchaseResult) -> Unit) =
        delegate.purchase(activity, sku, payload, onResult)

    fun consume(purchaseToken: String, onResult: (Boolean) -> Unit) =
        delegate.consume(purchaseToken, onResult)

}
