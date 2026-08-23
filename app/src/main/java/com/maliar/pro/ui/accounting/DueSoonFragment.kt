package com.maliar.pro.ui.accounting

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
import com.maliar.pro.adapters.DueSoonAdapter
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.databinding.FragmentDueSoonBinding
import com.maliar.pro.models.DueItemType
import com.maliar.pro.viewmodels.DueSoonViewModel
import com.maliar.pro.viewmodels.DueSoonViewModelFactory
import kotlinx.coroutines.launch

/** The full "همه چک‌ها و اقساط و بدهی‌های نزدیک" list, reached from the summary card at the
 *  top of the accounting tab. Tapping a check/installment jumps to its list screen;
 *  tapping a debtor jumps to the debtor management list. */
class DueSoonFragment : Fragment() {

    private lateinit var binding: FragmentDueSoonBinding
    private lateinit var adapter: DueSoonAdapter
    private val viewModel: DueSoonViewModel by viewModels {
        DueSoonViewModelFactory(
            AccountingManager(requireContext()),
            FinancialStatusManager(requireContext()),
            DebtorManager(requireContext())
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDueSoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DueSoonAdapter(onItemClick = { item ->
            when (item.type) {
                DueItemType.CHECK -> findNavController().navigate(R.id.checkListFragment)
                DueItemType.INSTALLMENT -> findNavController().navigate(R.id.installmentListFragment)
                DueItemType.DEBTOR_THEY_OWE_ME, DueItemType.DEBTOR_I_OWE_THEM ->
                    findNavController().navigate(R.id.debtorListFragment)
            }
        })
        binding.dueSoonRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.dueSoonRecyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.dueItems.collect { items ->
                adapter.submitList(items)
                binding.emptyStateText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
