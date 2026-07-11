package com.maliar.pro.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.billing.BazaarBillingHelper
import com.maliar.pro.billing.MyketBillingHelper
import com.maliar.pro.billing.PurchaseResult
import com.maliar.pro.billing.StoreChannel
import com.maliar.pro.utils.SubscriptionManager
import kotlinx.coroutines.launch

/** Lets a user refresh, purchase, and recover their premium entitlement. */
class SubscriptionActivity : AppCompatActivity() {
    private var storeChannel = StoreChannel.DIRECT
    private var storeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        storeChannel = SubscriptionManager.detectStoreChannel(this)
        connectToStoreIfNeeded()
        findViewById<android.view.View>(R.id.monthlyPlanButton).setOnClickListener {
            startCheckout(SubscriptionManager.Plan.MONTHLY)
        }
        findViewById<android.view.View>(R.id.yearlyPlanButton).setOnClickListener {
            startCheckout(SubscriptionManager.Plan.YEARLY)
        }
        findViewById<android.view.View>(R.id.refreshStatusButton).setOnClickListener {
            refreshStatus(showToast = true)
        }
        refreshStatus(showToast = false)
    }

    private fun connectToStoreIfNeeded() {
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.connect(this, { storeReady = it }, ::verifyAndGrantStorePurchase)
            StoreChannel.MYKET -> MyketBillingHelper.connect(this, { storeReady = it }, ::verifyAndGrantStorePurchase)
            StoreChannel.DIRECT -> Unit
        }
    }

    override fun onDestroy() {
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.disconnect()
            StoreChannel.MYKET -> MyketBillingHelper.disconnect()
            StoreChannel.DIRECT -> Unit
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.handleActivityResult(requestCode, resultCode, data)
            StoreChannel.MYKET -> MyketBillingHelper.handleActivityResult(requestCode, resultCode, data)
            StoreChannel.DIRECT -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(showToast = false)
    }

    private fun refreshStatus(showToast: Boolean) {
        val statusView = findViewById<TextView>(R.id.statusText)
        lifecycleScope.launch {
            SubscriptionManager.refreshFromServer(this@SubscriptionActivity)
            renderStatus(statusView)
            if (showToast) Toast.makeText(this@SubscriptionActivity, "وضعیت اشتراک به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
        }
        renderStatus(statusView)
    }

    private fun renderStatus(statusView: TextView) {
        statusView.text = SubscriptionManager.premiumExpiryLabel(this)
            ?: if (SubscriptionManager.hasPersonalKey(this)) {
                "شما از کلید هوش مصنوعی شخصی خود استفاده می‌کنید و محدودیتی ندارید."
            } else {
                "${SubscriptionManager.remainingFreeLifetime(this)} از ${SubscriptionManager.FREE_AI_LIFETIME_LIMIT} پیام رایگان اولیه باقی مانده است."
            }
    }

    private fun startCheckout(plan: SubscriptionManager.Plan) {
        when (storeChannel) {
            StoreChannel.DIRECT -> startDirectCheckout(plan)
            StoreChannel.BAZAAR, StoreChannel.MYKET -> startStoreCheckout(plan)
        }
    }

    private fun startStoreCheckout(plan: SubscriptionManager.Plan) {
        if (!storeReady) {
            Toast.makeText(this, "اتصال به فروشگاه برقرار نشده است. دوباره تلاش کنید.", Toast.LENGTH_LONG).show()
            return
        }
        val deviceId = com.maliar.pro.utils.PreferencesManager(this).getOrCreateDeviceId()
        val sku = when (plan) {
            SubscriptionManager.Plan.MONTHLY -> BazaarBillingHelper.SKU_MONTHLY
            SubscriptionManager.Plan.YEARLY -> BazaarBillingHelper.SKU_YEARLY
        }
        val onResult: (PurchaseResult) -> Unit = { result ->
            when (result) {
                is PurchaseResult.Success -> verifyAndGrantStorePurchase(result)
                is PurchaseResult.Cancelled -> Unit
                is PurchaseResult.Failed -> Toast.makeText(this, "خرید ناموفق بود: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.purchase(this, sku, deviceId, onResult)
            StoreChannel.MYKET -> MyketBillingHelper.purchase(this, sku, deviceId, onResult)
            StoreChannel.DIRECT -> Unit
        }
    }

    /** Server verification comes before consumption, so an interrupted purchase survives. */
    private fun verifyAndGrantStorePurchase(result: PurchaseResult.Success) {
        val plan = when (result.sku) {
            BazaarBillingHelper.SKU_MONTHLY -> SubscriptionManager.Plan.MONTHLY
            BazaarBillingHelper.SKU_YEARLY -> SubscriptionManager.Plan.YEARLY
            else -> return
        }
        lifecycleScope.launch {
            val verified = SubscriptionManager.verifyStorePurchase(this@SubscriptionActivity, storeChannel, plan, result.purchaseToken)
            if (verified) {
                when (storeChannel) {
                    StoreChannel.BAZAAR -> BazaarBillingHelper.consume(result.purchaseToken) { }
                    StoreChannel.MYKET -> MyketBillingHelper.consume(result.purchaseToken) { }
                    StoreChannel.DIRECT -> Unit
                }
            }
            renderStatus(findViewById(R.id.statusText))
            Toast.makeText(
                this@SubscriptionActivity,
                if (verified) "پرداخت با موفقیت تایید شد" else "تایید پرداخت ناموفق بود؛ خرید برای بررسی دوباره نگه داشته شد",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startDirectCheckout(plan: SubscriptionManager.Plan) {
        lifecycleScope.launch {
            val paymentUrl = SubscriptionManager.requestPayment(this@SubscriptionActivity, plan)
            if (paymentUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
            } else {
                Toast.makeText(this@SubscriptionActivity, "اتصال به درگاه پرداخت برقرار نشد.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
