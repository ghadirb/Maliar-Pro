package com.maliar.pro.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maliar.pro.databinding.ItemOnboardingPageBinding

data class OnboardingPage(val icon: String, val title: String, val description: String)

class OnboardingPagerAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingPagerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(pages[position])
    override fun getItemCount() = pages.size

    class ViewHolder(private val binding: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: OnboardingPage) {
            binding.onboardingIcon.text = page.icon
            binding.onboardingTitle.text = page.title
            binding.onboardingDescription.text = page.description
        }
    }
}
