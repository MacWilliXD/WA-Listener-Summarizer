package com.example.whatsappsummary.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.databinding.ActivityDashboardBinding
import com.example.whatsappsummary.ui.adapter.AppStatsAdapter
import com.example.whatsappsummary.ui.dialog.AppOptionsDialog
import com.example.whatsappsummary.viewmodel.DashboardViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ViewPortHandler
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: AppStatsAdapter
    private var selectedPackage: String = "all"
    private var selectedRange: Int = 7 // días
    private var selectedPieRange: Int = 7 // días para el PieChart
    private var selectedTopAppsLimit: Int = 10 // límite de aplicaciones a mostrar en lista
    private var selectedPieTopAppsLimit: Int = 10 // límite de aplicaciones a mostrar en pie chart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupCharts()
        setupSpinners()
        setupObservers()
        setupButtons()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = AppStatsAdapter(
            onAppClick = { appStats ->
                // Navegar a la lista de chats filtrada por aplicación
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("FILTER_PACKAGE", appStats.packageName)
                    putExtra("FILTER_APP_NAME", appStats.appName ?: appStats.packageName)
                }
                startActivity(intent)
            },
            onAppLongClick = { appStats ->
                showAppOptionsDialog(appStats)
            }
        )
        adapter.setPackageManager(packageManager)

        binding.recyclerTopApps.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = this@DashboardActivity.adapter
        }
    }

    private fun setupCharts() {
        // Configurar PieChart
        binding.pieChart.apply {
            description.isEnabled = false
            isRotationEnabled = true
            setDrawEntryLabels(false) // Deshabilitar etiquetas en el gráfico
            legend.isEnabled = false // Deshabilitar leyenda
            setExtraOffsets(5f, 5f, 5f, 5f) // Márgenes uniformes
            setDrawCenterText(false)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 40f
            transparentCircleRadius = 45f
            setTouchEnabled(true)
            isHighlightPerTapEnabled = true
            
            // Listener para mostrar el nombre al presionar
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val appName = e.label ?: "Desconocido"
                        val value = e.value.toInt()
                        Toast.makeText(this@DashboardActivity, "$appName: $value notificaciones", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onNothingSelected() {
                    // No hacer nada
                }
            })
        }

        // Configurar LineChart
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun setupSpinners() {
        // Spinner de rango de tiempo para PieChart
        val pieRanges = arrayOf("Hoy", "Última semana", "Último mes", "Últimos 3 meses", "Último año")
        val pieRangeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pieRanges)
        pieRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPieRange.adapter = pieRangeAdapter
        binding.spinnerPieRange.setSelection(1) // Última semana por defecto
        
        // Cargar datos iniciales del PieChart
        viewModel.loadPieChartData(selectedPieRange)
        
        binding.spinnerPieRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPieRange = when (position) {
                    0 -> 1
                    1 -> 7
                    2 -> 30
                    3 -> 90
                    4 -> 365
                    else -> 7
                }
                viewModel.loadPieChartData(selectedPieRange)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Spinner para límite de aplicaciones en PieChart
        val pieTopAppsLimits = arrayOf("Top 5", "Top 10", "Top 15", "Top 30", "Todos")
        val pieTopAppsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pieTopAppsLimits)
        pieTopAppsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPieTopApps.adapter = pieTopAppsAdapter
        binding.spinnerPieTopApps.setSelection(1) // Top 10 por defecto
        binding.spinnerPieTopApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPieTopAppsLimit = when (position) {
                    0 -> 5
                    1 -> 10
                    2 -> 15
                    3 -> 30
                    4 -> Int.MAX_VALUE // Todos
                    else -> 10
                }
                // Actualizar solo el PieChart con el nuevo límite
                viewModel.pieChartData.value?.let { appStats ->
                    val limitedStats = if (selectedPieTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedPieTopAppsLimit)
                    setupPieChart(limitedStats)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Spinner de rango de tiempo para LineChart
        val ranges = arrayOf("Última semana", "Último mes", "Últimos 3 meses", "Último año")
        val rangeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ranges)
        rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRange.adapter = rangeAdapter
        binding.spinnerRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRange = when (position) {
                    0 -> 7
                    1 -> 30
                    2 -> 90
                    3 -> 365
                    else -> 7
                }
                viewModel.loadLineChartData(selectedPackage, selectedRange)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Spinner para límite de aplicaciones mostradas
        val topAppsLimits = arrayOf("Top 5", "Top 10", "Top 15", "Top 30", "Todos")
        val topAppsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, topAppsLimits)
        topAppsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTopApps.adapter = topAppsAdapter
        binding.spinnerTopApps.setSelection(1) // Top 10 por defecto
        binding.spinnerTopApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTopAppsLimit = when (position) {
                    0 -> 5
                    1 -> 10
                    2 -> 15
                    3 -> 30
                    4 -> Int.MAX_VALUE // Todos
                    else -> 10
                }
                // Actualizar solo la lista con el nuevo límite
                viewModel.notificationsByApp.value?.let { appStats ->
                    val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
                    adapter.submitList(limitedStats)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateAppSpinner(apps: List<String>) {
        val appList = mutableListOf("Todas las aplicaciones")
        appList.addAll(apps)
        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApp.adapter = appAdapter
        binding.spinnerApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPackage = if (position == 0) "all" else apps[position - 1]
                viewModel.loadLineChartData(selectedPackage, selectedRange)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPieChart(appStats: List<com.example.whatsappsummary.viewmodel.AppNotificationStats>) {
        if (appStats.isEmpty()) {
            binding.pieChart.clear()
            return
        }

        val entries = appStats.map { stat ->
            PieEntry(stat.notificationCount.toFloat(), stat.appName ?: stat.packageName)
        }

        val totalCount = entries.sumOf { it.value.toInt() }
        
        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
            valueFormatter = object : IValueFormatter {
                override fun getFormattedValue(value: Float, entry: com.github.mikephil.charting.data.Entry?, dataSetIndex: Int, viewPortHandler: ViewPortHandler?): String {
                    val count = value.toInt()
                    val percentage = (value / totalCount * 100).toInt()
                    return "$count ($percentage%)"
                }
            }
        }

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.setUsePercentValues(false) // Usar valores absolutos
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    private fun setupLineChart(dailyData: List<Pair<String, Int>>) {
        if (dailyData.isEmpty()) {
            binding.lineChart.clear()
            return
        }

        val entries = dailyData.mapIndexed { index, (_, count) ->
            Entry(index.toFloat(), count.toFloat())
        }

        val dataSet = LineDataSet(entries, "Notificaciones").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
        }

        val data = LineData(dataSet)
        binding.lineChart.apply {
            this.data = data
            xAxis.valueFormatter = IndexAxisValueFormatter(dailyData.map { it.first })
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f
            invalidate()
        }
    }

    private fun setupObservers() {
        viewModel.totalNotifications.observe(this) { count ->
            binding.textTotalNotifications.text = count.toString()
        }

        viewModel.totalApps.observe(this) { count ->
            binding.textTotalApps.text = count.toString()
        }

        viewModel.todayNotifications.observe(this) { count ->
            binding.textTodayNotifications.text = count.toString()
        }

        viewModel.notificationsByApp.observe(this) { appStats ->
            // Aplicar el límite seleccionado
            val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
            adapter.submitList(limitedStats)
            
            // Actualizar spinner de apps
            val packageNames = appStats.map { it.packageName }
            updateAppSpinner(packageNames)
        }

        viewModel.pieChartData.observe(this) { appStats ->
            // Aplicar el límite seleccionado para el PieChart
            val limitedStats = if (selectedPieTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedPieTopAppsLimit)
            setupPieChart(limitedStats)
        }

        viewModel.lineChartData.observe(this) { dailyData ->
            setupLineChart(dailyData)
        }
    }

    private fun setupButtons() {
        binding.buttonRefresh.setOnClickListener {
            // Recargar todos los datos del dashboard
            viewModel.loadDashboardData()
            viewModel.loadPieChartData(selectedPieRange)
            viewModel.loadLineChartData(selectedPackage, selectedRange)
            Toast.makeText(this, "Dashboard actualizado", Toast.LENGTH_SHORT).show()
        }

        binding.buttonViewChats.setOnClickListener {
            // Navegar a la vista de lista de chats sin filtro
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAppOptionsDialog(appStats: com.example.whatsappsummary.viewmodel.AppNotificationStats) {
        val dialog = AppOptionsDialog(
            context = this,
            appStats = appStats,
            onDelete = {
                // Ejecutar la eliminación de forma secuencial
                lifecycleScope.launch {
                    try {
                        // Ejecutar la eliminación (ahora es suspend)
                        viewModel.deleteAppNotifications(appStats.packageName)

                        // Recargar los datos del dashboard para reflejar los cambios
                        viewModel.loadDashboardData()
                        // También recargar datos del PieChart
                        viewModel.loadPieChartData(selectedPieRange)

                        // Mostrar mensaje de confirmación
                        Toast.makeText(this@DashboardActivity, "Aplicación y sus notificaciones eliminadas", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@DashboardActivity, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onIgnoreChanged = { isIgnored ->
                // Notificar al adapter que los datos han cambiado para actualizar la apariencia visual
                adapter.notifyDataSetChanged()
            }
        )
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos cuando volvemos a la actividad
        viewModel.loadDashboardData()
        // También recargar datos filtrados del PieChart
        viewModel.loadPieChartData(selectedPieRange)
    }
}