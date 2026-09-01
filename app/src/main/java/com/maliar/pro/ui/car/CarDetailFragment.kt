package com.maliar.pro.ui.car

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.maliar.pro.R
import com.maliar.pro.database.CarManager
import com.maliar.pro.database.CarServiceLog
import com.maliar.pro.databinding.FragmentCarDetailBinding
import com.maliar.pro.dialogs.AddCarDialog
import com.maliar.pro.dialogs.AddCarExpenseDialog
import com.maliar.pro.dialogs.AddCarServiceItemDialog
import com.maliar.pro.dialogs.AddOdometerDialog
import com.maliar.pro.dialogs.MarkServiceDoneDialog
import com.maliar.pro.utils.CarServiceStatus
import com.maliar.pro.utils.CarServiceUrgency
import com.maliar.pro.utils.AIHelper
import com.maliar.pro.utils.PersianCalendarHelper
import com.maliar.pro.utils.PreferencesManager
import com.maliar.pro.viewmodels.CarDetailViewModel
import com.maliar.pro.viewmodels.CarDetailViewModelFactory
import kotlinx.coroutines.launch

class CarDetailFragment : Fragment() {

    private lateinit var binding: FragmentCarDetailBinding
    private var carId: Long = -1L

    private val viewModel: CarDetailViewModel by viewModels {
        CarDetailViewModelFactory(CarManager(requireContext()), carId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        carId = arguments?.getLong("carId") ?: -1L
        binding = FragmentCarDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.carNameText.setOnClickListener {
            val car = viewModel.car.value ?: return@setOnClickListener
            AddCarDialog(requireContext(), car) { updated -> viewModel.updateCar { updated } }.show()
        }

        binding.addOdometerButton.setOnClickListener {
            val km = viewModel.car.value?.currentOdometerKm ?: 0
            AddOdometerDialog(requireContext(), km) { newKm -> viewModel.addOdometerReading(newKm) }.show()
        }

        binding.addServiceItemButton.setOnClickListener {
            val km = viewModel.car.value?.currentOdometerKm ?: 0
            AddCarServiceItemDialog(requireContext(), carId, km) { item -> viewModel.addServiceItem(item) }.show()
        }

        binding.addExpenseButton.setOnClickListener {
            val km = viewModel.car.value?.currentOdometerKm ?: 0
            AddCarExpenseDialog(requireContext(), km) { title, category, odometerKm, cost, notes, linkToFinance ->
                viewModel.addManualCost(title, category, odometerKm, cost, notes, linkToFinance)
            }.show()
        }

        binding.aiTipsButton.setOnClickListener { requestAiTips() }

        lifecycleScope.launch {
            viewModel.car.collect { car ->
                if (car != null) {
                    binding.carNameText.text = car.name
                    binding.carOdometerText.text = String.format("%,d km", car.currentOdometerKm)
                    requireActivity().title = car.name
                }
            }
        }

        lifecycleScope.launch {
            viewModel.serviceStatuses.collect { statuses ->
                renderServiceStatuses(statuses)
                val hasActiveAiKey = PreferencesManager(requireContext()).getAPIKeys().any { it.isActive }
                binding.aiTipsButton.visibility = if (statuses.isNotEmpty() && hasActiveAiKey) View.VISIBLE else View.GONE
                binding.aiTipsText.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.costSummary.collect { summary ->
                binding.costThisMonthText.text = formatCurrency(summary.thisMonth)
                binding.costThisQuarterText.text = formatCurrency(summary.thisQuarter)
                binding.costThisYearText.text = formatCurrency(summary.thisYear)
                binding.costTotalText.text = formatCurrency(summary.total)
                binding.costAverageMonthlyText.text = formatCurrency(summary.averageMonthly)
                binding.costBreakdownText.text =
                    "سرویس‌ها: ${formatCurrency(summary.serviceCost)} · تعمیرات: ${formatCurrency(summary.repairCost)} · " +
                        "قطعات: ${formatCurrency(summary.partCost)} · سایر: ${formatCurrency(summary.otherCost)}"
            }
        }

        lifecycleScope.launch {
            viewModel.serviceLogs.collect { logs -> renderHistory(logs) }
        }
    }

    private fun renderServiceStatuses(statuses: List<CarServiceStatus>) {
        binding.serviceStatusContainer.removeAllViews()
        binding.serviceEmptyText.visibility = if (statuses.isEmpty()) View.VISIBLE else View.GONE
        val currentKm = viewModel.car.value?.currentOdometerKm ?: 0

        statuses.forEach { status ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_car_service_status, binding.serviceStatusContainer, false) as MaterialCardView

            val (icon, color, detail) = when (status.urgency) {
                CarServiceUrgency.OVERDUE -> Triple("🔴", R.color.error, overdueDetail(status))
                CarServiceUrgency.SOON -> Triple("🟠", R.color.warning, soonDetail(status))
                CarServiceUrgency.OK -> Triple("🟢", R.color.success, okDetail(status))
                CarServiceUrgency.UNSCHEDULED -> Triple("⚪", R.color.text_secondary, "بدون برنامهٔ زمان‌بندی‌شده")
            }

            row.findViewById<TextView>(R.id.statusIconText).text = icon
            row.findViewById<TextView>(R.id.statusNameText).text = status.item.name
            row.findViewById<TextView>(R.id.statusDetailText).apply {
                text = detail
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), color))
            }

            row.findViewById<MaterialButton>(R.id.markDoneButton).setOnClickListener {
                MarkServiceDoneDialog(requireContext(), status.item, currentKm) { km, cost, notes, linkToFinance ->
                    viewModel.markServiceDone(status.item, km, cost, notes, linkToFinance)
                }.show()
            }

            row.setOnClickListener {
                AddCarServiceItemDialog(requireContext(), carId, currentKm, status.item) { updated ->
                    viewModel.updateServiceItem(updated)
                }.show()
            }
            row.setOnLongClickListener {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("حذف مورد سرویس")
                    .setMessage("«${status.item.name}» حذف شود؟ سوابق ثبت‌شدهٔ آن حذف نمی‌شود.")
                    .setPositiveButton("حذف") { _, _ -> viewModel.deleteServiceItem(status.item) }
                    .setNegativeButton("لغو", null)
                    .show()
                true
            }

            binding.serviceStatusContainer.addView(row)
        }
    }

    private fun overdueDetail(status: CarServiceStatus): String {
        val parts = mutableListOf<String>()
        status.remainingKm?.let { if (it <= 0) parts += "${String.format("%,d", -it)} کیلومتر از موعد گذشته" }
        status.remainingDays?.let { if (it <= 0) parts += "${-it} روز از موعد گذشته" }
        return parts.joinToString(" / ").ifBlank { "از موعد گذشته" }
    }

    private fun soonDetail(status: CarServiceStatus): String {
        val parts = mutableListOf<String>()
        status.remainingKm?.let { parts += "${String.format("%,d", it)} کیلومتر مانده" }
        status.remainingDays?.let { parts += "$it روز مانده" }
        return parts.joinToString(" / ")
    }

    private fun okDetail(status: CarServiceStatus): String {
        val parts = mutableListOf<String>()
        status.remainingKm?.let { parts += "${String.format("%,d", it)} کیلومتر تا سرویس بعدی" }
        status.remainingDays?.let { parts += "$it روز تا سرویس بعدی" }
        return parts.joinToString(" / ")
    }

    private fun renderHistory(logs: List<CarServiceLog>) {
        binding.historyContainer.removeAllViews()
        binding.historyEmptyText.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE

        logs.take(30).forEach { log ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_car_service_log, binding.historyContainer, false)

            row.findViewById<TextView>(R.id.logTitleText).text = log.title
            val (y, m, d) = PersianCalendarHelper.gregorianMillisToJalali(log.date)
            val kmPart = log.odometerKm?.let { " · ${String.format("%,d", it)} km" }.orEmpty()
            row.findViewById<TextView>(R.id.logSubtitleText).text =
                "${PersianCalendarHelper.formatJalali(y, m, d)}$kmPart"
            row.findViewById<TextView>(R.id.logCostText).text =
                if (log.cost > 0) formatCurrency(log.cost) else ""

            row.setOnLongClickListener {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("حذف سابقه")
                    .setMessage(
                        "«${log.title}» حذف شود؟" +
                            if (log.linkedExpenseId != null) "\nهزینهٔ متناظر آن در بخش مالی هم حذف خواهد شد." else ""
                    )
                    .setPositiveButton("حذف") { _, _ -> viewModel.deleteServiceLog(log) }
                    .setNegativeButton("لغو", null)
                    .show()
                true
            }

            binding.historyContainer.addView(row)
        }
    }

    /**
     * Optional, user-initiated only (never automatic) AI maintenance tips. Reuses
     * AIHelper.generateText - same reviewed code path as the in-app assistant - and sends
     * only the car's brand/model/year and its service items' names + urgency + remaining
     * km/days (all already visible on this screen). Deliberately excludes the plate number
     * and any cost/financial data - none of that is needed to give maintenance advice.
     */
    private fun requestAiTips() {
        val car = viewModel.car.value ?: return
        val statuses = viewModel.serviceStatuses.value
        if (statuses.isEmpty()) return

        binding.aiTipsButton.isEnabled = false
        binding.aiTipsButton.text = "در حال دریافت توصیه..."
        binding.aiTipsText.visibility = View.GONE

        lifecycleScope.launch {
            val carLine = listOfNotNull(car.brand.takeIf { it.isNotBlank() }, car.model.takeIf { it.isNotBlank() }, car.year?.toString())
                .joinToString(" ").ifBlank { "خودرو" }
            val itemsLine = statuses.joinToString("، ") { status ->
                val urgencyLabel = when (status.urgency) {
                    CarServiceUrgency.OVERDUE -> "از موعد گذشته"
                    CarServiceUrgency.SOON -> "نزدیک به موعد"
                    CarServiceUrgency.OK -> "در وضعیت خوب"
                    CarServiceUrgency.UNSCHEDULED -> "بدون برنامهٔ زمان‌بندی"
                }
                "${status.item.name} ($urgencyLabel)"
            }
            val userPrompt = "خودرو: $carLine. وضعیت موارد سرویس: $itemsLine."
            val systemPrompt = "شما یک دستیار نگهداری خودرو فارسی‌زبان هستید. بر اساس وضعیت سرویس‌های زیر، " +
                "حداکثر ۳ توصیهٔ خیلی کوتاه (هرکدام یک خط) دربارهٔ اولویت انجام کارها یا نکات نگهداری بده. " +
                "فقط فارسی، بدون مقدمه، و توصیهٔ فنی/ایمنی حساس نده."

            val result = AIHelper.generateText(requireContext(), systemPrompt, userPrompt)

            binding.aiTipsButton.isEnabled = true
            binding.aiTipsButton.text = "🧠 توصیهٔ هوشمند نگهداری (اختیاری)"
            binding.aiTipsText.visibility = View.VISIBLE
            binding.aiTipsText.text = result
                ?: "دریافت توصیهٔ هوشمند ممکن نشد. لطفاً یک کلید API فعال در پروفایل ← کلیدهای API بررسی کنید."
        }
    }

    private fun formatCurrency(amount: Double): String = String.format("%,.0f ت", amount)
}
