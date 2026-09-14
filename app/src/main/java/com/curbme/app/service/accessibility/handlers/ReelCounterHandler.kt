package com.curbme.app.service.accessibility.handlers

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.curbme.app.core.utils.TimeUtils
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.ReelStatsEntity
import com.curbme.app.data.local.db.entity.ReelUsageStatsEntity
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.ReelProgressionAnalyzer
import com.curbme.app.service.accessibility.detectors.ReelTextExtractor
import com.curbme.app.service.accessibility.detectors.ShortsDetector
import com.curbme.app.ui.overlay.ReelCounterOverlayManager
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Lightweight, rate-limited handler for short video / reel scroll and watch time detection.
 * Operates purely on accessibility events and a continuous ticker for accurate time tracking.
 */
class ReelCounterHandler(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val reelStatsDao = db.reelStatsDao()
    private val reelUsageStatsDao = db.reelUsageStatsDao()
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyzer = ReelProgressionAnalyzer()
    private val overlayManager = ReelCounterOverlayManager(context)

    private var todayCount = 0
    private var totalTodayReelTimeMs = 0L
    private var lastDateStr = TimeUtils.todayKey()
    private var activePackage: String? = null

    private var hideJob: Job? = null
    private var lastShortsDetectedTime = 0L
    private var lastEventProcessedTime = 0L
    private var isShortsActive = false

    private var timeTrackingJob: Job? = null
    private var shortsStartTimeElapsed = 0L
    private var activeShortsPackage: String? = null

    private var currentReelStartTimeElapsed = 0L
    private var currentReelComparator: String? = null

    companion object {
        private const val TAG = "ReelCounter"
        // Grace period (5s) before hiding overlay when user explicitly navigates to a non-Shorts tab
        private const val HIDE_DEBOUNCE_MS = 5000L
        // Rate limiting: Process at most 1 event every 200ms to protect CPU while capturing fast swipes
        private const val MIN_EVENT_INTERVAL_MS = 200L
    }

    init {
        scope.launch {
            loadTodayCount()
        }
    }

    fun handleEvent(rootNode: AccessibilityNodeInfo?, packageName: String?, settings: Settings? = null) {
        if (packageName == null) {
            onLeftShortsApp()
            return
        }

        val isTargetApp = ShortsDetector.TARGET_SHORT_VIDEO_PACKAGES.contains(packageName)

        if (!isTargetApp) {
            onLeftShortsApp(packageName)
            return
        }

        // Rate-limiting check: skip rapid-fire content change events (max 1 check per 200ms)
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventProcessedTime < MIN_EVENT_INTERVAL_MS) {
            return
        }
        lastEventProcessedTime = now

        // Check if overlay is enabled in BOTH PrefsManager AND DataStore settings
        val isOverlayEnabledInPrefs = PrefsManager(context).isReelCounterOverlayOn
        val isOverlayEnabledInSettings = settings?.reelPlanConfig?.isDisplayReelCounterOverlay ?: true
        val isOverlayEnabled = isOverlayEnabledInPrefs && isOverlayEnabledInSettings

        if (!isOverlayEnabled) {
            onLeftShortsApp(packageName)
            return
        }

        activePackage = packageName

        // Transient null rootNode during layout updates should NOT trigger a hide schedule
        if (rootNode == null) return

        // Extract dynamic comparator text to verify if user is ACTUALLY on a Reel/Shorts video player view
        val comparator = ReelTextExtractor.extractComparator(rootNode, packageName)

        if (comparator == null) {
            if (isShortsActive) {
                // User was actively watching Shorts. Check if they explicitly navigated to a non-Shorts page (e.g. Home tab, Search, Profile)
                val isExplicitNonShorts = ShortsDetector.isExplicitNonShortsPage(rootNode, packageName)
                if (isExplicitNonShorts) {
                    Log.d(TAG, "Explicit non-shorts navigation detected on $packageName. Hiding overlay.")
                    isShortsActive = false
                    stopTimeTracking()
                    scheduleDebouncedHide()
                } else {
                    // Transient event during active video playback (controls fade out, caption/sound update, comments open).
                    // Keep overlay steadily visible and time tracking active!
                    startTimeTracking(packageName)
                    lastShortsDetectedTime = now
                    cancelHideJob()
                    overlayManager.showCount(todayCount)
                }
            } else {
                // Check if user is on Shorts container without extracted text yet
                val isShortsContentPresent = ShortsDetector.shouldBlock(rootNode, packageName)
                if (isShortsContentPresent) {
                    isShortsActive = true
                    startTimeTracking(packageName)
                    lastShortsDetectedTime = now
                    cancelHideJob()
                    overlayManager.showCount(todayCount)
                } else {
                    scheduleDebouncedHide()
                }
            }
            return
        }

        // Active Reel/Shorts viewer confirmed!
        onShortsConfirmed(packageName, comparator)
    }

    private fun onShortsConfirmed(packageName: String, comparator: String) {
        isShortsActive = true
        startTimeTracking(packageName)
        lastShortsDetectedTime = SystemClock.elapsedRealtime()
        cancelHideJob()

        overlayManager.showCount(todayCount)

        Log.d(TAG, "Detected on $packageName: '$comparator'")

        // Detect reel scroll transitions
        if (comparator.isNotBlank()) {
            val isNewReel = analyzer.checkReelProgression(packageName, comparator)
            if (isNewReel) {
                val now = SystemClock.elapsedRealtime()
                if (currentReelComparator != null && currentReelStartTimeElapsed > 0) {
                    val watchedMs = now - currentReelStartTimeElapsed
                    if (watchedMs >= 500) {
                        val durationSec = String.format(Locale.US, "%.1f", watchedMs / 1000.0)
                        val shortText = currentReelComparator?.take(50)?.replace("\n", " ") ?: ""
                        Log.i(TAG, "Watched reel for ${durationSec}s | App: $packageName | Text: '$shortText'")
                    }
                }
                currentReelComparator = comparator
                currentReelStartTimeElapsed = now

                onReelCounted(packageName, comparator)
            }
        }
    }

    private fun isScreenOn(): Boolean {
        return try {
            powerManager?.isInteractive ?: true
        } catch (_: Exception) {
            true
        }
    }

    private fun startTimeTracking(packageName: String) {
        if (timeTrackingJob?.isActive == true && activeShortsPackage == packageName) return

        stopTimeTracking()
        activeShortsPackage = packageName
        shortsStartTimeElapsed = SystemClock.elapsedRealtime()

        timeTrackingJob = scope.launch {
            while (isActive && isShortsActive) {
                delay(1000L)
                if (!isScreenOn()) {
                    Log.d(TAG, "Screen turned off. Pausing reel time tracking.")
                    break
                }
                flushShortsTime(packageName)
            }
        }
    }

    private fun stopTimeTracking() {
        activeShortsPackage?.let { pkg ->
            flushShortsTime(pkg)
        }
        val now = SystemClock.elapsedRealtime()
        if (currentReelComparator != null && currentReelStartTimeElapsed > 0) {
            val watchedMs = now - currentReelStartTimeElapsed
            if (watchedMs >= 500) {
                val durationSec = String.format(Locale.US, "%.1f", watchedMs / 1000.0)
                val shortText = currentReelComparator?.take(50)?.replace("\n", " ") ?: ""
                Log.i(TAG, "Finished watching reel after ${durationSec}s | App: $activeShortsPackage | Text: '$shortText'")
            }
        }
        currentReelComparator = null
        currentReelStartTimeElapsed = 0L

        timeTrackingJob?.cancel()
        timeTrackingJob = null
        activeShortsPackage = null
    }

    private fun flushShortsTime(packageName: String) {
        val now = SystemClock.elapsedRealtime()
        val deltaMs = now - shortsStartTimeElapsed
        if (deltaMs <= 0) return
        shortsStartTimeElapsed = now

        val today = TimeUtils.todayKey()
        val wallNow = System.currentTimeMillis()
        scope.launch {
            try {
                val updatedRows = reelUsageStatsDao.addTime(today, packageName, deltaMs, wallNow)
                if (updatedRows == 0) {
                    reelUsageStatsDao.upsert(
                        ReelUsageStatsEntity(
                            date = today,
                            packageName = packageName,
                            totalTime = deltaMs,
                            reelCount = 0,
                            lastVisited = wallNow
                        )
                    )
                }
                val stats = reelUsageStatsDao.getForDate(today)
                totalTodayReelTimeMs = stats.sumOf { it.totalTime }
                Log.d(TAG, "Reel watch time updated: +${deltaMs}ms | $packageName | Total Today Reel Time: ${TimeUtils.formatDurationShort(totalTodayReelTimeMs)}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating reel time", e)
            }
        }
    }

    private fun scheduleDebouncedHide() {
        if (hideJob?.isActive == true) return

        hideJob = scope.launch {
            delay(HIDE_DEBOUNCE_MS)
            val elapsedSinceLastDetection = SystemClock.elapsedRealtime() - lastShortsDetectedTime
            if (elapsedSinceLastDetection >= HIDE_DEBOUNCE_MS && !isShortsActive) {
                withContext(Dispatchers.Main) {
                    overlayManager.hideOverlay()
                }
            }
        }
    }

    private fun cancelHideJob() {
        hideJob?.cancel()
        hideJob = null
    }

    private fun onLeftShortsApp(packageName: String? = null) {
        isShortsActive = false
        stopTimeTracking()
        activePackage = null
        cancelHideJob()
        analyzer.clear(packageName)
        overlayManager.removeOverlay()
    }

    fun removeOverlay() {
        isShortsActive = false
        stopTimeTracking()
        cancelHideJob()
        overlayManager.removeOverlay()
    }

    fun onDestroy() {
        isShortsActive = false
        stopTimeTracking()
        cancelHideJob()
        overlayManager.destroy()
        scope.cancel()
    }

    private fun onReelCounted(packageName: String, comparator: String) {
        val today = TimeUtils.todayKey()
        if (today != lastDateStr) {
            todayCount = 0
            totalTodayReelTimeMs = 0L
            lastDateStr = today
        }
        todayCount++

        Log.i(TAG, "Reel counted! App: $packageName, Text: '$comparator', Today Total: $todayCount")
        overlayManager.showCount(todayCount)

        val wallNow = System.currentTimeMillis()
        scope.launch {
            try {
                reelStatsDao.upsert(ReelStatsEntity(date = today, count = todayCount, lastUpdated = wallNow))
                val updatedRows = reelUsageStatsDao.incrementCount(today, packageName, wallNow)
                if (updatedRows == 0) {
                    reelUsageStatsDao.upsert(
                        ReelUsageStatsEntity(
                            date = today,
                            packageName = packageName,
                            totalTime = 0,
                            reelCount = 1,
                            lastVisited = wallNow
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadTodayCount() {
        try {
            lastDateStr = TimeUtils.todayKey()
            todayCount = reelStatsDao.getCount(lastDateStr) ?: 0
            val stats = reelUsageStatsDao.getForDate(lastDateStr)
            totalTodayReelTimeMs = stats.sumOf { it.totalTime }
        } catch (_: Exception) {
            todayCount = 0
            totalTodayReelTimeMs = 0L
        }
    }
}
