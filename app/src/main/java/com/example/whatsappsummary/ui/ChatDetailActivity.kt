package com.example.whatsappsummary.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.whatsappsummary.R
import com.example.whatsappsummary.databinding.ActivityChatDetailBinding
import com.example.whatsappsummary.ui.fragment.MessagesFragment
import com.example.whatsappsummary.ui.fragment.SummariesFragment
import com.example.whatsappsummary.viewmodel.ChatDetailViewModel
import com.google.android.material.tabs.TabLayout

class ChatDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatDetailBinding
    private lateinit var viewModel: ChatDetailViewModel
    private var chatId: String? = null
    private var chatName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Obtener datos del intent
        chatId = intent.getStringExtra("CHAT_ID")
        chatName = intent.getStringExtra("CHAT_NAME")
        
        if (chatId == null) {
            finish()
            return
        }
        
        setupToolbar()
        setupViewModel()
        setupTabs()
        
        // Cargar fragmento inicial
        if (savedInstanceState == null) {
            showFragment(MessagesFragment.newInstance(chatId!!))
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = chatName ?: "Chat"
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ChatDetailViewModel::class.java]
        viewModel.loadChatData(chatId!!)
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Mensajes"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Resúmenes"))
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showFragment(MessagesFragment.newInstance(chatId!!))
                    1 -> showFragment(SummariesFragment.newInstance(chatId!!))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
