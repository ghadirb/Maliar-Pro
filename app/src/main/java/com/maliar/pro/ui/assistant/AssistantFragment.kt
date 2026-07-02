package com.maliar.pro.ui.assistant
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.maliar.pro.viewmodels.AssistantViewModel
import com.maliar.pro.viewmodels.AssistantViewModelFactory
import androidx.fragment.app.viewModels

class AssistantFragment : Fragment() {
    private val viewModel: AssistantViewModel by viewModels { AssistantViewModelFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Placeholder UI - expand as needed
        return android.widget.LinearLayout(requireContext())
    }
}