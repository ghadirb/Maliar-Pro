package com.maliar.pro.ui.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maliar.pro.R
import com.maliar.pro.database.Contact
import com.maliar.pro.database.ContactManager
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.VoiceCallHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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

    // Confirmation is spoken and listened-to, not just tapped - see confirmAndCall().
    private var confirmTts: TextToSpeech? = null
    private var confirmRecognizer: SpeechRecognizer? = null
    private var activeDialog: AlertDialog? = null

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

    /**
     * Parses the transcript for a call intent + target contact.
     *
     * Order-of-words used to matter a lot here ("تماس با X" was never recognized, only
     * "به X تماس بگیر") and matching was a plain substring check, so a name spoken with a
     * relation word attached ("مریم همسر") or slightly different from how it's saved never
     * matched. Both problems are handled the same way now: the online AI model (when a key
     * is active) is given the transcript *and* the real contact list together, so it can
     * pick the closest matching saved contact regardless of sentence order or small wording
     * differences. A simple regex + substring match is kept only as a fallback for when no
     * AI key is configured or the request fails.
     */
    private suspend fun handleTranscript(transcript: String) {
        val contacts = try {
            ContactManager(this).getAllContactsList()
        } catch (e: Exception) {
            emptyList()
        }

        if (contacts.isEmpty()) {
            setStatus("📝 «$transcript»\nهنوز مخاطبی در بخش مخاطبین برنامه ثبت نشده تا بشود با آن تماس گرفت.")
            return
        }

        var matched = try {
            aiDetectCallAndContact(transcript, contacts)
        } catch (e: Exception) {
            null
        }

        if (matched == null) {
            val contactQuery = regexGuessContactQuery(transcript)
            if (contactQuery.isNullOrBlank()) {
                setStatus("📝 «$transcript»\nاین یک دستور تماس تشخیص داده نشد. برای دستورات دیگر از تب دستیار استفاده کنید.")
                return
            }
            matched = fuzzyLocalMatch(contactQuery, contacts)
            if (matched == null) {
                setStatus("⚠️ مخاطبی شبیه «$contactQuery» در برنامه پیدا نشد.")
                return
            }
        }

        confirmAndCall(matched)
    }

    /** Asks the active AI model to pick the matching contact directly from the real list. */
    private suspend fun aiDetectCallAndContact(transcript: String, contacts: List<Contact>): Contact? {
        val namesList = contacts.joinToString("\n") { "- ${it.name}" }
        val raw = AIHelper.generateText(
            this,
            systemPrompt = """
                این یک دستور صوتی فارسی برای اجرای تماس تلفنی در یک اپ است. اگر این متن درخواست
                تماس با یکی از مخاطبین زیر است، فقط نام دقیق همان مخاطب را از لیست برگردان -
                حتی اگر پسوند نسبت خانوادگی اضافه داشت (مثل «مریم همسر») یا تلفظ کمی فرق داشت،
                نزدیک‌ترین مورد را از لیست انتخاب کن. اگر این متن اصلاً درخواست تماس نیست یا هیچ
                مخاطبی از لیست منطبق نبود، فقط بنویس NONE. فقط یکی از نام‌های دقیق لیست یا کلمه
                NONE را بنویس، بدون هیچ توضیح اضافه.

                لیست مخاطبین:
                $namesList
            """.trimIndent(),
            userPrompt = transcript
        )?.trim()

        if (raw.isNullOrBlank() || raw.equals("NONE", ignoreCase = true)) return null
        return contacts.firstOrNull { it.name == raw }
            ?: contacts.firstOrNull { it.name.contains(raw, ignoreCase = true) || raw.contains(it.name, ignoreCase = true) }
    }

    /** Fallback when no AI key is active: recognizes the request in either word order. */
    private fun regexGuessContactQuery(transcript: String): String? {
        val patterns = listOf(
            Regex("(?:زنگ بزن|تماس بگیر)\\s*(?:به|با)\\s+([\\p{L}\\s]{2,25})"),
            Regex("(?:تماس|زنگ)\\s+(?:به|با)\\s+([\\p{L}\\s]{2,25})"),
            Regex("(?:به|با)\\s+([\\p{L}\\s]{2,25}?)\\s*(?:زنگ بزن|تماس بگیر|تماس)")
        )
        for (pattern in patterns) {
            pattern.find(transcript)?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) return it }
        }
        return null
    }

    private fun fuzzyLocalMatch(query: String, contacts: List<Contact>): Contact? {
        val cleaned = query.trim()
        return contacts.firstOrNull { it.name.contains(cleaned, ignoreCase = true) || cleaned.contains(it.name, ignoreCase = true) }
            ?: contacts.firstOrNull { contact ->
                val queryWords = cleaned.split(" ").filter { it.length > 1 }
                queryWords.any { qw -> contact.name.contains(qw, ignoreCase = true) }
            }
    }

    /**
     * Confirms out loud instead of requiring a tap: speaks the question, then listens for a
     * plain "بله"/"نه" answer and places the call automatically on a yes - this is the "record
     * once, confirm by voice" flow that was requested instead of pressing buttons repeatedly.
     * The dialog with buttons still shows at the same time purely as a fallback for noisy
     * environments or devices without a working recognizer - whichever path resolves first wins.
     */
    private fun confirmAndCall(contact: Contact) {
        setStatus("🔊 با ${contact.name} تماس بگیرم؟ («بله» یا «نه» بگویید)")
        activeDialog = AlertDialog.Builder(this)
            .setTitle("تایید تماس")
            .setMessage("با ${contact.name} (${contact.phoneNumber}) تماس بگیرم؟\nمی‌توانید با گفتن «بله» یا «نه» هم پاسخ دهید.")
            .setPositiveButton("بله، تماس بگیر") { _, _ -> placeCall(contact) }
            .setNegativeButton("لغو") { _, _ -> stopConfirmVoiceFlow(); setStatus("لغو شد.") }
            .setCancelable(false)
            .show()
        speakAndListenForConfirmation(contact)
    }

    private fun speakAndListenForConfirmation(contact: Contact) {
        confirmTts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val result = confirmTts?.setLanguage(Locale("fa", "IR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                confirmTts?.setLanguage(Locale.US)
            }
            confirmTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { listenForYesNo(contact) }
                }
            })
            val params = Bundle()
            confirmTts?.speak(
                "با ${contact.name} تماس بگیرم؟ بگویید بله یا نه",
                TextToSpeech.QUEUE_FLUSH, params, "confirm_call_${contact.id}"
            )
        }
    }

    private fun listenForYesNo(contact: Contact) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            confirmRecognizer?.destroy()
            confirmRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { /* dialog buttons remain as fallback */ }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        when {
                            matches.any { isAffirmative(it) } -> runOnUiThread { placeCall(contact) }
                            matches.any { isNegative(it) } -> runOnUiThread {
                                stopConfirmVoiceFlow(); activeDialog?.dismiss(); setStatus("لغو شد.")
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            }
            confirmRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            // Dialog buttons still work even if voice listening couldn't start.
        }
    }

    private fun isAffirmative(text: String) = AFFIRMATIVE_PHRASES.any { text.contains(it) }
    private fun isNegative(text: String) = NEGATIVE_PHRASES.any { text.contains(it) }

    private fun placeCall(contact: Contact) {
        stopConfirmVoiceFlow()
        activeDialog?.dismiss()
        val result = VoiceCallHelper.makeCallWithResult(this, contact.phoneNumber)
        setStatus(
            when (result) {
                VoiceCallHelper.CallResult.CALLED_DIRECTLY -> "📞 در حال تماس با ${contact.name}…"
                VoiceCallHelper.CallResult.OPENED_DIALER_NO_PERMISSION -> "📲 شماره‌گیر با شماره ${contact.name} باز شد."
                VoiceCallHelper.CallResult.FAILED -> "❌ خطا در برقراری تماس"
            }
        )
        findViewById<android.view.View>(R.id.micButton).postDelayed({ finish() }, 1500)
    }

    private fun stopConfirmVoiceFlow() {
        try { confirmRecognizer?.stopListening(); confirmRecognizer?.destroy() } catch (e: Exception) { /* best effort */ }
        confirmRecognizer = null
        try { confirmTts?.stop(); confirmTts?.shutdown() } catch (e: Exception) { /* best effort */ }
        confirmTts = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isRecording) { recorder?.stop(); recorder?.release() }
        } catch (e: Exception) { /* best effort */ }
        outputFile?.delete()
        stopConfirmVoiceFlow()
    }

    companion object {
        private val AFFIRMATIVE_PHRASES = listOf("بله", "بلی", "آره", "اره", "باشه", "تایید", "انجام بده", "تماس بگیر")
        private val NEGATIVE_PHRASES = listOf("نه ", "نه", "خیر", "نکن", "لغو", "نمی‌خوام", "نمیخوام")
    }
}
