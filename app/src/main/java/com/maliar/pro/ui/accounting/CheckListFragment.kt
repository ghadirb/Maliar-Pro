package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.CheckAdapter
import com.maliar.pro.databinding.FragmentCheckListBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.dialogs.AddCheckDialog
import com.maliar.pro.dialogs.EditCheckDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

class CheckListFragment : Fragment() {
    private lateinit var binding: FragmentCheckListBinding
    private lateinit var adapter: CheckAdapter
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCheckListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CheckAdapter(onItemClick = { showEditCheckDialog(it) }, onDeleteClick = { deleteCheck(it) })
        binding.checkRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.checkRecyclerView.adapter = adapter
        binding.addCheckFab.setOnClickListener { AddCheckDialog(requireContext(), viewModel).show() }
        loadChecks()
    }

    private fun showEditCheckDialog(check: com.maliar.pro.database.Check) {
        EditCheckDialog(requireContext(), viewModel, check).show()
    }

    private fun loadChecks() {
        lifecycleScope.launch {
            viewModel.checkList.collect { adapter.submitList(it) }
        }
    }

    private fun deleteCheck(check: com.maliar.pro.database.Check) {
        lifecycleScope.launch { viewModel.deleteCheck(check); loadChecks() }
    }
}
