package com.maliar.pro.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

object VoiceCallHelper {
    
    private const val TAG = "VoiceCallHelper"

    /** Result of a call attempt so the assistant can explain what actually happened. */
    enum class CallResult { CALLED_DIRECTLY, OPENED_DIALER_NO_PERMISSION, FAILED }

    /**
     * Make a phone call to the given number. CALL_PHONE is a dangerous runtime permission
     * on Android 6+ - if it hasn't been granted (declaring it in the manifest is not
     * enough), ACTION_CALL throws a SecurityException. Rather than let that crash or
     * silently fail, fall back to ACTION_DIAL, which opens the dialer pre-filled with the
     * number and needs no permission at all, so the assistant is never a dead end.
     */
    fun makeCallWithResult(context: Context, phoneNumber: String): CallResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return CallResult.CALLED_DIRECTLY
            } catch (e: Exception) {
                Log.e(TAG, "Error making direct call, falling back to dialer: ${e.message}", e)
            }
        }

        return if (openDialer(context, phoneNumber)) CallResult.OPENED_DIALER_NO_PERMISSION
        else CallResult.FAILED
    }

    /**
     * Make a phone call to the given number
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error making call: ${e.message}", e)
            false
        }
    }
    
    /**
     * Open dialer with the phone number pre-filled
     */
    fun openDialer(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening dialer: ${e.message}", e)
            false
        }
    }
}
