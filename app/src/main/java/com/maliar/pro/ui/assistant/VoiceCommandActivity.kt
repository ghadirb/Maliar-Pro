package com.maliar.pro.ui.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maliar.pro.R
import com.maliar.pro.database.ContactManager
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * Real "run a command from a notification" flow, launched from the background
 * notification's "🎙️ دستور صوتی" action: record a short voice command, transcribe it,
 * and if it's a call request ("به علی زنگ بزن" / "با سارا تماس بگیر"), match it against
 * the app's own Contacts, ask for a plain confirmation, then place the real call - all
 * without needing to open the full assistant chat screen first.
 *
 * Scope is intentionally narrow (call requests only) rather than a general command
 * executor, since a phone call is the one action serious enough that it should always be
 * confirmed before it happens.
 */
class VoiceCommandActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording() else {
            setStatus("برای دستور صوتی، دسترسی میکروفون لازم است.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_command)

        val micButton = findViewById<FloatingActionButton>(R.id.micButton)
        setStatus("برای شروع، دکمه میکروفون را بزنید و بگویید مثلاً «به علی زنگ بزن»")

        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<android.view.View>(R.id.closeButton).setOnClickListener { finish() }
    }

    private fun setStatus(text: String) {
        findViewById<TextView>(R.id.voiceStatusText).text = text
    }

    private fun toggleRecording() {
        if (isRecording) stopRecordingAndTranscribe() else startRecording()
    }

    private fun startRecording() {
        try {
            outputFile = File(cacheDir, "voice_command_${System.currentTimeMillis()}.m4a")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            setStatus("🎙️ در حال ضبط… دوباره دکمه را بزنید تا تمام شود")
            findViewById<FloatingActionButton>(R.id.micButton)
                .setImageResource(R.drawable.ic_check)
        } catch (e: Exception) {
            setStatus("❌ خطا در شروع ضبط صدا: ${e.message}")
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        findViewById<FloatingActionButton>(R.id.micButton).setImageResource(R.drawable.ic_notification)
        try {
            recorder?.apply { stop(); release() }
        } catch (e: Exception) {
            // Recording may have been too short to produce a valid file; handled below.
        }
        recorder = null

        val file = outputFile ?: return
        setStatus("⏳ در حال تبدیل صدا به متن…")

        lifecycleScope.launch {
            val transcript = AIHelper.transcribeAudio(this@VoiceCommandActivity, file)
            file.delete()

            if (transcript.isNullOrBlank()) {
                setStatus("❌ متوجه نشدم چی گفتید. دوباره امتحان کنید یا از تب دستیار استفاده کنید.")
                return@launch
            }
            setStatus("📝 متوجه شدم: «$transcript»")
            handleTranscript(transcript)
        }
    }

    /** Very small, purpose-built parser: only looks for a call intent + a contact name. */
    private suspend fun handleTranscript(transcript: String) {
        val nameGuess = Regex("(?:به|با)\\s+([\\p{L}\\s]{2,20}?)\\s*(?:زنگ بزن|تماس بگیر|تماس)")
            .find(transcript)?.groupValues?.get(1)?.trim()

        if (nameGuess.isNullOrBlank()) {
            setStatus("📝 «$transcript»\nاین یک دستور تماس تشخیص داده نشد. برای دستورات دیگر از تب دستیار استفاده کنید.")
            return
        }

        val contacts = try {
            ContactManager(this).getAllContactsList()
        } catch (e: Exception) {
            emptyList()
        }
        val matched = contacts.firstOrNull {
            it.name.contains(nameGuess, ignoreCase = true) || nameGuess.contains(it.name, ignoreCase = true)
        }

        if (matched == null) {
            setStatus("⚠️ مخاطبی به نام «$nameGuess» در برنامه پیدا نشد.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("تایید تماس")
            .setMessage("آیا می‌خواهید با ${matched.name} (${matched.phoneNumber}) تماس بگیرید؟")
            .setPositiveButton("بله، تماس بگیر") { _, _ ->
                val result = VoiceCallHelper.makeCallWithResult(this, matched.phoneNumber)
                setStatus(
                    when (result) {
                        VoiceCallHelper.CallResult.CALLED_DIRECTLY -> "📞 در حال تماس با ${matched.name}…"
                        VoiceCallHelper.CallResult.OPENED_DIALER_NO_PERMISSION -> "📲 شماره‌گیر با شماره ${matched.name} باز شد."
                        VoiceCallHelper.CallResult.FAILED -> "❌ خطا در برقراری تماس"
                    }
                )
                findViewById<android.view.View>(R.id.micButton).postDelayed({ finish() }, 1500)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isRecording) { recorder?.stop(); recorder?.release() }
        } catch (e: Exception) { /* best effort */ }
        outputFile?.delete()
    }
}
