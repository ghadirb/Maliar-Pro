package com.maliar.pro.ui.debtors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.R
import com.maliar.pro.adapters.DebtorAdapter
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.databinding.FragmentDebtorListBinding
import com.maliar.pro.dialogs.AddDebtorDialog
import com.maliar.pro.viewmodels.DebtorListViewModel
import com.maliar.pro.viewmodels.DebtorListViewModelFactory
import kotlinx.coroutines.launch

class DebtorListFragment : Fragment() {

    private lateinit var binding: FragmentDebtorListBinding
    private lateinit var adapter: DebtorAdapter
    private val debtorManager by lazy { DebtorManager(requireContext()) }
    private val viewModel: DebtorListViewModel by viewModels {
        DebtorListViewModelFactory(debtorManager)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDebtorListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DebtorAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putLong("debtorId", item.debtor.id) }
                findNavController().navigate(R.id.action_debtorListFragment_to_debtorDetailFragment, bundle)
            },
            onDeleteClick = { item ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("حذف")
                    .setMessage("آیا از حذف \"${item.debtor.name}\" مطمئن هستید؟")
                    .setPositiveButton("حذف") { _, _ -> viewModel.deleteDebtor(item.debtor) }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        )
        binding.debtorRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.debtorRecyclerView.adapter = adapter

        binding.addDebtorFab.setOnClickListener {
            AddDebtorDialog(requireContext(), debtorManager).show()
        }

        lifecycleScope.launch {
            viewModel.debtorsWithBalance.collect { list ->
                adapter.submitList(list)
                binding.emptyStateText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.totalTheyOweMe.collect {
                binding.totalTheyOweMeText.text = String.format("%,.0f تومان", it)
            }
        }
        lifecycleScope.launch {
            viewModel.totalIOweThem.collect {
                binding.totalIOweThemText.text = String.format("%,.0f تومان", it)
            }
        }
    }
}
