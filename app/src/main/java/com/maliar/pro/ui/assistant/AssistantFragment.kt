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
import com.maliar.pro.viewmodels.AssistantViewModel
import com.maliar.pro.viewmodels.AssistantViewModelFactory
import androidx.fragment.app.viewModels

class AssistantFragment : Fragment() {
    private val viewModel: AssistantViewModel by viewModels { AssistantViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        // ... full chat UI with RecyclerView for messages, input, send button

        val sendBtn = MaterialButton(requireContext())
        sendBtn.setOnClickListener {
            // Call viewModel.sendMessage using GapGPT/Liara based on config
        }
        return view
    }
}