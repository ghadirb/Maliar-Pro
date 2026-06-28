package com.maliar.pro.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object VoiceCallHelper {
    
    private const val TAG = "VoiceCallHelper"
    
    /**
     * Make a phone call to the given number
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
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
