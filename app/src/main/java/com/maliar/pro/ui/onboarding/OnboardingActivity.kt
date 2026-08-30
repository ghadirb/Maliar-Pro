package com.maliar.pro.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.maliar.pro.MainActivity
import com.maliar.pro.databinding.ActivityOnboardingBinding
import com.maliar.pro.utils.PreferencesManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val pages = listOf(
        OnboardingPage("💰", "مدیریت مالی هوشمند", "درآمد، هزینه، چک و قسط خود را در یک‌جا، با تقویم شمسی، مدیریت کنید"),
        OnboardingPage("⏰", "یادآوری‌های قابل‌اعتماد", "برای اقساط، چک‌ها و کارهای روزمره‌تان یادآوری بسازید - با صدای دلخواه خودتان"),
        OnboardingPage("📊", "گزارش‌های حرفه‌ای", "روند درآمد و هزینه‌تان را ببینید و با خروجی Excel/PDF نگهش دارید"),
        OnboardingPage("🤖", "دستیار هوش مصنوعی", "سوال بپرسید یا با گفتن یک جمله ساده، هزینه یا یادآوری ثبت کنید")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingViewPager.adapter = OnboardingPagerAdapter(pages)
        TabLayoutMediator(binding.onboardingDotsIndicator, binding.onboardingViewPager) { _, _ -> }.attach()

        binding.skipButton.setOnClickListener { finishOnboarding() }

        binding.onboardingNextButton.setOnClickListener {
            val current = binding.onboardingViewPager.currentItem
            if (current < pages.size - 1) {
                binding.onboardingViewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.onboardingViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.onboardingNextButton.text = if (position == pages.size - 1) "شروع کنید" else "بعدی"
            }
        })
    }

    private fun finishOnboarding() {
        PreferencesManager(this).setOnboardingCompleted(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
