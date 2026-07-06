package com.maliar.pro.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maliar.pro.databinding.FragmentApiKeysBinding
import com.maliar.pro.models.AIProvider
import com.maliar.pro.models.APIKey
import com.maliar.pro.utils.AutoProvisioningManager
import com.maliar.pro.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * "تنظیمات هوش مصنوعی" - lists every stored AI key (the free/shared ones this app
 * auto-provisions, plus any personal key the person added) and lets them toggle which
 * ones are active or add/remove a personal key. A personal key here is also exactly what
 * SubscriptionManager checks for to grant unlimited AI usage (see APIKey.isAutoProvisioned).
 */
class APIKeysFragment : Fragment() {

    private lateinit var binding: FragmentApiKeysBinding
    private val prefs by lazy { PreferencesManager(requireContext()) }
    private lateinit var adapter: APIKeysAdapter

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

        adapter = APIKeysAdapter(
            keys = emptyList(),
            onToggleActive = { key, isActive -> updateKey(key) { it.copy(isActive = isActive) } },
            onDelete = { key -> deleteKey(key) }
        )
        binding.keysRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { runAutoProvisioning() }
        binding.addKeyButton.setOnClickListener { showAddPersonalKeyDialog() }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val keys = prefs.getAPIKeys()
        if (keys.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.keysRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.keysRecyclerView.visibility = View.VISIBLE
            adapter.updateKeys(keys)
        }
    }

    private fun updateKey(target: APIKey, transform: (APIKey) -> APIKey) {
        val updated = prefs.getAPIKeys().map { if (it === target || it == target) transform(it) else it }
        prefs.saveAPIKeys(updated)
        refreshList()
    }

    private fun deleteKey(target: APIKey) {
        val remaining = prefs.getAPIKeys().filterNot { it == target }
        prefs.saveAPIKeys(remaining)
        Toast.makeText(requireContext(), "کلید حذف شد", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun runAutoProvisioning() {
        Toast.makeText(requireContext(), "در حال بررسی کلیدهای رایگان...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = AutoProvisioningManager.autoProvision(requireContext())
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "کلیدها با موفقیت دریافت شدند", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "دریافت کلیدهای رایگان ناموفق بود؛ اتصال اینترنت را بررسی کنید",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshList()
        }
    }

    private fun showAddPersonalKeyDialog() {
        val providers = listOf("GAPGPT", "OPENAI", "LIARA", "CUSTOM")
        val providerInput = AutoCompleteTextView(requireContext()).apply {
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, providers))
            hint = "سرویس (مثلاً GAPGPT)"
            setText(providers.first(), false)
        }
        val keyInput = EditText(requireContext()).apply { hint = "کلید API (sk-...)" }
        val baseUrlInput = EditText(requireContext()).apply {
            hint = "آدرس Base URL (فقط برای CUSTOM لازم است)"
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(providerInput)
            addView(keyInput)
            addView(baseUrlInput)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("افزودن کلید شخصی")
            .setMessage("این کلید فقط روی همین گوشی ذخیره می‌شود و مصرف آن نامحدود و بدون سقف روزانه است، چون هزینه‌ی آن مستقیم از حساب خودتان کسر می‌شود.")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val providerName = providerInput.text.toString().trim().uppercase()
                val keyValue = keyInput.text.toString().trim()
                val baseUrl = baseUrlInput.text.toString().trim().ifBlank { null }

                if (keyValue.isBlank()) {
                    Toast.makeText(requireContext(), "کلید API را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val provider = try {
                    AIProvider.valueOf(providerName)
                } catch (e: IllegalArgumentException) {
                    AIProvider.CUSTOM
                }
                if (provider == AIProvider.CUSTOM && baseUrl.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "برای CUSTOM باید Base URL را هم وارد کنید", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val newKey = APIKey(
                    provider = provider,
                    key = keyValue,
                    baseUrl = baseUrl,
                    isActive = true,
                    isAutoProvisioned = false
                )
                prefs.saveAPIKeys(prefs.getAPIKeys() + newKey)
                Toast.makeText(requireContext(), "کلید شخصی اضافه و فعال شد", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
