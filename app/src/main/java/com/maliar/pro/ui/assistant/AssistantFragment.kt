package com.maliar.pro.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.maliar.pro.R

class AssistantFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val title = TextView(requireContext()).apply {
            text = "دستیار هوشمند Maliar"
            textSize = 20f
            setTextAppearance(R.style.TextAppearance_Material3_HeadlineMedium)
        }
        view.addView(title)

        // Chat area (placeholder)
        val chatArea = TextView(requireContext()).apply {
            text = "چت با دستیار...
(در حال توسعه کامل با GAPGPT)"
            textSize = 16f
        }
        view.addView(chatArea)

        // Input
        val input = EditText(requireContext()).apply {
            hint = "پیام خود را بنویسید..."
        }
        view.addView(input)

        // Send button
        val sendButton = MaterialButton(requireContext()).apply {
            text = "ارسال"
            setOnClickListener {
                // TODO: Integrate GAPGPT / voice
            }
        }
        view.addView(sendButton)

        return view
    }
}