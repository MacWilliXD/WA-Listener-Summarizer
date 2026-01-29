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
        
        try {
            setupToolbar()
            setupViewModel()
            setupTabs()
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error inicializando vista de detalle", e)
            finish()
            return
        }
        
        // Cargar fragmento inicial
        if (savedInstanceState == null) {
            try {
                showFragment(MessagesFragment.newInstance(chatId!!))
            } catch (e: Exception) {
                android.util.Log.e("ChatDetailActivity", "Error al cargar fragmento inicial", e)
            }
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
        try {
            chatId?.let { viewModel.loadChatData(it) }
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error cargando datos del chat", e)
        }
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
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            android.util.Log.e("ChatDetailActivity", "Error mostrando fragmento", e)
        }
    }
}
