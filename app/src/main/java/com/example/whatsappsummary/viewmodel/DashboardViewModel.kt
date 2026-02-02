package com.example.whatsappsummary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.whatsappsummary.data.AppDatabase
import com.example.whatsappsummary.data.entity.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    private val _totalNotifications = MutableLiveData<Int>()
    val totalNotifications: LiveData<Int> = _totalNotifications

    private val _totalApps = MutableLiveData<Int>()
    val totalApps: LiveData<Int> = _totalApps

    private val _notificationsByApp = MutableLiveData<List<AppNotificationStats>>()
    val notificationsByApp: LiveData<List<AppNotificationStats>> = _notificationsByApp

    private val _todayNotifications = MutableLiveData<Int>()
    val todayNotifications: LiveData<Int> = _todayNotifications

    private val _lineChartData = MutableLiveData<List<Pair<String, Int>>>()
    val lineChartData: LiveData<List<Pair<String, Int>>> = _lineChartData

    private val _pieChartData = MutableLiveData<List<AppNotificationStats>>()
    val pieChartData: LiveData<List<AppNotificationStats>> = _pieChartData

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Total de notificaciones
                val total = database.notificationDao().getTotalNotifications()
                _totalNotifications.postValue(total)

                // Total de aplicaciones
                val apps = database.notificationDao().getUniqueApps().size
                _totalApps.postValue(apps)

                // Notificaciones por aplicación (procesamiento en Kotlin)
                try {
                    val allNotifications = database.notificationDao().getNotificationsByRange(0, System.currentTimeMillis())
                    val appStats = allNotifications
                        .groupBy { it.packageName }
                        .map { (packageName, notifications) ->
                            // Obtener nombre real de la aplicación usando PackageManager
                            val appName = try {
                                val applicationInfo = getApplication<Application>().packageManager.getApplicationInfo(packageName, 0)
                                getApplication<Application>().packageManager.getApplicationLabel(applicationInfo).toString()
                            } catch (e: Exception) {
                                packageName // Fallback al packageName si no se puede obtener el nombre
                            }
                            
                            AppNotificationStats(
                                packageName = packageName,
                                appName = appName,
                                notificationCount = notifications.size,
                                lastNotificationTime = notifications.maxOfOrNull { it.timestamp } ?: 0L
                            )
                        }
                        .sortedByDescending { it.notificationCount }
                    _notificationsByApp.postValue(appStats)
                } catch (e: Exception) {
                    // Fallback: lista vacía si hay error
                    _notificationsByApp.postValue(emptyList())
                }

                // Notificaciones de hoy
                val todayStart = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val today = database.notificationDao().getNotificationsSince(todayStart).size
                _todayNotifications.postValue(today)
            }
        }
    }

    fun loadLineChartData(packageName: String, daysRange: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val endTime = System.currentTimeMillis()
                    val startTime = endTime - (daysRange * 24 * 60 * 60 * 1000L)
                    
                    val notifications = if (packageName == "all") {
                        database.notificationDao().getNotificationsByRange(startTime, endTime)
                    } else {
                        database.notificationDao().getNotificationsByPackageAndRange(packageName, startTime, endTime)
                    }
                    
                    // Agrupar por día
                    val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                    val dailyCounts = notifications
                        .groupBy { notification ->
                            val date = Date(notification.timestamp)
                            dateFormat.format(date)
                        }
                        .map { (date, notifs) -> date to notifs.size }
                        .sortedBy { it.first }
                    
                    _lineChartData.postValue(dailyCounts)
                } catch (e: Exception) {
                    _lineChartData.postValue(emptyList())
                }
            }
        }
    }

    fun loadPieChartData(daysRange: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val endTime = System.currentTimeMillis()
                    val startTime = endTime - (daysRange * 24 * 60 * 60 * 1000L)
                    
                    val notifications = database.notificationDao().getNotificationsByRange(startTime, endTime)
                    val appStats = notifications
                        .groupBy { it.packageName }
                        .map { (packageName, notifs) ->
                            val appName = try {
                                val applicationInfo = getApplication<Application>().packageManager.getApplicationInfo(packageName, 0)
                                getApplication<Application>().packageManager.getApplicationLabel(applicationInfo).toString()
                            } catch (e: Exception) {
                                packageName
                            }
                            
                            AppNotificationStats(
                                packageName = packageName,
                                appName = appName,
                                notificationCount = notifs.size,
                                lastNotificationTime = notifs.maxOfOrNull { it.timestamp } ?: 0L
                            )
                        }
                        .sortedByDescending { it.notificationCount }
                    
                    _pieChartData.postValue(appStats)
                } catch (e: Exception) {
                    _pieChartData.postValue(emptyList())
                }
            }
        }
    }
}

data class AppNotificationStats(
    val packageName: String,
    val appName: String?,
    val notificationCount: Int,
    val lastNotificationTime: Long
)