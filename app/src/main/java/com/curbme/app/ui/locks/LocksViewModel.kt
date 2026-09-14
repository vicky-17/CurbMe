package com.curbme.app.ui.locks

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curbme.app.core.utils.AppIconManager
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.dao.AppBlockDao
import com.curbme.app.data.local.db.entity.AppBlockRule
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isSocial: Boolean = false,
    val isBlocked: Boolean = false,
    val assignedPlanName: String? = null
)

// Represents the state of our 6-step wizard
data class WizardState(
    val currentStep: Int = 1,
    val planType: String = "STAY_FOCUSED",
    val selectedPackages: Set<String> = emptySet(),
    val blockMethod: String = "INTERSTITIAL",
    val timingMode: String = "ON_DEMAND",
    val selectedDays: Set<Int> = emptySet(), // 1 for Monday, 7 for Sunday
    val multiDayCount: Int = 7,
    val pomodoroFocus: Int = 25,
    val pomodoroShortBreak: Int = 5,
    val pomodoroLongBreak: Int = 15,
    val pomodoroCycles: Int = 4,
    val enforcedEndDate: Long? = null,
    val planName: String = "",
    // Step 1 Details
    val allowedMinutes: Int = 30,
    val intervalMinutes: Int = 60,
    // Habit Details
    val maxLaunches: Int = 5,
    val maxTimePerLaunch: Int = 0,
    // Step 5 Details
    val stopChallenge: String = "NONE",
    val isAntiUninstall: Boolean = false
)

class LocksViewModel(
    private val context: Context,
    private val prefs: PrefsManager,
    private val appBlockDao: AppBlockDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isAppsLoading = MutableStateFlow(true)
    val isAppsLoading = _isAppsLoading.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())

    // Active rules from Database
    val activeRules = appBlockDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps for selection dialog
    val selectableApps = combine(_installedApps, _searchQuery, activeRules) { apps, query, rules ->
        val ruleMap = rules.associateBy { it.packageName }
        apps.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }.map { app ->
            val rule = ruleMap[app.packageName]
            app.copy(assignedPlanName = rule?.planName)
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Wizard State
    private val _wizardState = MutableStateFlow(WizardState())
    val wizardState = _wizardState.asStateFlow()

    private val dataStoreManager = DataStoreManager(context)

    // Website states
    val blockedWebsites: StateFlow<Set<String>> = dataStoreManager.settings
        .map { it.blockedWebsites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val db = AppDatabase.getDatabase(context)
    val visitedWebsites: StateFlow<List<WebsiteStatsEntity>> =
        db.websiteStatsDao().getForDateFlow(TimeUtils.todayKey())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAppsLoading.value = true
            val startTime = System.currentTimeMillis()
            
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val socialPackages = setOf(
                "com.facebook.katana", "com.instagram.android", "com.twitter.android", 
                "com.zhiliaoapp.musically", "com.whatsapp", "com.snapchat.android",
                "com.reddit.frontpage", "com.linkedin.android", "org.telegram.messenger",
                "com.google.android.youtube", "com.pinterest"
            )

            val appItems = packages.mapNotNull { appInfo ->
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent == null) return@mapNotNull null

                AppItem(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isSocial = socialPackages.contains(appInfo.packageName)
                )
            }
            _installedApps.value = appItems
            
            // Ensure at least 2 seconds of loading time for UX
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2000) {
                kotlinx.coroutines.delay(2000 - elapsedTime)
            }
            _isAppsLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // --- Wizard Actions ---

    fun nextStep() {
        _wizardState.update { state ->
            val next = state.currentStep + 1
            if (next == 5 && state.planName.isBlank()) {
                val nextNum = calculateNextPlanNumber()
                state.copy(currentStep = next, planName = "Plan $nextNum")
            } else {
                state.copy(currentStep = next)
            }
        }
    }

    private fun calculateNextPlanNumber(): Int {
        val uniquePlanNames = activeRules.value.map { it.planName }.distinct()
        return uniquePlanNames.size + 1
    }

    fun previousStep() {
        if (_wizardState.value.currentStep > 1) {
            _wizardState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun updateWizard(update: (WizardState) -> WizardState) {
        _wizardState.update(update)
    }

    fun toggleAppSelection(packageName: String) {
        _wizardState.update { state ->
            // Check if app is already assigned to another plan (different from the current one if we're editing, 
            // though the wizard currently defaults to new plans)
            val assignedRule = activeRules.value.find { it.packageName == packageName }
            if (assignedRule != null && assignedRule.planName != state.planName) {
                return@update state
            }

            val current = state.selectedPackages.toMutableSet()
            if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
            state.copy(selectedPackages = current)
        }
    }

    fun resetWizard() {
        _wizardState.value = WizardState()
    }

    fun clearCurrentStep() {
        _wizardState.update { state ->
            when (state.currentStep) {
                1 -> state.copy(planType = "STAY_FOCUSED")
                2 -> state.copy(selectedPackages = emptySet(), blockMethod = "SUSPEND")
                3 -> state.copy(
                    timingMode = "ON_DEMAND", 
                    selectedDays = emptySet(), 
                    multiDayCount = 7,
                    pomodoroFocus = 25,
                    pomodoroShortBreak = 5,
                    pomodoroLongBreak = 15,
                    pomodoroCycles = 4,
                    enforcedEndDate = null
                )
                4 -> state.copy(allowedMinutes = 0, maxLaunches = 0, intervalMinutes = 60)
                5 -> state.copy(stopChallenge = "NONE", isAntiUninstall = false)
                6 -> state.copy(planName = "")
                else -> state
            }
        }
    }

    fun toggleDaySelection(day: Int) {
        _wizardState.update { state ->
            val current = state.selectedDays.toMutableSet()
            if (current.contains(day)) current.remove(day) else current.add(day)
            state.copy(selectedDays = current)
        }
    }

    fun saveWizardPlan() {
        val state = _wizardState.value
        viewModelScope.launch(Dispatchers.IO) {
            val rules = state.selectedPackages.map { pkg ->
                val app = _installedApps.value.find { it.packageName == pkg }
                val iconPath = app?.icon?.let { drawable ->
                    AppIconManager.saveAppIcon(context, pkg, drawable)
                }

                val activeDaysMask = if (state.timingMode == "WEEKLY") {
                    var mask = 0
                    state.selectedDays.forEach { mask = mask or (1 shl (it - 1)) }
                    mask
                } else 127 // Default all days

                val expiry = if (state.stopChallenge == "ENFORCED" && state.enforcedEndDate != null) {
                    state.enforcedEndDate
                } else if (state.timingMode == "MULTI_DAY") {
                    System.currentTimeMillis() + (state.multiDayCount * 24 * 60 * 60 * 1000L)
                } else 0L

                AppBlockRule(
                    packageName = pkg,
                    appName = app?.name ?: "Unknown App",
                    iconPath = iconPath,
                    planType = state.planType,
                    allowedMinutes = state.allowedMinutes,
                    intervalMinutes = state.intervalMinutes,
                    maxLaunches = state.maxLaunches,
                    maxTimePerLaunch = state.maxTimePerLaunch,
                    blockMethod = state.blockMethod,
                    useInterstitial = state.blockMethod == "INTERSTITIAL",
                    timingMode = state.timingMode,
                    activeDays = activeDaysMask,
                    expiryTimestamp = expiry,
                    planName = state.planName,
                    stopChallengeType = state.stopChallenge,
                    isAntiUninstallEnabled = state.isAntiUninstall
                )
            }
            appBlockDao.insertRules(rules)
            // Reset wizard after saving
            _wizardState.value = WizardState()
        }
    }

    fun removeRule(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = appBlockDao.getRuleForPackage(packageName)
            if (rule != null && rule.stopChallengeType == "ENFORCED" && rule.expiryTimestamp > System.currentTimeMillis()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Cannot delete enforced rule until it expires", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            AppIconManager.deleteSavedIcon(context, packageName)
            appBlockDao.deleteRuleByPackage(packageName)
        }
    }

    fun removePlan(planName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = activeRules.value.filter { it.planName == planName }
            val isEnforced = rules.any { it.stopChallengeType == "ENFORCED" && it.expiryTimestamp > System.currentTimeMillis() }
            
            if (isEnforced) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "This plan is enforced and cannot be deleted yet", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            rules.forEach { rule ->
                AppIconManager.deleteSavedIcon(context, rule.packageName)
            }
            appBlockDao.deleteRulesByPlanName(planName)
        }
    }

    // --- Website Actions ---

    fun addWebsite(domain: String) {
        val cleaned = domain.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
        if (cleaned.isNotEmpty()) {
            viewModelScope.launch {
                dataStoreManager.updateSettings { current ->
                    val updated = current.blockedWebsites.toMutableSet()
                    updated.add(cleaned)
                    current.copy(blockedWebsites = updated)
                }
            }
        }
    }

    fun removeWebsite(domain: String) {
        viewModelScope.launch {
            dataStoreManager.updateSettings { current ->
                val updated = current.blockedWebsites.toMutableSet()
                updated.remove(domain)
                current.copy(blockedWebsites = updated)
            }
        }
    }

    fun lockVisitedWebsite(domain: String) {
        addWebsite(domain)
        Toast.makeText(context, "Locked $domain", Toast.LENGTH_SHORT).show()
    }
}