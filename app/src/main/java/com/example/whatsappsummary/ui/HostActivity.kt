package com.example.whatsappsummary.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.whatsappsummary.databinding.ActivityHostBinding
import com.example.whatsappsummary.viewmodel.NavSharedViewModel
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Host de las dos vistas principales (Chats + Dashboard) en un ViewPager2.
 * La navegación entre pantallas es por swipe; no hay botones.
 */
class HostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHostBinding
    private val navViewModel: NavSharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = HostPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1

        // Indicador de dos puntos (clickables: permiten saltar a la otra página)
        TabLayoutMediator(binding.pageIndicator, binding.viewPager) { _, _ -> /* sin labels */ }.attach()

        // Escuchar peticiones de cambio de página desde los fragments
        navViewModel.pageRequest.observe(this) { requested ->
            if (requested != null && requested in 0..1 && binding.viewPager.currentItem != requested) {
                binding.viewPager.setCurrentItem(requested, true)
            }
        }

        // Home = Dashboard (página 0). Back desde cualquier otra vuelve al home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.viewPager.currentItem != PAGE_DASHBOARD) {
                    binding.viewPager.setCurrentItem(PAGE_DASHBOARD, true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private class HostPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            PAGE_DASHBOARD -> DashboardFragment()
            PAGE_CHATS -> ChatsListFragment()
            else -> throw IllegalStateException("Unexpected page: $position")
        }
    }

    companion object {
        const val PAGE_DASHBOARD = 0
        const val PAGE_CHATS = 1
    }
}
