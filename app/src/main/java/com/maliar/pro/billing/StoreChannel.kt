package com.maliar.pro.billing

import com.maliar.pro.BuildConfig

/**
 * Which channel this particular APK was built for. Bazaar and Myket require different
 * billing-service bindings and public keys, so the official integration uses separate
 * Gradle flavors rather than guessing from the installer package at runtime.
 */
enum class StoreChannel(val apiValue: String) {
    BAZAAR("bazaar"),
    MYKET("myket"),
    DIRECT("direct");

    companion object {
        fun current(): StoreChannel = when (BuildConfig.STORE_CHANNEL) {
            BAZAAR.apiValue -> BAZAAR
            MYKET.apiValue -> MYKET
            else -> DIRECT
        }
    }
}
