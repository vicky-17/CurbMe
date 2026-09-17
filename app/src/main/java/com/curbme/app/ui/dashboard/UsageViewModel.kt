package com.curbme.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.curbme.app.core.utils.UsageStatsHelper
import com.curbme.app.data.models.AppUsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DayUsageData(
    val label: String,
    val valueHours: Float,
    val date: Calendar
)

data class WeekCacheEntry(
    val weeklyData: List<DayUsageData>,
    val dailyStats: List<List<AppUsageInfo>>,
    val sortedWeeklyStats: List<AppUsageInfo>,
    val dateRangeLabel: String
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val usageStatsHelper = UsageStatsHelper(application)
    private val weekCache = mutableMapOf<Int, WeekCacheEntry>()

    private val _usageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val usageStats = _usageStats.asStateFlow()

    // Fixed "Today" stats specifically for the Dashboard
    private val _todayStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val todayStats = _todayStats.asStateFlow()

    private val _weeklyTotalStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val weeklyTotalStats = _weeklyTotalStats.asStateFlow()

    private val _weeklyDateRangeLabel = MutableStateFlow("")
    val weeklyDateRangeLabel = _weeklyDateRangeLabel.asStateFlow()

    private val _weeklyTotalTimeMs = MutableStateFlow(0L)
    val weeklyTotalTimeMs = _weeklyTotalTimeMs.asStateFlow()

    private val _comparisonPercent = MutableStateFlow(0)
    val comparisonPercent = _comparisonPercent.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(usageStatsHelper.hasUsageStatsPermission())
    val isPermissionGranted = _isPermissionGranted.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _weeklyData = MutableStateFlow<List<DayUsageData>>(emptyList())
    val weeklyData = _weeklyData.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(calculateTodayIndex()) 
    val selectedDayIndex = _selectedDayIndex.asStateFlow()
    
    private val _weekOffset = MutableStateFlow(0)
    val weekOffset = _weekOffset.asStateFlow()

    init {
        refreshStats()
        startAutoRefresh()
    }

    fun refreshStats() {
        if (usageStatsHelper.hasUsageStatsPermission()) {
            _isPermissionGranted.value = true
            weekCache.clear()
            loadTodayOnly()

            // Update selected index if we are on the current week so the chart follows the day rollover
            if (_weekOffset.value == 0) {
                _selectedDayIndex.value = calculateTodayIndex()
            }

            loadWeekData()
        } else {
            _isPermissionGranted.value = false
        }
    }

    private fun loadTodayOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val stats = usageStatsHelper.getTodayUsageStats()
                _todayStats.value = stats
                
                // Calculate comparison
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val yesterdayStats = usageStatsHelper.getUsageStatsForDay(yesterday)
                val todayTotal = stats.sumOf { it.usageTimeMs }
                val yesterdayTotal = yesterdayStats.sumOf { it.usageTimeMs }
                
                if (yesterdayTotal > 0) {
                    val diff = todayTotal - yesterdayTotal
                    val percent = (diff.toDouble() / yesterdayTotal * 100).toInt()
                    _comparisonPercent.value = percent
                } else {
                    _comparisonPercent.value = 0
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index
        loadSelectedDayStats()
    }

    fun changeWeek(offsetDelta: Int) {
        _weekOffset.value += offsetDelta
        loadWeekData()
    }

    private fun loadWeekData() {
        val currentOffset = _weekOffset.value
        val cached = weekCache[currentOffset]
        if (cached != null) {
            applyCachedWeek(cached)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val weekData = mutableListOf<DayUsageData>()
                val dailyStatsList = mutableListOf<List<AppUsageInfo>>()
                val calendar = Calendar.getInstance()
                
                // Robust Monday calculation
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                calendar.add(Calendar.WEEK_OF_YEAR, currentOffset)
                
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                val allWeekAppMap = mutableMapOf<String, AppUsageInfo>()
                
                for (i in 0..6) {
                    val date = calendar.clone() as Calendar
                    val stats = usageStatsHelper.getUsageStatsForDay(date)
                    dailyStatsList.add(stats)
                    val totalMs = stats.sumOf { it.usageTimeMs }
                    weekData.add(DayUsageData(labels[i], totalMs / 3600000f, date))

                    for (app in stats) {
                        val existing = allWeekAppMap[app.packageName]
                        if (existing == null) {
                            allWeekAppMap[app.packageName] = app.copy()
                        } else {
                            allWeekAppMap[app.packageName] = existing.copy(
                                usageTimeMs = existing.usageTimeMs + app.usageTimeMs,
                                launchCount = existing.launchCount + app.launchCount
                            )
                        }
                    }

                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                var rangeLabel = ""
                val firstDay = weekData.firstOrNull()?.date
                val lastDay = weekData.lastOrNull()?.date
                if (firstDay != null && lastDay != null) {
                    val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
                    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                    val startStr = sdf.format(firstDay.time)
                    val endStr = sdf.format(lastDay.time)
                    val yearStr = yearFormat.format(lastDay.time)
                    rangeLabel = "$startStr – $endStr, $yearStr"
                }

                val sortedWeeklyStats = allWeekAppMap.values
                    .filter { it.usageTimeMs > 0 }
                    .sortedByDescending { it.usageTimeMs }

                val entry = WeekCacheEntry(
                    weeklyData = weekData,
                    dailyStats = dailyStatsList,
                    sortedWeeklyStats = sortedWeeklyStats,
                    dateRangeLabel = rangeLabel
                )
                weekCache[currentOffset] = entry

                if (_weekOffset.value == currentOffset) {
                    applyCachedWeek(entry)
                }

                if (!weekCache.containsKey(currentOffset - 1)) {
                    prefetchWeek(currentOffset - 1)
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun applyCachedWeek(cached: WeekCacheEntry) {
        _weeklyData.value = cached.weeklyData
        _weeklyDateRangeLabel.value = cached.dateRangeLabel
        _weeklyTotalStats.value = cached.sortedWeeklyStats
        _weeklyTotalTimeMs.value = cached.sortedWeeklyStats.sumOf { it.usageTimeMs }

        val idx = _selectedDayIndex.value
        if (cached.dailyStats.isNotEmpty() && idx in cached.dailyStats.indices) {
            _usageStats.value = cached.dailyStats[idx]
        } else {
            _usageStats.value = emptyList()
        }
        _isLoading.value = false
    }

    private fun prefetchWeek(offset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val weekData = mutableListOf<DayUsageData>()
                val dailyStatsList = mutableListOf<List<AppUsageInfo>>()
                val calendar = Calendar.getInstance()
                
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                calendar.add(Calendar.WEEK_OF_YEAR, offset)
                
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                val allWeekAppMap = mutableMapOf<String, AppUsageInfo>()
                
                for (i in 0..6) {
                    val date = calendar.clone() as Calendar
                    val stats = usageStatsHelper.getUsageStatsForDay(date)
                    dailyStatsList.add(stats)
                    val totalMs = stats.sumOf { it.usageTimeMs }
                    weekData.add(DayUsageData(labels[i], totalMs / 3600000f, date))

                    for (app in stats) {
                        val existing = allWeekAppMap[app.packageName]
                        if (existing == null) {
                            allWeekAppMap[app.packageName] = app.copy()
                        } else {
                            allWeekAppMap[app.packageName] = existing.copy(
                                usageTimeMs = existing.usageTimeMs + app.usageTimeMs,
                                launchCount = existing.launchCount + app.launchCount
                            )
                        }
                    }

                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }

                var rangeLabel = ""
                val firstDay = weekData.firstOrNull()?.date
                val lastDay = weekData.lastOrNull()?.date
                if (firstDay != null && lastDay != null) {
                    val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
                    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                    val startStr = sdf.format(firstDay.time)
                    val endStr = sdf.format(lastDay.time)
                    val yearStr = yearFormat.format(lastDay.time)
                    rangeLabel = "$startStr – $endStr, $yearStr"
                }

                val sortedWeeklyStats = allWeekAppMap.values
                    .filter { it.usageTimeMs > 0 }
                    .sortedByDescending { it.usageTimeMs }

                weekCache[offset] = WeekCacheEntry(
                    weeklyData = weekData,
                    dailyStats = dailyStatsList,
                    sortedWeeklyStats = sortedWeeklyStats,
                    dateRangeLabel = rangeLabel
                )
            } catch (_: Exception) {}
        }
    }

    private fun loadSelectedDayStats() {
        val currentOffset = _weekOffset.value
        val cached = weekCache[currentOffset]
        val idx = _selectedDayIndex.value

        if (cached != null && idx in cached.dailyStats.indices) {
            _usageStats.value = cached.dailyStats[idx]
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val week = _weeklyData.value
                if (week.isNotEmpty() && idx in week.indices) {
                    val selectedDate = week[idx].date
                    val stats = usageStatsHelper.getUsageStatsForDay(selectedDate)
                    _usageStats.value = stats
                }
            } catch (_: Exception) {
                // Ignore
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calculateTodayIndex(): Int {
        val calendar = Calendar.getInstance()
        // Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6
        val day = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        return if (day == Calendar.SUNDAY) 6 else day - 2
    }

    private fun startAutoRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(300000) // Refresh every 5 minutes
                if (usageStatsHelper.hasUsageStatsPermission()) {
                    // refreshStats() updates todayStats, weekly labels, and handles day rollover
                    refreshStats()
                }
            }
        }
    }
}
