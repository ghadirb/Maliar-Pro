package com.maliar.pro.ui.accounting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maliar.pro.adapters.ExpenseAdapter
import com.maliar.pro.databinding.FragmentExpenseListBinding
import com.maliar.pro.database.AccountingManager
import com.maliar.pro.database.FinancialStatusManager
import com.maliar.pro.dialogs.AddExpenseDialog
import com.maliar.pro.dialogs.EditExpenseDialog
import com.maliar.pro.viewmodels.AccountingViewModel
import com.maliar.pro.viewmodels.AccountingViewModelFactory
import kotlinx.coroutines.launch
import com.maliar.pro.utils.PersianCalendarHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ExpenseListFragment : Fragment() {
    private lateinit var binding: FragmentExpenseListBinding
    private lateinit var adapter: ExpenseAdapter
    private var allExpenses: List<com.maliar.pro.database.Expense> = emptyList()
    private var selectedMonth: Pair<Int, Int>? = null
    private val financialStatusManager by lazy { FinancialStatusManager(requireContext()) }
    private val viewModel: AccountingViewModel by viewModels {
        AccountingViewModelFactory(AccountingManager(requireContext()))
    }
    private val receiptPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val image = runCatching { InputImage.fromFilePath(requireContext(), uri) }.getOrNull()
        if (image == null) {
            Toast.makeText(requireContext(), "خواندن تصویر رسید ممکن نیست.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { result ->
                val extracted = result.text.trim()
                if (extracted.isBlank()) {
                    Toast.makeText(requireContext(), "متنی از رسید تشخیص داده نشد.", Toast.LENGTH_LONG).show()
                } else {
                    showReceiptReview(extracted)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "اسکن رسید ناموفق بود.", Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExpenseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ExpenseAdapter(onItemClick = { showEditExpenseDialog(it) }, onDeleteClick = { deleteExpense(it) })
        binding.expenseRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.expenseRecyclerView.adapter = adapter
        binding.addExpenseFab.setOnClickListener { AddExpenseDialog(requireContext(), viewModel).show() }
        binding.expenseMonthFilterButton.setOnClickListener { showMonthPicker() }
        binding.scanReceiptButton.setOnClickListener { receiptPicker.launch("image/*") }
        loadExpenses()
    }

    private fun showReceiptReview(text: String) {
        val draft = extractReceiptDraft(text)
        lifecycleScope.launch {
            val accounts = financialStatusManager.getAllAssetsList()
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (20 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, 0)
            }
            fun input(hint: String, value: String = "", numeric: Boolean = false) =
                EditText(requireContext()).apply {
                    this.hint = hint
                    setText(value)
                    if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    container.addView(this)
            }
            container.addView(TextView(requireContext()).apply {
                this.text = "اطلاعات زیر فقط از روی متنِ رسید پیشنهاد شده است؛ پیش از ثبت، همه موارد را بررسی و اصلاح کنید."
            })
            val amountInput = input("مبلغ (تومان)", draft.amount?.toLong()?.toString().orEmpty(), true)
            val titleInput = input("عنوان یا فروشگاه", draft.title)
            val categoryInput = input("دسته‌بندی", draft.category)
            val (year, month, day) = PersianCalendarHelper.getCurrentJalaliDate()
            val dateInput = input("تاریخ شمسی (مثال ۱۴۰۵/۰۶/۱۳)", "$year/$month/$day")
            val accountLabels = listOf("حساب پیش‌فرض") + accounts.map { it.title }
            val accountSpinner = Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, accountLabels)
                container.addView(this)
            }
            val rawInput = input("متن استخراج‌شده (برای توضیحات)", text.take(500))

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("اطلاعات استخراج‌شده از رسید")
                .setView(container)
                .setPositiveButton("تأیید و ثبت هزینه") { _, _ ->
                    val amount = normalizeDigits(amountInput.text.toString())
                        .replace(",", "").replace("٬", "").toDoubleOrNull()
                    val date = parseJalaliDate(dateInput.text.toString())
                    val category = categoryInput.text.toString().trim()
                    if (amount == null || amount <= 0.0 || category.isBlank() || date == null) {
                        Toast.makeText(requireContext(), "مبلغ، دسته‌بندی و تاریخ را صحیح وارد کنید.", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    val selectedAccount = accountSpinner.selectedItemPosition
                        .takeIf { it > 0 }
                        ?.let { accounts[it - 1].id }
                    viewModel.addExpense(
                        com.maliar.pro.database.Expense(
                            amount = amount,
                            category = category,
                            description = titleInput.text.toString().trim().ifBlank { rawInput.text.toString().trim() },
                            date = date,
                            accountId = selectedAccount
                        )
                    )
                    Toast.makeText(requireContext(), "هزینه پس از تأیید شما ثبت شد.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("لغو", null)
                .show()
        }
    }

    private data class ReceiptDraft(val amount: Double?, val title: String, val category: String)

    /** ML Kit runs on device; the chosen image is never copied or persisted by this flow. */
    private fun extractReceiptDraft(text: String): ReceiptDraft {
        val normalized = normalizeDigits(text)
        val amounts = Regex("(?<!\\d)([0-9][0-9,٬]*)(?!\\d)")
            .findAll(normalized)
            .mapNotNull { it.groupValues[1].replace(",", "").replace("٬", "").toDoubleOrNull() }
            .filter { it >= 1_000.0 }
            .toList()
        val lines = text.lineSequence().map { it.trim() }.filter { it.length >= 3 }.toList()
        val title = lines.firstOrNull { line ->
            !line.any { it.isDigit() } && line.length <= 80
        }.orEmpty()
        val category = when {
            listOf("بنزین", "پمپ", "تاکسی", "اسنپ", "ماشین").any { normalized.contains(it) } -> "خودرو"
            listOf("سوپر", "فروشگاه", "میوه", "نان", "خوراک").any { normalized.contains(it) } -> "خوراک"
            listOf("دارو", "درمان", "پزشک", "بیمارستان").any { normalized.contains(it) } -> "درمان"
            else -> "عمومی"
        }
        return ReceiptDraft(amounts.maxOrNull(), title, category)
    }

    private fun normalizeDigits(text: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        var result = text
        persian.forEachIndexed { index, char -> result = result.replace(char, ('0' + index)) }
        arabic.forEachIndexed { index, char -> result = result.replace(char, ('0' + index)) }
        return result
    }

    private fun parseJalaliDate(value: String): Long? {
        val parts = normalizeDigits(value).trim().split(Regex("[/\\-]"))
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 1300..1600 || month !in 1..12 || day !in 1..PersianCalendarHelper.daysInJalaliMonth(year, month)) return null
        return PersianCalendarHelper.jalaliToGregorianMillis(year, month, day)
    }

    private fun showEditExpenseDialog(expense: com.maliar.pro.database.Expense) {
        EditExpenseDialog(requireContext(), viewModel, expense).show()
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            viewModel.expenseList.collect {
                allExpenses = it
                adapter.submitList(selectedMonth?.let { (y, m) -> it.filter { item -> PersianCalendarHelper.gregorianMillisToJalali(item.date).let { d -> d.first == y && d.second == m } } } ?: it)
            }
        }
    }

    private fun showMonthPicker() {
        val year = android.widget.EditText(requireContext()).apply { hint = "سال شمسی"; inputType = 2 }
        val month = android.widget.EditText(requireContext()).apply { hint = "ماه ۱ تا ۱۲"; inputType = 2 }
        val box = android.widget.LinearLayout(requireContext()).apply { orientation = 1; addView(year); addView(month) }
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("انتخاب ماه و سال")
            .setView(box).setNegativeButton("پاک کردن فیلتر") { _, _ -> selectedMonth = null; binding.expenseFilterLabel.text = "همه هزینه‌ها"; adapter.submitList(allExpenses) }
            .setPositiveButton("نمایش") { _, _ ->
                val y = year.text.toString().toIntOrNull(); val m = month.text.toString().toIntOrNull()
                if (y == null || m == null || m !in 1..12) return@setPositiveButton
                selectedMonth = y to m; binding.expenseFilterLabel.text = "هزینه‌های $m/$y"
                adapter.submitList(allExpenses.filter { PersianCalendarHelper.gregorianMillisToJalali(it.date).let { d -> d.first == y && d.second == m } })
            }.show()
    }

    private fun deleteExpense(expense: com.maliar.pro.database.Expense) {
        lifecycleScope.launch { viewModel.deleteExpense(expense); loadExpenses() }
    }
}
