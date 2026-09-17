package com.curbme.app.service.monitor

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.AppBlockRule
import kotlinx.coroutines.*

import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.core.utils.PermissionHelper
import android.widget.Toast
import com.curbme.app.ui.block.AppBlockOverlayManager
import java.util.*

class AppBlockEngineService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var db: AppDatabase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var overlayManager: AppBlockOverlayManager
    private var settings = Settings()

    private var lastForegroundApp: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        db = AppDatabase.getDatabase(this)
        dataStoreManager = DataStoreManager(this)
        overlayManager = AppBlockOverlayManager(this, dataStoreManager)
        
        // Initialize usage tracker
        val tracker = AppUsageTracker()
        tracker.setup(this)
        
        serviceScope.launch {
            dataStoreManager.settings.collect {
                settings = it
            }
        }
        
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val notification = com.curbme.app.service.notification.NotificationHelper.buildGuardianForegroundNotification(this)
        startForeground(com.curbme.app.core.utils.Constants.NOTIFICATION_ID_GUARDIAN, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val currentApp = getForegroundPackage()
                    android.util.Log.d("AppBlockEngine", "Detected foreground app: $currentApp")
                    
                    // Hub & Spoke: Update the central state immediately.
                    // This will trigger System B (Usage Tracker / Notifications) automatically.
                    MonitorState.updateForegroundApp(currentApp)

                    if (currentApp != null && !IGNORED_PACKAGES.contains(currentApp) && currentApp != packageName && currentApp != lastForegroundApp) {
                        android.util.Log.d("AppBlockEngine", "App switched to: $currentApp")
                        lastForegroundApp = currentApp
                        
                        // 1. Instantly check if we should block (The Enforcer)
                        checkForegroundApp(currentApp)
                    } else if (currentApp == null || IGNORED_PACKAGES.contains(currentApp)) {
                        if (lastForegroundApp != null) {
                            lastForegroundApp = null
                        }
                    }

                    // 2. Regular background check for time limits/countdowns
                    checkForegroundApp(currentApp)

                } catch (e: Exception) {
                    android.util.Log.e("AppBlockEngine", "Error in logic loop", e)
                }
                delay(300) 
            }
        }
    }

    private suspend fun checkForegroundApp(forcedPackage: String? = null) {
        val currentApp = forcedPackage ?: getForegroundPackage() ?: return

        // 1. If overlay is already showing, keep it until it's explicitly dismissed via its own button
        if (AppBlockOverlayManager.isOverlayShowing) {
            return
        }

        // 2. Skip our own app
        if (currentApp == packageName) {
            overlayManager.hide()
            return
        }

        // 2. Handle Banking Mode Timeout & Enforcement
        if (settings.isBankingBypassEnabled) {
            val startTime = settings.bankingBypassStartTime
            val now = System.currentTimeMillis()
            if (now - startTime > 10 * 60 * 1000L) {
                // Auto-disable timeout
                dataStoreManager.setBankingBypassEnabled(false)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppBlockEngineService, "Banking Mode expired.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Strict Enforcement: If accessibility is OFF, only allowed apps
                if (!PermissionHelper.isAccessibilityEnabled(this)) {
                    val allowedPackages = listOf(
                        packageName, 
                        settings.bankingBypassPackage, 
                        PermissionHelper.getLauncherPackageName(this)
                    )
                    
                    if (!allowedPackages.contains(currentApp)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AppBlockEngineService, 
                                "Only ${settings.bankingBypassPackage} is allowed during Banking Mode.", 
                                Toast.LENGTH_LONG).show()
                        }
                        returnToHome()
                        return
                    }
                }
            }
        }

        // 3. Check for temporary bypass
        val bypassExpiry = settings.appBypassMap[currentApp]
        if (bypassExpiry != null && System.currentTimeMillis() < bypassExpiry) {
            overlayManager.hide()
            return
        }

        // 4. Check if there is a rule for this app
        val rule = db.appBlockDao().getRuleForPackage(currentApp)
        
        if (rule != null && shouldBlock(rule)) {
            android.util.Log.d("AppBlockEngine", "Blocking $currentApp (${rule.planType})")
            applyBlock(rule)
        } else {
            overlayManager.hide()
        }

        lastForegroundApp = currentApp
    }

    private fun getForegroundPackage(): String? {
        val endTime = System.currentTimeMillis()

        // We progressively look back further if we don't find anything in the initial window.
        // This handles cases where the user stays on one screen for a long time.
        val lookbackWindows = listOf(
            5_000L,       // 5 seconds (fastest, covers active switching)
            60_000L,      // 1 minute
            300_000L,     // 5 minutes
            3_600_000L,   // 1 hour
            86_400_000L   // 24 hours (safety fallback)
        )

        for (window in lookbackWindows) {
            val startTime = endTime - window
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            var latestPackage: String? = null
            var latestTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // Filter for ACTIVITY_RESUMED to find the most recent app opened
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }

            // If we found a package in this window, return it immediately.
            // Otherwise, the loop continues to the next (larger) window.
            if (latestPackage != null) {
                return latestPackage
            }
        }

        return null
    }

    private fun shouldBlock(rule: AppBlockRule): Boolean {
        // 1. Check Timing Mode
        when (rule.timingMode) {
            "WEEKLY" -> {
                val calendar = Calendar.getInstance()
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val adjustedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                val isDayActive = (rule.activeDays and (1 shl (adjustedDay - 1))) != 0
                if (!isDayActive) return false
            }
            "MULTI_DAY" -> {
                if (rule.expiryTimestamp > 0 && System.currentTimeMillis() > rule.expiryTimestamp) {
                    return false
                }
            }
        }

        // 2. Check Plan Type
        return when (rule.planType) {
            "STAY_FOCUSED" -> true
            "TIME_LIMIT" -> true
            "HABIT_TRAINING" -> true
            "SCREEN_BREAK" -> true
            else -> false
        }
    }

    private fun applyBlock(rule: AppBlockRule) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(this, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)
        val isDO = dpm.isDeviceOwnerApp(packageName)

        val reason = when (rule.planType) {
            "STAY_FOCUSED" -> "Stay Focused mode is active"
            "TIME_LIMIT" -> "Daily time limit reached"
            "HABIT_TRAINING" -> "Max launches reached"
            "SCREEN_BREAK" -> "Time for a screen break"
            else -> "App is restricted by plan"
        }

        when (rule.blockMethod) {
            "SUSPEND" -> {
                if (isDO) {
                    try {
                        dpm.setPackagesSuspended(adminComponent, arrayOf(rule.packageName), true)
                    } catch (e: Exception) {
                        showFullBlock(rule.appName, rule.packageName, reason, rule.planType)
                    }
                } else {
                    showFullBlock(rule.appName, rule.packageName, reason, rule.planType)
                }
            }
            "HIDE" -> {
                if (isDO) {
                    try {
                        dpm.setApplicationHidden(adminComponent, rule.packageName, true)
                    } catch (e: Exception) {
                        showFullBlock(rule.appName, rule.packageName, reason, rule.planType)
                    }
                } else {
                    showFullBlock(rule.appName, rule.packageName, reason, rule.planType)
                }
            }
            else -> showFullBlock(rule.appName, rule.packageName, reason, rule.planType)
        }
    }

    private fun showFullBlock(appName: String, packageName: String, reason: String, planType: String) {
        overlayManager.show(appName, packageName, reason, planType)
    }

    private fun returnToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        overlayManager.hide()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "AppBlockEngine"

        @Volatile
        var instance: AppBlockEngineService? = null
            private set

        private val IGNORED_PACKAGES = setOf("android", "com.android.systemui", "com.miui.systemui.plugin")

        fun onAppSwitched(packageName: String) {
            val svc = instance ?: return
            svc.serviceScope.launch {
                MonitorState.updateForegroundApp(packageName)
                svc.checkForegroundApp(packageName)
            }
        }
    }
}
