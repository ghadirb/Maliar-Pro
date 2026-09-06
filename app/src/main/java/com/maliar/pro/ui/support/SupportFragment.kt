package com.maliar.pro.ui.support

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.databinding.FragmentSupportBinding
import com.maliar.pro.support.FaqRepository
import com.maliar.pro.support.SupportChannel
import com.maliar.pro.support.SupportLauncher
import com.maliar.pro.support.SupportMessageBuilder

/**
 * "پشتیبانی مالیار پرو" (spec: سیستم پشتیبان مالیار پرو.txt). Everything here only uses
 * public messenger links via [SupportLauncher] - no bot, no messaging API (spec section
 * 7). If a real ticketing/bot backend is added later, it would live behind
 * [SupportLauncher]/[SupportMessageBuilder] as an alternative implementation; this
 * screen itself wouldn't need to change.
 */
class SupportFragment : Fragment() {

    private lateinit var binding: FragmentSupportBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.telegramSupportText.text = "${SupportChannel.TELEGRAM.emoji} پشتیبانی در ${SupportChannel.TELEGRAM.displayName}"
        binding.eitaaSupportText.text = "${SupportChannel.EITAA.emoji} پشتیبانی در ${SupportChannel.EITAA.displayName}"
        binding.rubikaSupportText.text = "${SupportChannel.RUBIKA.emoji} پشتیبانی در ${SupportChannel.RUBIKA.displayName}"

        binding.telegramSupportCard.setOnClickListener { SupportLauncher.open(requireContext(), SupportChannel.TELEGRAM) }
        binding.eitaaSupportCard.setOnClickListener { SupportLauncher.open(requireContext(), SupportChannel.EITAA) }
        binding.rubikaSupportCard.setOnClickListener { SupportLauncher.open(requireContext(), SupportChannel.RUBIKA) }

        binding.reportIssueCard.setOnClickListener { showComposeDialog(isIssueReport = true) }
        binding.suggestFeatureCard.setOnClickListener { showComposeDialog(isIssueReport = false) }

        renderFaq()
    }

    /**
     * Shared dialog for both "گزارش مشکل" and "پیشنهاد قابلیت" (spec sections 3-4):
     * the person types their text, picks a messenger, and the auto-built message is
     * copied to the clipboard right before that messenger opens - see
     * [SupportLauncher.copyToClipboard] for why a copy-then-paste flow is used instead of
     * trying to pre-fill a specific chat, which no public messenger link can reliably do
     * on its own.
     */
    private fun showComposeDialog(isIssueReport: Boolean) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val input = EditText(context).apply {
            hint = if (isIssueReport) "مشکل خود را شرح دهید" else "پیشنهاد خود را بنویسید"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            container.addView(this)
        }
        TextView(context).apply {
            text = "پس از تأیید، متن کامل (همراه با نسخهٔ برنامه و دستگاه) کپی می‌شود؛ سپس پیام‌رسان موردنظر را برای paste کردن انتخاب کنید."
            textSize = 12f
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            container.addView(this)
        }

        AlertDialog.Builder(context)
            .setTitle(if (isIssueReport) "گزارش مشکل" else "پیشنهاد قابلیت")
            .setView(container)
            .setPositiveButton("ادامه و انتخاب پیام‌رسان") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                val message = if (isIssueReport) {
                    SupportMessageBuilder.buildIssueReport(text)
                } else {
                    SupportMessageBuilder.buildFeatureSuggestion(text)
                }
                showChannelPicker(message)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showChannelPicker(message: String) {
        val context = requireContext()
        val channels = SupportChannel.entries.toTypedArray()
        val labels = channels.map { "${it.emoji} ${it.displayName}" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("ارسال با کدام پیام‌رسان؟")
            .setItems(labels) { _, which ->
                val channel = channels[which]
                SupportLauncher.copyToClipboard(context, "گزارش مالیار پرو", message)
                SupportLauncher.open(context, channel)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    /** Renders [FaqRepository]'s list as simple tap-to-expand rows - adding a new
     *  question to that repository is the only thing needed to show it here too. */
    private fun renderFaq() {
        val context = requireContext()
        binding.faqContainer.removeAllViews()
        FaqRepository.getAll().forEach { item ->
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
                radius = 12 * resources.displayMetrics.density
                cardElevation = 0f
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (14 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            val questionText = TextView(context).apply {
                text = "❓ ${item.question}"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val answerText = TextView(context).apply {
                text = item.answer
                textSize = 13f
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
                visibility = View.GONE
            }
            content.addView(questionText)
            content.addView(answerText)
            card.addView(content)
            card.setOnClickListener {
                answerText.visibility = if (answerText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            binding.faqContainer.addView(card)
        }
    }
}
