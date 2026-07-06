package com.maliar.pro.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemApiKeyBinding
import com.maliar.pro.models.APIKey

/**
 * Shows every stored AI key (both the free shared/auto-provisioned ones and any personal
 * key the person added themselves). Auto-provisioned keys can be toggled but not deleted -
 * deleting them would just have them silently reappear on the next auto-provisioning
 * check anyway, so hiding that button avoids a confusing no-op delete.
 */
class APIKeysAdapter(
    private var keys: List<APIKey>,
    private val onToggleActive: (APIKey, Boolean) -> Unit,
    private val onDelete: (APIKey) -> Unit
) : RecyclerView.Adapter<APIKeysAdapter.KeyViewHolder>() {

    class KeyViewHolder(val binding: ItemApiKeyBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateKeys(newKeys: List<APIKey>) {
        keys = newKeys
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val binding = ItemApiKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KeyViewHolder(binding)
    }

    override fun getItemCount(): Int = keys.size

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = keys[position]
        val binding = holder.binding

        binding.providerText.text = key.provider.name
        binding.sourceBadge.text = if (key.isAutoProvisioned) "رایگان مشترک" else "شخصی"

        val raw = key.key
        binding.maskedKeyText.text = if (raw.length > 10) {
            "${raw.take(6)}••••••${raw.takeLast(4)}"
        } else {
            "••••••••"
        }

        // Avoid firing onToggleActive while we're just setting the initial checked state
        // from data - listener is set AFTER isChecked so the very first bind doesn't
        // trigger a spurious "user toggled it" callback.
        binding.activeSwitch.setOnCheckedChangeListener(null)
        binding.activeSwitch.isChecked = key.isActive
        binding.activeSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleActive(key, isChecked)
        }

        // Deleting an auto-provisioned (shared/free) key is pointless - the next
        // provisioning check just re-adds it - so that button is hidden for those rows
        // to avoid a confusing no-op delete.
        binding.deleteButton.visibility = if (key.isAutoProvisioned) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
        binding.deleteButton.setOnClickListener { onDelete(key) }
    }
}
