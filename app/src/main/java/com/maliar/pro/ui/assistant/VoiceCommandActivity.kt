package com.maliar.pro.ui.assistant

import android.app.Activity
import android.os.Bundle

/**
 * Compatibility placeholder for the removed voice-call notification action.
 * The activity is not registered in AndroidManifest in the Play-safe test build.
 */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
