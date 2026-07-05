package com.maliar.pro.models

import com.google.gson.annotations.SerializedName

data class APIKey(
    @SerializedName("provider")
    val provider: AIProvider,
    
    @SerializedName("key")
    val key: String,
    
    @SerializedName("baseUrl")
    val baseUrl: String? = null,
    
    @SerializedName("isActive")
    val isActive: Boolean = false,

    // True only for keys fetched automatically by AutoProvisioningManager (the shared,
    // free pool this app pays for). False (the default) means the person typed this key
    // in themselves in "کلیدهای هوش مصنوعی", so it's their own account/their own cost -
    // SubscriptionManager uses this to grant unlimited AI usage to anyone using their own
    // key, since none of that usage costs the app owner anything.
    @SerializedName("isAutoProvisioned")
    val isAutoProvisioned: Boolean = false
)

enum class AIProvider {
    OPENAI,
    ANTHROPIC,
    OPENROUTER,
    AIML,
    GLADIA,
    LIARA,
    GAPGPT,
    CUSTOM,
    IVIRA,
    AVALAI
}
