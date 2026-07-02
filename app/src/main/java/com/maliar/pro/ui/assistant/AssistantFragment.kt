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
    ): View? {
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        val title = TextView(requireContext()).apply {
            text = "🤖 دستیار هوشمند Maliar"
            textSize = 24f
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Large)
        }
        view.addView(title)

        val status = TextView(requireContext()).apply {
            text = "در حال اتصال به GAPGPT..."
            textSize = 16f
        }
        view.addView(status)

        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val input = EditText(requireContext()).apply {
            hint = "سوال یا دستور خود را بنویسید..."
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputLayout.addView(input)

        val sendBtn = MaterialButton(requireContext()).apply {
            text = "ارسال"
            setOnClickListener {
                // GAPGPT integration placeholder
                status.text = "در حال پردازش..."
            }
        }
        inputLayout.addView(sendBtn)
        view.addView(inputLayout)

        return view
    }
}