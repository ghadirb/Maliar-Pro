package com.maliar.pro.billing

/** Unified outcome of a purchase attempt, whatever channel (Bazaar/Myket/direct
 *  gateway) actually handled it. [Success.purchaseToken] must be sent to the backend so
 *  it can verify the purchase with Bazaar/Myket's server-to-server API before actually
 *  granting premium days - never trust the client's word alone for a paid entitlement. */
sealed class PurchaseResult {
    data class Success(
        val sku: String,
        val purchaseToken: String,
        val orderId: String? = null
    ) : PurchaseResult()

    data class Failed(val message: String) : PurchaseResult()
    object Cancelled : PurchaseResult()
}
