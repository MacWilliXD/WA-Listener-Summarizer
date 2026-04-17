package com.example.whatsappsummary.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whatsappsummary.R
import com.example.whatsappsummary.databinding.FragmentDashboardBinding
import com.example.whatsappsummary.ui.adapter.AppStatsAdapter
import com.example.whatsappsummary.ui.dialog.AppOptionsDialog
import com.example.whatsappsummary.viewmodel.DashboardViewModel
import com.example.whatsappsummary.viewmodel.NavSharedViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private val navViewModel: NavSharedViewModel by activityViewModels()
    private lateinit var adapter: AppStatsAdapter

    private var selectedPackage: String = "all"
    private var selectedStartLine: Long = 0L
    private var selectedEndLine: Long = 0L
    private var selectedStartPie: Long = 0L
    private var selectedEndPie: Long = 0L
    private var selectedTopAppsLimit: Int = 5
    private var selectedPieTopAppsLimit: Int = 10
    private var selectedPieApp: String = "all"
    private var availableAppNames: List<String> = emptyList()
    private var appNameToPackageMap: MutableMap<String, String> = mutableMapOf()
    private var packageToAppNameMap: MutableMap<String, String> = mutableMapOf()
    private var selectedGranularity: String = "day"
    private var currentDailyData: List<Triple<String, Long, Int>> = emptyList()
    private var currentPieTotal: Int = 0

    private val pastelChartColors: List<Int> by lazy {
        listOf(
            ContextCompat.getColor(requireContext(), R.color.chart_1),
            ContextCompat.getColor(requireContext(), R.color.chart_2),
            ContextCompat.getColor(requireContext(), R.color.chart_3),
            ContextCompat.getColor(requireContext(), R.color.chart_4),
            ContextCompat.getColor(requireContext(), R.color.chart_5),
            ContextCompat.getColor(requireContext(), R.color.chart_6),
            ContextCompat.getColor(requireContext(), R.color.chart_7),
            ContextCompat.getColor(requireContext(), R.color.chart_8)
        )
    }

    private fun formatLargeNumber(value: Float): String {
        val absValue = Math.abs(value)
        return when {
            absValue >= 1_000_000_000_000 -> String.format("%.1fT", value / 1_000_000_000_000)
            absValue >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000)
            absValue >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
            absValue >= 1_000 -> String.format("%.1fK", value / 1_000)
            else -> String.format("%.0f", value)
        }
    }

    private fun formatFullNumber(value: Float): String {
        val intValue = value.toInt()
        return String.format("%,d", intValue).replace(",", ".")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDateDefaults()
        setupRecyclerView()
        setupCharts()
        setupSpinners()
        setupObservers()
        setupButtons()

        updatePieChartTitle()

        viewModel.loadDashboardData()
        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
        viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        viewModel.loadDashboardData()
        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
        viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                // Ahora no lanzamos MainActivity: pedimos cambio de página con filtro.
                navViewModel.requestShowChats(
                    packageName = appStats.packageName,
                    appName = appStats.appName ?: appStats.packageName
                )
            },
            onAppLongClick = { appStats -> showAppOptionsDialog(appStats) }
        )
        adapter.setPackageManager(requireContext().packageManager)

        binding.recyclerTopApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DashboardFragment.adapter
        }
    }

    private fun setupCharts() {
        val axisTextColor = ContextCompat.getColor(requireContext(), R.color.colorTextSecondary)
        val gridColorVal = ContextCompat.getColor(requireContext(), R.color.colorDivider)
        val axisLineCol = ContextCompat.getColor(requireContext(), R.color.colorOutline)

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

            val pieMarker = com.example.whatsappsummary.ui.chart.ChartMarkerView(requireContext()) { entry, _ ->
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

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {}
                override fun onNothingSelected() {}
            })
        }

        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false

            val lineMarker = com.example.whatsappsummary.ui.chart.ChartMarkerView(requireContext()) { entry, _ ->
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

            axisRight.isEnabled = false
            setExtraOffsets(10f, 20f, 20f, 10f)

            var zoomDirectionLocked = false
            var initialDistanceX = 0f
            var initialDistanceY = 0f

            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                    zoomDirectionLocked = false
                    if (me != null && me.pointerCount >= 2) {
                        initialDistanceX = Math.abs(me.getX(1) - me.getX(0))
                        initialDistanceY = Math.abs(me.getY(1) - me.getY(0))
                    }
                }

                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                    setScaleXEnabled(true)
                    setScaleYEnabled(true)
                    zoomDirectionLocked = false
                }

                override fun onChartLongPressed(me: MotionEvent?) {}
                override fun onChartDoubleTapped(me: MotionEvent?) {}
                override fun onChartSingleTapped(me: MotionEvent?) {}
                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}

                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                    if (!zoomDirectionLocked && me != null && me.pointerCount >= 2) {
                        val currentDistanceX = Math.abs(me.getX(1) - me.getX(0))
                        val currentDistanceY = Math.abs(me.getY(1) - me.getY(0))
                        val deltaX = Math.abs(currentDistanceX - initialDistanceX)
                        val deltaY = Math.abs(currentDistanceY - initialDistanceY)
                        val minThreshold = 20f
                        if (deltaX > minThreshold || deltaY > minThreshold) {
                            if (deltaX > deltaY * 1.5f) {
                                setScaleXEnabled(true); setScaleYEnabled(false)
                            } else if (deltaY > deltaX * 1.5f) {
                                setScaleXEnabled(false); setScaleYEnabled(true)
                            } else {
                                setScaleXEnabled(true); setScaleYEnabled(true)
                            }
                            zoomDirectionLocked = true
                        }
                    }
                }

                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
            }
            setVisibleXRangeMaximum(20f)
            setVisibleXRangeMinimum(3f)
        }
    }

    private fun setupSpinners() {
        val pieTopAppsLimits = arrayOf("Top 5", "Top 10", "Top 15", "Top 30", "Todos")
        val pieTopAppsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, pieTopAppsLimits)
        pieTopAppsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPieTopApps.adapter = pieTopAppsAdapter
        binding.spinnerPieTopApps.setSelection(1)
        binding.spinnerPieTopApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPieTopAppsLimit = when (position) { 0 -> 5; 1 -> 10; 2 -> 15; 3 -> 30; 4 -> Int.MAX_VALUE; else -> 10 }
                viewModel.pieChartData.value?.let { appStats ->
                    val limitedStats = if (selectedPieTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedPieTopAppsLimit)
                    setupPieChart(limitedStats)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.buttonPieDateRange.setOnClickListener { showDateRangePicker(true) }

        val topAppsLimits = arrayOf("Top 5", "Top 10", "Top 15", "Top 30", "Todos")
        val topAppsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, topAppsLimits)
        topAppsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTopApps.adapter = topAppsAdapter
        binding.spinnerTopApps.setSelection(0)
        binding.spinnerTopApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTopAppsLimit = when (position) { 0 -> 5; 1 -> 10; 2 -> 15; 3 -> 30; 4 -> Int.MAX_VALUE; else -> Int.MAX_VALUE }
                viewModel.notificationsByApp.value?.let { appStats ->
                    val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
                    adapter.submitList(limitedStats)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val timeGranularities = arrayOf("Minuto", "Hora", "Día", "Semana", "Mes")
        val timeGranularityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeGranularities)
        timeGranularityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeGranularity.adapter = timeGranularityAdapter
        binding.spinnerTimeGranularity.setSelection(2)
        binding.spinnerTimeGranularity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGranularity = when (position) { 0 -> "minute"; 1 -> "hour"; 2 -> "day"; 3 -> "week"; 4 -> "month"; else -> "hour" }
                viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateAppSpinner(appNames: List<String>) {
        if (_binding == null) return
        val appList = mutableListOf("Todas las aplicaciones")
        appList.addAll(appNames)
        val appAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApp.adapter = appAdapter

        val selectedPosition = if (selectedPackage == "all") 0
        else {
            val appDisplayName = packageToAppNameMap[selectedPackage]
            val appIndex = appNames.indexOf(appDisplayName)
            if (appIndex >= 0) appIndex + 1 else 0
        }
        binding.spinnerApp.setSelection(selectedPosition, false)

        binding.spinnerApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousPackage = selectedPackage
                selectedPackage = if (position == 0) "all" else appNameToPackageMap[appNames[position - 1]] ?: "all"
                if (previousPackage != selectedPackage) {
                    viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePieAppSpinner(appNames: List<String>) {
        if (_binding == null) return
        val appList = mutableListOf("Todas las aplicaciones")
        appList.addAll(appNames)
        val appAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPieApp.adapter = appAdapter

        val selectedPosition = if (selectedPieApp == "all") 0
        else {
            val appDisplayName = packageToAppNameMap[selectedPieApp]
            val appIndex = appNames.indexOf(appDisplayName)
            if (appIndex >= 0) appIndex + 1 else 0
        }
        binding.spinnerPieApp.setSelection(selectedPosition, false)

        binding.spinnerPieApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousApp = selectedPieApp
                selectedPieApp = if (position == 0) "all" else appNameToPackageMap[appNames[position - 1]] ?: "all"
                if (previousApp != selectedPieApp) {
                    updatePieChartTitle()
                    viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePieChartTitle() {
        if (_binding == null) return
        val title = if (selectedPieApp == "all") {
            "Notificaciones por Aplicación"
        } else {
            val appDisplayName = packageToAppNameMap[selectedPieApp] ?: selectedPieApp
            "Chats de $appDisplayName"
        }
        binding.textPieChartTitle.text = title
    }

    private fun setupPieChart(appStats: List<com.example.whatsappsummary.viewmodel.AppNotificationStats>) {
        if (_binding == null) return
        if (appStats.isEmpty()) {
            binding.pieChart.clear(); return
        }

        val entries = appStats.map { stat ->
            PieEntry(stat.notificationCount.toFloat(), stat.appName ?: stat.packageName)
        }
        val totalCount = entries.sumOf { it.value.toInt() }
        currentPieTotal = totalCount

        val dataSet = PieDataSet(entries, "").apply {
            colors = pastelChartColors
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.colorOnPrimaryContainer)
            sliceSpace = 2f
            valueFormatter = object : IValueFormatter {
                override fun getFormattedValue(value: Float, entry: com.github.mikephil.charting.data.Entry?, dataSetIndex: Int, viewPortHandler: ViewPortHandler?): String {
                    val percentage = (value / totalCount * 100).toInt()
                    val formattedValue = formatLargeNumber(value)
                    return "$formattedValue ($percentage%)"
                }
            }
        }

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.setUsePercentValues(false)
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    private fun setupLineChart(dailyData: List<Triple<String, Long, Int>>) {
        currentDailyData = dailyData
        if (_binding == null) return
        if (dailyData.isEmpty()) {
            binding.lineChart.clear(); return
        }

        val entries = dailyData.mapIndexed { index, (_, _, count) ->
            Entry(index.toFloat(), count.toFloat())
        }

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val primaryDeep = ContextCompat.getColor(requireContext(), R.color.colorPrimaryVariant)
        val accentColor = ContextCompat.getColor(requireContext(), R.color.colorSecondary)

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
                    val percentageText = if (index > 0 && index < entries.size) {
                        val previousValue = entries[index - 1].y.toInt()
                        if (previousValue > 0) {
                            val change = currentValue - previousValue
                            val percentage = (change.toFloat() / previousValue.toFloat() * 100).toInt()
                            when {
                                percentage > 0 -> " ▲+${percentage}%"
                                percentage < 0 -> " ▼${percentage}%"
                                else -> " ▬0%"
                            }
                        } else " ▲+${currentValue}%"
                    } else ""
                    return "${formatLargeNumber(value)}$percentageText"
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            highLightColor = accentColor
            highlightLineWidth = 1.5f
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
        }

        val data = LineData(dataSet)
        binding.lineChart.apply {
            this.data = data
            xAxis.valueFormatter = IndexAxisValueFormatter(dailyData.map { it.first })
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {}
                override fun onNothingSelected() {}
            })
            animateXY(1200, 1200)
        }
    }

    private fun setupObservers() {
        viewModel.totalNotifications.observe(viewLifecycleOwner) { count ->
            binding.textTotalNotifications.text = formatLargeNumber(count.toFloat())
        }
        viewModel.totalApps.observe(viewLifecycleOwner) { count ->
            binding.textTotalApps.text = count.toString()
        }
        viewModel.todayNotifications.observe(viewLifecycleOwner) { count ->
            binding.textTodayNotifications.text = formatLargeNumber(count.toFloat())
        }
        viewModel.notificationsByApp.observe(viewLifecycleOwner) { appStats ->
            val limitedStats = if (selectedTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedTopAppsLimit)
            adapter.submitList(limitedStats)

            appNameToPackageMap.clear()
            packageToAppNameMap.clear()
            appStats.forEach { stat ->
                val displayName = stat.appName ?: stat.packageName
                appNameToPackageMap[displayName] = stat.packageName
                packageToAppNameMap[stat.packageName] = displayName
            }
            availableAppNames = appStats.map { it.appName ?: it.packageName }
            updateAppSpinner(availableAppNames)
            updatePieAppSpinner(availableAppNames)
        }
        viewModel.pieChartData.observe(viewLifecycleOwner) { appStats ->
            val limitedStats = if (selectedPieTopAppsLimit == Int.MAX_VALUE) appStats else appStats.take(selectedPieTopAppsLimit)
            setupPieChart(limitedStats)
            updatePieAppSpinner(availableAppNames)
        }
        viewModel.lineChartData.observe(viewLifecycleOwner) { dailyData ->
            setupLineChart(dailyData)
        }
    }

    private fun setupButtons() {
        binding.buttonRefresh.setOnClickListener {
            viewModel.loadDashboardData()
            viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
            viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
            Toast.makeText(requireContext(), "Dashboard actualizado", Toast.LENGTH_SHORT).show()
        }

        binding.buttonLineDateRange.setOnClickListener { showDateRangePicker(false) }
    }

    private fun showAppOptionsDialog(appStats: com.example.whatsappsummary.viewmodel.AppNotificationStats) {
        val dialog = AppOptionsDialog(
            context = requireContext(),
            appStats = appStats,
            onDelete = {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.deleteAppNotifications(appStats.packageName)
                        viewModel.loadDashboardData()
                        viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
                        Toast.makeText(requireContext(), "Aplicación y sus notificaciones eliminadas", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onIgnoreChanged = { _ -> adapter.notifyDataSetChanged() }
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

        DatePickerDialog(requireContext(), { _, year, month, day ->
            val startCal = Calendar.getInstance()
            startCal.set(year, month, day, 0, 0, 0)
            startCal.set(Calendar.MILLISECOND, 0)
            val selectedStart = startCal.timeInMillis

            cal.timeInMillis = currentEnd
            val endYear = cal.get(Calendar.YEAR)
            val endMonth = cal.get(Calendar.MONTH)
            val endDay = cal.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, eYear, eMonth, eDay ->
                val endCal = Calendar.getInstance()
                endCal.set(eYear, eMonth, eDay, 23, 59, 59)
                endCal.set(Calendar.MILLISECOND, 999)
                val selectedEnd = endCal.timeInMillis

                if (selectedStart > selectedEnd) {
                    Toast.makeText(requireContext(), "La fecha de inicio no puede ser posterior a la fecha de fin", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                if (isPie) {
                    selectedStartPie = selectedStart; selectedEndPie = selectedEnd
                    viewModel.loadPieChartData(selectedStartPie, selectedEndPie, selectedPieApp)
                } else {
                    selectedStartLine = selectedStart; selectedEndLine = selectedEnd
                    viewModel.loadLineChartData(selectedPackage, selectedStartLine, selectedEndLine, selectedGranularity)
                }
            }, endYear, endMonth, endDay).show()
        }, startYear, startMonth, startDay).show()
    }
}
