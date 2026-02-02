package com.example.whatsappsummary.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.ActivityAppNotificationsBinding
import com.example.whatsappsummary.ui.adapter.NotificationAdapter
import com.example.whatsappsummary.viewmodel.AppNotificationsViewModel
import kotlinx.coroutines.launch

class AppNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppNotificationsBinding
    private lateinit var viewModel: AppNotificationsViewModel
    private lateinit var adapter: NotificationAdapter
    private var packageName: String = ""
    private var appName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener parámetros del intent
        packageName = intent.getStringExtra("PACKAGE_NAME") ?: ""
        appName = intent.getStringExtra("APP_NAME") ?: packageName

        setupViewModel()
        setupRecyclerView()
        setupToolbar()
        loadNotifications()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[AppNotificationsViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter()

        binding.recyclerNotifications.apply {
            layoutManager = LinearLayoutManager(this@AppNotificationsActivity)
            adapter = this@AppNotificationsActivity.adapter
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = appName
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            val notifications = viewModel.getNotificationsForApp(packageName)
            adapter.submitList(notifications)

            if (notifications.isEmpty()) {
                binding.textEmpty.visibility = android.view.View.VISIBLE
                binding.recyclerNotifications.visibility = android.view.View.GONE
            } else {
                binding.textEmpty.visibility = android.view.View.GONE
                binding.recyclerNotifications.visibility = android.view.View.VISIBLE
            }
        }
    }
}