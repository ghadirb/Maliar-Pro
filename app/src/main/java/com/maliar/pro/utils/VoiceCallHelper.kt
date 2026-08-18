package com.maliar.pro.utils

import android.content.Context

/**
 * Phone-call functionality is intentionally disabled in the Play-safe test build.
 * Kept as a compatibility shim for existing UI/view-model code so the rest of the
 * application can compile without CALL_PHONE/READ_PHONE_STATE permissions.
 */
object VoiceCallHelper {
    enum class CallResult {
        CALLED_DIRECTLY,
        OPENED_DIALER_NO_PERMISSION,
        FAILED
    }

    fun makeCallWithResult(context: Context, phoneNumber: String): CallResult =
        CallResult.FAILED

    fun openDialer(context: Context, phoneNumber: String) {
        // Direct phone calling is disabled in this test build.
    }
}
