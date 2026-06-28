package com.maliar.pro.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.maliar.pro.databinding.FragmentApiKeysBinding
import com.maliar.pro.utils.PreferencesManager

class APIKeysFragment : Fragment() {

    private lateinit var binding: FragmentApiKeysBinding
    private val prefs by lazy { PreferencesManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentApiKeysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAPIKeys()
    }

    private fun setupAPIKeys() {
        val keys = prefs.getAPIKeys()
        
        if (keys.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.keysRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.keysRecyclerView.visibility = View.VISIBLE
            // Setup adapter for keys
        }
    }
}
