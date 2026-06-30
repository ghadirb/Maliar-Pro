package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.InstallmentAdapter
import com.maliar.pro.databinding.FragmentInstallmentListBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.dialogs.AddInstallmentDialog
import com.maliar.pro.dialogs.EditInstallmentDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch

class InstallmentListFragment : Fragment() {
    private lateinit var binding: FragmentInstallmentListBinding
    private lateinit var adapter: InstallmentAdapter
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentInstallmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = InstallmentAdapter(onItemClick = { showEditInstallmentDialog(it) }, onDeleteClick = { deleteInstallment(it) })
        binding.installmentRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.installmentRecyclerView.adapter = adapter
        binding.addInstallmentFab.setOnClickListener { AddInstallmentDialog(requireContext(), viewModel).show() }
        loadInstallments()
    }

    private fun showEditInstallmentDialog(installment: com.maliar.pro.database.Installment) {
        EditInstallmentDialog(requireContext(), viewModel, installment).show()
    }

    private fun loadInstallments() {
        lifecycleScope.launch {
            viewModel.installmentList.collect { adapter.submitList(it) }
        }
    }

    private fun deleteInstallment(installment: com.maliar.pro.database.Installment) {
        lifecycleScope.launch { viewModel.deleteInstallment(installment); loadInstallments() }
    }
}
