package com.maliar.pro.ui.debtors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.maliar.pro.adapters.DebtorPaymentAdapter
import com.maliar.pro.database.DebtorDirection
import com.maliar.pro.database.DebtorManager
import com.maliar.pro.databinding.FragmentDebtorDetailBinding
import com.maliar.pro.dialogs.AddDebtorPaymentDialog
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.viewmodels.DebtorDetailViewModel
import com.maliar.pro.viewmodels.DebtorDetailViewModelFactory
import kotlinx.coroutines.launch

class DebtorDetailFragment : Fragment() {

    private lateinit var binding: FragmentDebtorDetailBinding
    private lateinit var paymentAdapter: DebtorPaymentAdapter
    private val debtorManager by lazy { DebtorManager(requireContext()) }
    private val debtorId: Long by lazy { arguments?.getLong("debtorId") ?: -1L }
    private val viewModel: DebtorDetailViewModel by viewModels {
        DebtorDetailViewModelFactory(debtorManager, debtorId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDebtorDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        paymentAdapter = DebtorPaymentAdapter()
        binding.paymentsRecyclerView.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.paymentsRecyclerView.adapter = paymentAdapter

        binding.addPaymentFab.setOnClickListener {
            AddDebtorPaymentDialog(requireContext(), debtorManager, debtorId).show()
        }

        lifecycleScope.launch {
            viewModel.debtor.collect { debtor ->
                if (debtor == null) return@collect
                binding.nameText.text = debtor.name
                binding.directionText.text = if (debtor.direction == DebtorDirection.THEY_OWE_ME)
                    "🤝 بدهکار به شما" else "🧾 شما بدهکارید"
                binding.totalAmountText.text = String.format("%,.0f تومان", debtor.amount)
                binding.descriptionText.visibility =
                    if (debtor.description.isNotBlank()) View.VISIBLE else View.GONE
                binding.descriptionText.text = debtor.description

                if (debtor.dueDate != null) {
                    val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(debtor.dueDate)
                    binding.dueDateText.text = "سررسید: ${PersianCalendarHelper.formatJalali(y, m, d)}"
                    binding.dueDateText.visibility = View.VISIBLE
                } else {
                    binding.dueDateText.visibility = View.GONE
                }

                binding.addPaymentFab.visibility = if (debtor.isSettled) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            viewModel.totalPaid.collect { paid ->
                binding.paidAmountText.text = String.format("%,.0f تومان", paid)
                val debtor = viewModel.debtor.value
                val remaining = ((debtor?.amount ?: 0.0) - paid).coerceAtLeast(0.0)
                binding.remainingAmountText.text = String.format("%,.0f تومان", remaining)
            }
        }

        lifecycleScope.launch {
            viewModel.payments.collect { payments ->
                paymentAdapter.submitList(payments)
                binding.emptyPaymentsText.visibility = if (payments.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
