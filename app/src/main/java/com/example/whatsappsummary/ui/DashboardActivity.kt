package com.example.whatsappsummary.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
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
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
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
    private var selectedStartLine: Long = 0L
    private var selectedEndLine: Long = 0L
    private var selectedStartPie: Long = 0L
    private var selectedEndPie: Long = 0L
    private var selectedTopAppsLimit: Int = 5 // límite de aplicaciones a mostrar en lista (Top 5 por defecto)
    private var selectedPieTopAppsLimit: Int = 10 // límite de aplicaciones a mostrar en pie chart
    private var selectedPieApp: String = "all" // aplicación seleccionada para pie chart
    private var availableApps: List<String> = emptyList() // lista de todas las aplicaciones disponibles
    private var availableAppNames: List<String> = emptyList() // lista de nombres de aplicaciones para mostrar
    private var appNameToPackageMap: MutableMap<String, String> = mutableMapOf() // mapa nombre -> package
    private var packageToAppNameMap: MutableMap<String, String> = mutableMapOf() // mapa package -> nombre
    private var selectedGranularity: String = "day" // granularidad por defecto: día
    private var currentDailyData: List<Triple<String, Long, Int>> = emptyList()
    private var currentPieTotal: Int = 0

    private val pastelChartColors: List<Int> by lazy {
        listOf(
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_1),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_2),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_3),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_4),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_5),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_6),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_7),
            androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.chart_8)
        )
    }

    // Función para formatear números grandes con sufijos
    private fun formatLargeNumber(value: Float): String {
        val absValue = Math.abs(value)
        return when {
            absValue >= 1_000_000_000_000 -> String.format("%.1fT", value / 1_000_000_000_000) // Trillones
            absValue >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000) // Billones
            absValue >= 1_000_000 -> String.format("%.1fM", value / 1_000_000) // Millones
            absValue >= 1_000 -> String.format("%.1fK", value / 1_000) // Miles
            else -> String.format("%.0f", value) // Valores normales sin decimales
        }
    }

    // Función para formatear números completos con separadores de miles
    private fun formatFullNumber(value: Float): String {
        val intValue = value.toInt()
        return String.format("%,d", intValue).replace(",", ".")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupDateDefaults()
        setupRecyclerView()
        setupCharts()
        setupSpinners()
        setupObservers()
        setupButtons()

        // Inicializar título del gráfico de pastel
        updatePieChartTitle()

        // Load initial chart data with default filters
        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
        viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
    }

    private fun setupDateDefaults() {
        val cal = Calendar.getInstance()
        selectedEndLine = cal.timeInMillis
        selectedEndPie = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, -7)
        selectedStartLine = cal.timeInMillis
        selectedStartPie = cal.timeInMillis
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
        val axisTextColor = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorTextSecondary)
        val gridColorVal = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorDivider)
        val axisLineCol = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorOutline)

        // Configurar PieChart
        binding.pieChart.apply {
            description.isEnabled = false
            isRotationEnabled = true
            setDrawEntryLabels(false)
            legend.isEnabled = false
            setExtraOffsets(12f, 12f, 12f, 12f)
            setDrawCenterText(false)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 44f
            transparentCircleRadius = 48f
            setTouchEnabled(true)
            isHighlightPerTapEnabled = true

            // Tooltip personalizado con clamp a los bordes del chart
            val pieMarker = com.example.whatsappsummary.ui.chart.ChartMarkerView(this@DashboardActivity) { entry, _ ->
                val pie = entry as? PieEntry
                val name = pie?.label?.takeIf { it.isNotBlank() } ?: "Sin nombre"
                val count = formatFullNumber(pie?.value ?: 0f)
                val percentage = if (currentPieTotal > 0 && pie != null) {
                    val p = (pie.value / currentPieTotal * 100).toInt()
                    "$p% del total"
                } else null
                com.example.whatsappsummary.ui.chart.ChartMarkerView.MarkerContent(
                    title = name,
                    value = "$count notificaciones",
                    hint = percentage
                )
            }
            pieMarker.chartView = binding.pieChart
            marker = pieMarker

            // Listener: no hace falta Toast, solo no-op para mantener la API
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {}
                override fun onNothingSelected() {}
            })
        }

        // Configurar LineChart
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false

            // Tooltip personalizado (pastel, coherente con la app) con clamp a los bordes
            val lineMarker = com.example.whatsappsummary.ui.chart.ChartMarkerView(this@DashboardActivity) { entry, _ ->
                val idx = entry?.x?.toInt() ?: 0
                if (idx in currentDailyData.indices) {
                    val (label, _, count) = currentDailyData[idx]
                    val pct = if (idx > 0) {
                        val prev = currentDailyData[idx - 1].third
                        if (prev > 0) {
                            val change = count - prev
                            val percentage = (change.toFloat() / prev.toFloat() * 100).toInt()
                            when {
                                percentage > 0 -> "▲ +$percentage% vs anterior"
                                percentage < 0 -> "▼ $percentage% vs anterior"
                                else -> "Sin cambio"
                            }
                        } else null
                    } else null
                    com.example.whatsappsummary.ui.chart.ChartMarkerView.MarkerContent(
                        title = label,
                        value = "${formatFullNumber(count.toFloat())} notificaciones",
                        hint = pct
                    )
                } else {
                    com.example.whatsappsummary.ui.chart.ChartMarkerView.MarkerContent("—", "", null)
                }
            }
            lineMarker.chartView = binding.lineChart
            marker = lineMarker

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = gridColorVal
                gridLineWidth = 1f
                textColor = axisTextColor
                textSize = 11f
                setDrawAxisLine(true)
                axisLineColor = axisLineCol
                axisLineWidth = 1f
                granularity = 1f
                labelRotationAngle = -45f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = gridColorVal
                gridLineWidth = 1f
                textColor = axisTextColor
                textSize = 11f
                setDrawAxisLine(true)
                axisLineColor = axisLineCol
                axisLineWidth = 1f
                setDrawZeroLine(true)
                zeroLineColor = axisLineCol
                zeroLineWidth = 1f
                axisMinimum = 0f
            }

            // Deshabilitar eje Y derecho
            axisRight.isEnabled = false

            // Configurar márgenes
            setExtraOffsets(10f, 20f, 20f, 10f)

            // Configurar zoom inteligente por dirección
            var zoomDirectionLocked = false
            var isHorizontalZoom = false
            var initialDistanceX = 0f
            var initialDistanceY = 0f

            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                    zoomDirectionLocked = false
                    if (me != null && me.pointerCount >= 2) {
                        // Calcular distancia inicial entre los dos primeros dedos
                        val x1 = me.getX(0)
                        val y1 = me.getY(0)
                        val x2 = me.getX(1)
                        val y2 = me.getY(1)
                        initialDistanceX = Math.abs(x2 - x1)
                        initialDistanceY = Math.abs(y2 - y1)
                    }
                }

                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                    // Resetear configuración de zoom al terminar el gesto
                    setScaleXEnabled(true)
                    setScaleYEnabled(true)
                    zoomDirectionLocked = false
                }

                override fun onChartLongPressed(me: MotionEvent?) {}

                override fun onChartDoubleTapped(me: MotionEvent?) {}

                override fun onChartSingleTapped(me: MotionEvent?) {}

                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}

                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                    // Determinar dirección predominante del zoom solo al inicio
                    if (!zoomDirectionLocked && me != null && me.pointerCount >= 2) {
                        // Calcular distancia actual entre los dedos
                        val x1 = me.getX(0)
                        val y1 = me.getY(0)
                        val x2 = me.getX(1)
                        val y2 = me.getY(1)
                        val currentDistanceX = Math.abs(x2 - x1)
                        val currentDistanceY = Math.abs(y2 - y1)

                        // Calcular cuánto han cambiado las distancias
                        val deltaX = Math.abs(currentDistanceX - initialDistanceX)
                        val deltaY = Math.abs(currentDistanceY - initialDistanceY)

                        // Usar un umbral mínimo para evitar detección prematura
                        val minThreshold = 20f // píxeles

                        if (deltaX > minThreshold || deltaY > minThreshold) {
                            if (deltaX > deltaY * 1.5f) {
                                // Zoom principalmente horizontal
                                setScaleXEnabled(true)
                                setScaleYEnabled(false)
                                isHorizontalZoom = true
                            } else if (deltaY > deltaX * 1.5f) {
                                // Zoom principalmente vertical
                                setScaleXEnabled(false)
                                setScaleYEnabled(true)
                                isHorizontalZoom = false
                            } else {
                                // Cambios similares, permitir ambas direcciones
                                setScaleXEnabled(true)
                                setScaleYEnabled(true)
                            }
                            zoomDirectionLocked = true
                        }
                    }
                }

                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
            }
            setVisibleXRangeMaximum(20f) // Máximo 20 puntos visibles
            setVisibleXRangeMinimum(3f)  // Mínimo 3 puntos visibles
        }
    }

    private fun setupSpinners() {
        // Spinner de límite de aplicaciones en PieChart
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

        // Spinner para selección de aplicación en PieChart
        // Se inicializará cuando lleguen los datos de aplicaciones

        // Botón para rango de fechas del PieChart
        binding.buttonPieDateRange.setOnClickListener {
            showDateRangePicker(true)
        }
        
        // Spinner para límite de aplicaciones mostradas
        val topAppsLimits = arrayOf("Top 5", "Top 10", "Top 15", "Top 30", "Todos")
        val topAppsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, topAppsLimits)
        topAppsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTopApps.adapter = topAppsAdapter
        binding.spinnerTopApps.setSelection(0) // Top 5 por defecto
        binding.spinnerTopApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTopAppsLimit = when (position) {
                    0 -> 5
                    1 -> 10
                    2 -> 15
                    3 -> 30
                    4 -> Int.MAX_VALUE // Todos
                    else -> Int.MAX_VALUE
                }
                // Actualizar solo la lista con el nuevo límite
                viewModel.notificationsByApp.value?.let { appStats ->
                    val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
                    adapter.submitList(limitedStats)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Spinner para granularidad de tiempo en línea del tiempo
        val timeGranularities = arrayOf("Minuto", "Hora", "Día", "Semana", "Mes")
        val timeGranularityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeGranularities)
        timeGranularityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeGranularity.adapter = timeGranularityAdapter
        binding.spinnerTimeGranularity.setSelection(2) // Día por defecto
        binding.spinnerTimeGranularity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGranularity = when (position) {
                    0 -> "minute"
                    1 -> "hour"
                    2 -> "day"
                    3 -> "week"
                    4 -> "month"
                    else -> "hour"
                }
                // Recargar datos de la línea del tiempo con la nueva granularidad
                viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateAppSpinner(appNames: List<String>) {
        val appList = mutableListOf("Todas las aplicaciones")
        appList.addAll(appNames)
        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApp.adapter = appAdapter

        // Encontrar la posición de la aplicación actualmente seleccionada
        val selectedPosition = if (selectedPackage == "all") {
            0
        } else {
            val appDisplayName = packageToAppNameMap[selectedPackage]
            val appIndex = appNames.indexOf(appDisplayName)
            if (appIndex >= 0) appIndex + 1 else 0
        }

        // Establecer la selección sin disparar el listener
        binding.spinnerApp.setSelection(selectedPosition, false)

        binding.spinnerApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousPackage = selectedPackage
                selectedPackage = if (position == 0) "all" else appNameToPackageMap[appNames[position - 1]] ?: "all"
                
                // Solo recargar si cambió la aplicación seleccionada
                if (previousPackage != selectedPackage) {
                    viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePieAppSpinner(appNames: List<String>) {
        val appList = mutableListOf("Todas las aplicaciones")
        appList.addAll(appNames)
        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPieApp.adapter = appAdapter

        // Encontrar la posición de la aplicación actualmente seleccionada
        val selectedPosition = if (selectedPieApp == "all") {
            0
        } else {
            val appDisplayName = packageToAppNameMap[selectedPieApp]
            val appIndex = appNames.indexOf(appDisplayName)
            if (appIndex >= 0) appIndex + 1 else 0
        }

        // Establecer la selección sin disparar el listener
        binding.spinnerPieApp.setSelection(selectedPosition, false)

        binding.spinnerPieApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousApp = selectedPieApp
                selectedPieApp = if (position == 0) "all" else appNameToPackageMap[appNames[position - 1]] ?: "all"

                // Solo recargar si cambió la aplicación seleccionada
                if (previousApp != selectedPieApp) {
                    // Actualizar título del gráfico
                    updatePieChartTitle()

                    // Recargar datos del pie chart
                    viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePieChartTitle() {
        val title = if (selectedPieApp == "all") {
            "Notificaciones por Aplicación"
        } else {
            val appDisplayName = packageToAppNameMap[selectedPieApp] ?: selectedPieApp
            "Chats de $appDisplayName"
        }
        binding.textPieChartTitle.text = title
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
        currentPieTotal = totalCount

        val dataSet = PieDataSet(entries, "").apply {
            colors = pastelChartColors
            valueTextSize = 10f
            valueTextColor = androidx.core.content.ContextCompat.getColor(this@DashboardActivity, com.example.whatsappsummary.R.color.colorOnPrimaryContainer)
            sliceSpace = 2f
            valueFormatter = object : IValueFormatter {
                override fun getFormattedValue(value: Float, entry: com.github.mikephil.charting.data.Entry?, dataSetIndex: Int, viewPortHandler: ViewPortHandler?): String {
                    val count = value.toInt()
                    val percentage = (value / totalCount * 100).toInt()
                    val formattedValue = formatLargeNumber(value)
                    return "$formattedValue ($percentage%)"
                }
            }
        }

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.setUsePercentValues(false) // Usar valores absolutos
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    private fun setupLineChart(dailyData: List<Triple<String, Long, Int>>) {
        currentDailyData = dailyData
        if (dailyData.isEmpty()) {
            binding.lineChart.clear()
            return
        }

        val entries = dailyData.mapIndexed { index, (_, _, count) ->
            Entry(index.toFloat(), count.toFloat())
        }

        val primaryColor = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorPrimary)
        val primaryDeep = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorPrimaryVariant)
        val accentColor = androidx.core.content.ContextCompat.getColor(this, com.example.whatsappsummary.R.color.colorSecondary)

        val dataSet = LineDataSet(entries, "").apply {
            color = primaryColor
            lineWidth = 2.5f
            setDrawCircles(true)
            setCircleColor(primaryColor)
            circleRadius = 4.5f
            setDrawCircleHole(true)
            circleHoleRadius = 2.5f

            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 40

            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = primaryDeep
            valueFormatter = object : IValueFormatter {
                override fun getFormattedValue(value: Float, entry: com.github.mikephil.charting.data.Entry?, dataSetIndex: Int, viewPortHandler: ViewPortHandler?): String {
                    val currentValue = value.toInt()
                    val index = entry?.x?.toInt() ?: 0
                    
                    // Calcular cambio porcentual respecto al valor anterior
                    val percentageText = if (index > 0 && index < entries.size) {
                        val previousValue = entries[index - 1].y.toInt()
                        if (previousValue > 0) {
                            val change = currentValue - previousValue
                            val percentage = (change.toFloat() / previousValue.toFloat() * 100).toInt()
                            if (percentage > 0) {
                                " ▲+${percentage}%"
                            } else if (percentage < 0) {
                                " ▼${percentage}%"
                            } else {
                                " ▬0%"
                            }
                        } else {
                            " ▲+${currentValue}%" // Si el anterior era 0, mostrar el valor actual como aumento
                        }
                    } else {
                        "" // No mostrar porcentaje para el primer punto
                    }
                    
                    val formattedValue = formatLargeNumber(value)
                    return "$formattedValue$percentageText"
                }
            }

            // Efectos visuales
            mode = LineDataSet.Mode.CUBIC_BEZIER // Línea suavizada
            cubicIntensity = 0.2f

            // Resaltar puntos
            highLightColor = accentColor
            highlightLineWidth = 1.5f
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
        }

        val data = LineData(dataSet)
        binding.lineChart.apply {
            this.data = data
            xAxis.valueFormatter = IndexAxisValueFormatter(dailyData.map { it.first })

            // MarkerView ya maneja el tooltip; listener no-op para preservar contrato.
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {}
                override fun onNothingSelected() {}
            })

            // Animación suave
            animateXY(1200, 1200)
        }
    }

    private fun setupObservers() {
        viewModel.totalNotifications.observe(this) { count ->
            binding.textTotalNotifications.text = formatLargeNumber(count.toFloat())
        }

        viewModel.totalApps.observe(this) { count ->
            binding.textTotalApps.text = count.toString()
        }

        viewModel.todayNotifications.observe(this) { count ->
            binding.textTodayNotifications.text = formatLargeNumber(count.toFloat())
        }

        viewModel.notificationsByApp.observe(this) { appStats ->
            // Aplicar el límite seleccionado
            val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
            adapter.submitList(limitedStats)
            
            // Crear mapas para relacionar nombres de apps con package names
            appNameToPackageMap.clear()
            packageToAppNameMap.clear()
            appStats.forEach { stat ->
                val displayName = stat.appName ?: stat.packageName
                appNameToPackageMap[displayName] = stat.packageName
                packageToAppNameMap[stat.packageName] = displayName
            }
            
            // Guardar la lista de nombres de aplicaciones disponibles para el spinner
            availableAppNames = appStats.map { it.appName ?: it.packageName }
            
            // Actualizar spinners de aplicaciones
            updateAppSpinner(availableAppNames)
            updatePieAppSpinner(availableAppNames)
        }

        viewModel.pieChartData.observe(this) { appStats ->
            // Aplicar el límite seleccionado para el PieChart
            val limitedStats = if (selectedPieTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedPieTopAppsLimit)
            setupPieChart(limitedStats)

            // Actualizar spinner de aplicaciones para el pie chart (usando todas las apps disponibles)
            updatePieAppSpinner(availableAppNames)
        }

        viewModel.lineChartData.observe(this) { dailyData ->
            setupLineChart(dailyData)
        }
    }

    private fun setupButtons() {
        binding.buttonRefresh.setOnClickListener {
            // Recargar todos los datos del dashboard
            viewModel.loadDashboardData()
            viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
            viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
            Toast.makeText(this, "Dashboard actualizado", Toast.LENGTH_SHORT).show()
        }

        binding.buttonViewChats.setOnClickListener {
            // Navegar a la vista de lista de chats y limpiar los filtros
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("CLEAR_FILTERS", true)
            startActivity(intent)
        }

        binding.buttonLineDateRange.setOnClickListener {
            showDateRangePicker(false)
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
                        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)

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

    private fun showDateRangePicker(isPie: Boolean) {
        val cal = Calendar.getInstance()
        val currentStart = if (isPie) selectedStartPie else selectedStartLine
        val currentEnd = if (isPie) selectedEndPie else selectedEndLine

        cal.timeInMillis = currentStart
        val startYear = cal.get(Calendar.YEAR)
        val startMonth = cal.get(Calendar.MONTH)
        val startDay = cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, year, month, day ->
            val startCal = Calendar.getInstance()
            startCal.set(year, month, day, 0, 0, 0)
            startCal.set(Calendar.MILLISECOND, 0)
            val selectedStart = startCal.timeInMillis

            // Now pick end date
            cal.timeInMillis = currentEnd
            val endYear = cal.get(Calendar.YEAR)
            val endMonth = cal.get(Calendar.MONTH)
            val endDay = cal.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, eYear, eMonth, eDay ->
                val endCal = Calendar.getInstance()
                endCal.set(eYear, eMonth, eDay, 23, 59, 59)
                endCal.set(Calendar.MILLISECOND, 999)
                val selectedEnd = endCal.timeInMillis

                if (selectedStart > selectedEnd) {
                    Toast.makeText(this, "La fecha de inicio no puede ser posterior a la fecha de fin", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                if (isPie) {
                    selectedStartPie = selectedStart
                    selectedEndPie = selectedEnd
                    viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
                } else {
                    selectedStartLine = selectedStart
                    selectedEndLine = selectedEnd
                    viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
                }
            }, endYear, endMonth, endDay).show()
        }, startYear, startMonth, startDay).show()
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos cuando volvemos a la actividad
        viewModel.loadDashboardData()
        // También recargar datos filtrados del PieChart con filtro de app
        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
        // Recargar línea del tiempo con granularidad actual
        viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
    }
}