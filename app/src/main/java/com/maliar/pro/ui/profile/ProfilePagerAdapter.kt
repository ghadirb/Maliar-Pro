package com.maliar.pro.ui.profile

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ProfilePagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SettingsFragment()
            1 -> APIKeysFragment()
            2 -> ContactsFragment()
            else -> SettingsFragment()
        }
    }
}
