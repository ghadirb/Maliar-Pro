package com.maliar.pro.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.R
import com.maliar.pro.utils.SubscriptionManager
import kotlinx.coroutines.launch

/**
 * Lets the person see their current AI-usage entitlement (free daily limit vs premium)
 * and start a Zarinpal payment for a monthly/yearly plan. The actual payment + activation
 * happens on the backend (see /server) - this screen only requests a payment URL and
 * opens it in the browser, then lets the person pull the resulting status back down.
 */
class SubscriptionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

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

    override fun onResume() {
        super.onResume()
        // Coming back from the browser after finishing (or abandoning) a payment - refresh
        // so success shows up immediately without the person needing to tap anything.
        refreshStatus(showToast = false)
    }

    private fun refreshStatus(showToast: Boolean) {
        val statusView = findViewById<android.widget.TextView>(R.id.statusText)
        lifecycleScope.launch {
            SubscriptionManager.refreshFromServer(this@SubscriptionActivity)
            renderStatus(statusView)
            if (showToast) {
                Toast.makeText(this@SubscriptionActivity, "وضعیت اشتراک به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
            }
        }
        // Render immediately from cache too, so it's never stuck on "در حال بررسی..." if
        // the network call above is slow or offline.
        renderStatus(statusView)
    }

    private fun renderStatus(statusView: android.widget.TextView) {
        val premiumLabel = SubscriptionManager.premiumExpiryLabel(this)
        statusView.text = when {
            premiumLabel != null -> premiumLabel
            SubscriptionManager.hasPersonalKey(this) ->
                "شما در حال استفاده از کلید هوش مصنوعی شخصی خودتان هستید - بدون محدودیت روزانه."
            else -> {
                val remaining = SubscriptionManager.remainingFreeToday(this)
                "امروز $remaining از ${SubscriptionManager.FREE_DAILY_AI_LIMIT} پیام رایگان باقی مانده است."
            }
        }
    }

    private fun startCheckout(plan: SubscriptionManager.Plan) {
        lifecycleScope.launch {
            Toast.makeText(this@SubscriptionActivity, "در حال اتصال به درگاه پرداخت...", Toast.LENGTH_SHORT).show()
            val paymentUrl = SubscriptionManager.requestPayment(this@SubscriptionActivity, plan)
            if (paymentUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
            } else {
                Toast.makeText(
                    this@SubscriptionActivity,
                    "اتصال به درگاه پرداخت برقرار نشد. اتصال اینترنت را بررسی کنید یا بعداً دوباره تلاش کنید.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
