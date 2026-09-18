package com.curbme.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.curbme.app.core.utils.Constants
import com.curbme.app.core.utils.UiDumper.dumpAll
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.data.local.prefs.Settings
import com.curbme.app.service.accessibility.detectors.ShortsDetector
import com.curbme.app.service.accessibility.handlers.AppBlockHandler
import com.curbme.app.service.accessibility.handlers.BrowserBlocker
import com.curbme.app.service.accessibility.handlers.ReelCounterHandler
import com.curbme.app.service.accessibility.handlers.ShortsBlockHandler
import com.curbme.app.service.accessibility.handlers.WebsiteBlockHandler
import com.curbme.app.service.accessibility.handlers.WebsiteUsageHandler
import com.curbme.app.service.monitor.AppUsageTracker
import com.curbme.app.service.overlay.FocusModeOverlayService
import com.curbme.app.ui.block.AppBlockOverlayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

class GuardianAccessibilityService : AccessibilityService() {
    private var dataStoreManager: DataStoreManager? = null
    private var settings = Settings()
    private val settingsFlow = MutableStateFlow(Settings())
    private var lastViewEventTimestamp: Long = 0L
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventChannel = Channel<AccessibilityEventInfo>(Channel.CONFLATED)
    private var shortsRecheckJob: Job? = null
    
    private data class AccessibilityEventInfo(
        val eventType: Int,
        val packageName: String?
    )
    
    private var shortsBlockHandler: ShortsBlockHandler? = null
    private var appBlockHandler: AppBlockHandler? = null
    private var appUsageTracker: AppUsageTracker? = null
    private var reelCounterHandler: ReelCounterHandler? = null
    private var websiteUsageHandler: WebsiteUsageHandler? = null
    private var websiteBlockHandler: WebsiteBlockHandler? = null
    private var browserBlocker: BrowserBlocker? = null

    private val browserCapabilityCache = ConcurrentHashMap<String, Boolean>()

    fun isBrowserSupported(packageName: String): Boolean {
        return browserCapabilityCache.getOrPut(packageName) {
            Constants.BrowserConstants.SUPPORTED_BROWSERS.contains(packageName)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        dataStoreManager = DataStoreManager(this)
        
        serviceScope.launch {
            dataStoreManager?.settings?.collect { newSettings ->
                settings = newSettings
                settingsFlow.value = newSettings
                val isOverlayEnabledInPrefs = PrefsManager(this@GuardianAccessibilityService).isReelCounterOverlayOn
                val isOverlayEnabledInSettings = newSettings.reelPlanConfig.isDisplayReelCounterOverlay
                if (!isOverlayEnabledInPrefs || !isOverlayEnabledInSettings) {
                    reelCounterHandler?.removeOverlay()
                }
            }
        }

        serviceScope.launch(Dispatchers.Default) {
            eventChannel.receiveAsFlow().collect { eventInfo ->
                try {
                    processEvent(eventInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing event", e)
                }
            }
        }

        shortsBlockHandler = ShortsBlockHandler(
            ShortsBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        appBlockHandler = AppBlockHandler(
            AppBlockHandler.ActionPerformer { action: Int -> this.performGlobalAction(action) })
        reelCounterHandler = ReelCounterHandler(this)
        websiteUsageHandler = WebsiteUsageHandler(this)
        websiteBlockHandler = WebsiteBlockHandler(this)
        browserBlocker = BrowserBlocker(this)
        
        // Use existing tracker if available, otherwise setup a new one
        appUsageTracker = AppUsageTracker.instance ?: AppUsageTracker().apply { setup(this@GuardianAccessibilityService) }
        
        serviceConnectedTimestamp = System.currentTimeMillis()
        lastEventTimestamp = 0L
        Log.i(TAG, "Guardian accessibility service connected")
    }

    override fun onInterrupt() {
        lastEventTimestamp = 0L
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        appUsageTracker?.onDestroy()
        reelCounterHandler?.onDestroy()
        websiteUsageHandler?.onDestroy()
        appUsageTracker = null
        reelCounterHandler = null
        websiteUsageHandler = null
        websiteBlockHandler = null
        browserBlocker = null
        instance = null
        Log.w(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventTimestamp = System.currentTimeMillis()
        if (event == null) return

        val eventType = event.eventType
        val pkgSeq = event.packageName

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            checkAndHandlePopupWindow()
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkgSeq != null) {
            val pkgName = pkgSeq.toString()
            lastForegroundPackage = pkgName
            // Hub & Spoke: Update central state immediately for fast notifications
            com.curbme.app.service.monitor.MonitorState.updateForegroundApp(pkgName)
            // System A: Notify block engine for immediate enforcement
            com.curbme.app.service.monitor.AppBlockEngineService.onAppSwitched(pkgName)
        }

        // Conflate events: only process the latest one to avoid interaction timeouts
        val eventInfo = AccessibilityEventInfo(event.eventType, event.packageName?.toString())
        eventChannel.trySend(eventInfo)
    }

    private fun processEvent(event: AccessibilityEventInfo) {
        val eventType = event.eventType
        val pkgSeq = event.packageName
        
        // ── Block Escape Suppression ──────────────────────────────────────────
        if (AppBlockOverlayManager.isOverlayShowing || FocusModeOverlayService.isFocusModeActive) {
            val pkg = pkgSeq ?: ""
            // If user tries to open Recents or System settings while overlay is up
            if (pkg == "com.android.systemui" || pkg == "com.android.settings") {
                Log.w(TAG, "Suppression: Forced HOME while overlay is showing")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            checkAndHandlePopupWindow()
            val root = rootInActiveWindow // Slow call
            if (root != null) {
                if (findAndPerformBack(root)) return
            }
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            && eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
            && eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
            && eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            return
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastViewEventTimestamp < MIN_VIEW_EVENT_INTERVAL_MS) {
                return
            }
            lastViewEventTimestamp = now
        }

        if (pkgSeq == null) {
            Log.d(TAG, "processEvent: pkgSeq is null, eventType=$eventType")
            return
        }
        val pkg = pkgSeq.toString()
        if (pkg == packageName) return

        val root = rootInActiveWindow
        if (root != null) {
            if (DEBUG_DUMP_UI) {
                dumpAll(root, pkg)
            }

            checkAndRecheckShorts(root, pkg)
            reelCounterHandler?.handleEvent(root, pkg, settings)
            websiteUsageHandler?.handleEvent(root, pkg, settings.isWebsiteUsageTrackingEnabled)
            val isWebBlocked = websiteBlockHandler?.handle(root, pkg, settings) { performGlobalAction(it) } ?: false
            if (!isWebBlocked) {
                appBlockHandler?.handle(root, pkg, eventType, settings)
            }
        } else {
            Log.d(TAG, "processEvent: rootInActiveWindow is null for $pkg")
            checkAndRecheckShorts(null, pkg)
            reelCounterHandler?.handleEvent(null, pkg, settings)
            websiteUsageHandler?.handleEvent(null, pkg, settings.isWebsiteUsageTrackingEnabled)
        }
    }

    private fun checkAndRecheckShorts(root: AccessibilityNodeInfo?, pkg: String) {
        val isBlocked = shortsBlockHandler?.handle(root, pkg, settings, this) ?: false
        if (isBlocked) {
            shortsRecheckJob?.cancel()
            shortsRecheckJob = null
            return
        }

        // If not blocked right away, but the target app is a short-video app (e.g. YouTube, Instagram),
        // schedule delayed re-checks to catch nodes as soon as layout/inflation completes.
        if (ShortsDetector.TARGET_SHORT_VIDEO_PACKAGES.contains(pkg)) {
            val isBlockShortsEnabled = settings.isBlockShorts || PrefsManager(this).isBlockShorts
            if (isBlockShortsEnabled) {
                shortsRecheckJob?.cancel()
                shortsRecheckJob = serviceScope.launch {
                    delay(200)
                    if (isActive) {
                        val freshRoot = rootInActiveWindow
                        val blockedAt200 = shortsBlockHandler?.handle(freshRoot, pkg, settings, this@GuardianAccessibilityService) ?: false
                        if (blockedAt200) return@launch
                    }
                    delay(300)
                    if (isActive) {
                        val freshRoot = rootInActiveWindow
                        shortsBlockHandler?.handle(freshRoot, pkg, settings, this@GuardianAccessibilityService)
                    }
                }
            }
        }
    }

    private fun findAndPerformBack(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (!settings.isAntiUninstallEnabled) return false

        if (!hasText(root, "CurbMe")) return false

        val dangerous =
            hasText(root, "Force stop")
                    || hasText(root, "Erase all data (factory reset)")
                    || hasText(root, "Deactivate this device admin app")
                    || hasText(root, "Use CurbMe")
                    || hasText(root, "Battery details")
                    || hasText(root, "VPN")



        if (dangerous) {
            Log.w(TAG, "🔒 Dangerous page for CurbMe → firing BACK")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        return false
    }

    private fun hasText(root: AccessibilityNodeInfo, text: String?): Boolean {
        try {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            return nodes != null && !nodes.isEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    // ── Popup & Split Screen Window Management ──────────────────────────────
    private var lastToastTime = 0L
    private var isClosingPopupWindow = false

    private fun checkAndHandlePopupWindow() {
        val isPopupWindowDisabled = settings.isDisablePopupWindowEnabled || PrefsManager(this).isDisablePopupWindowEnabled
        if (!isPopupWindowDisabled) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val activeWindows = windows ?: return

        for (window in activeWindows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

            val pkg = window.root?.packageName?.toString() ?: continue
            if (isExcludedPackage(pkg)) continue

            // Skip windows that aren't focused and active
            if (!window.isFocused && !window.isActive) continue

            val bounds = Rect()
            window.getBoundsInScreen(bounds)

            val isFloating = !(
                bounds.width() >= screenWidth * 0.95f &&
                bounds.height() >= screenHeight * 0.85f
            )

            if (isFloating) {
                Log.d(TAG, "Floating or split-screen window detected for pkg: $pkg. Closing via BACK...")
                closePopupWindowRepeatedly()
                showPopupWindowDisabledToast()
                break
            }
        }
    }

    private fun closePopupWindowRepeatedly() {
        if (isClosingPopupWindow) return
        isClosingPopupWindow = true

        performGlobalAction(GLOBAL_ACTION_BACK)

        serviceScope.launch(Dispatchers.Main) {
            var retries = 0
            while (retries < 5) {
                delay(120)
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                val stillFloating = windows?.any { window ->
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@any false
                    val pkg = window.root?.packageName?.toString() ?: return@any false
                    if (isExcludedPackage(pkg)) return@any false
                    if (!window.isFocused && !window.isActive) return@any false

                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    !(bounds.width() >= screenWidth * 0.95f && bounds.height() >= screenHeight * 0.85f)
                } ?: false

                if (stillFloating) {
                    Log.d(TAG, "Window still floating/split-screen (retry $retries) -> pressing BACK again")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    retries++
                } else {
                    break
                }
            }
            isClosingPopupWindow = false
        }
    }

    private fun showPopupWindowDisabledToast() {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 2500L) {
            lastToastTime = now
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Popup window or split screen is disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val systemPackages by lazy {
        setOf(
            "android",
            "com.android.systemui",
            "com.miui.home",
            "com.miui.securitycenter",
            packageName
        )
    }

    private fun isExcludedPackage(pkgName: String): Boolean {
        if (pkgName in systemPackages) return true
        if (pkgName.contains("launcher") || pkgName.contains("home")) return true
        if (pkgName == packageName) return true
        return false
    } //    private void logViewHierarchy(AccessibilityNodeInfo nodeInfo, int depth) {
    //        if (nodeInfo == null) return;
    //        StringBuilder prefix = new StringBuilder();
    //        for (int i = 0; i < depth; i++) {
    //            prefix.append("  ");
    //        }
    //        Log.d(TAG, prefix.toString() + nodeInfo.toString());
    //        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
    //            AccessibilityNodeInfo child = nodeInfo.getChild(i);
    //            if (child != null) {
    //                logViewHierarchy(child, depth + 1);
    //            }
    //        }
    //    }


    companion object {
        private const val TAG = "GuardianService"
        private const val MIN_VIEW_EVENT_INTERVAL_MS = 200L

        @JvmStatic
        fun disableService(context: Context? = null) {
            val svc = instance
            if (svc != null) {
                try {
                    svc.disableSelf()
                    Log.i(TAG, "disableSelf() invoked on GuardianAccessibilityService instance")
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking disableSelf()", e)
                }
            }

            if (context != null) {
                try {
                    val hasWriteSecure = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_SECURE_SETTINGS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasWriteSecure) {
                        val cr = context.contentResolver
                        val enabledServices = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                        val expected = android.content.ComponentName(context, GuardianAccessibilityService::class.java).flattenToString()
                        if (enabledServices.contains(expected)) {
                            val updated = enabledServices.split(":")
                                .filter { it.isNotBlank() && it != expected }
                                .joinToString(":")
                            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                            Log.i(TAG, "Accessibility service revoked via WRITE_SECURE_SETTINGS")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling accessibility via Settings.Secure", e)
                }
            }
        }

        @JvmField
        @Volatile
        var lastEventTimestamp: Long = 0L

        @JvmField
        @Volatile
        var serviceConnectedTimestamp: Long = 0L

        @Volatile
        var lastForegroundPackage: String? = null

        @Volatile
        var DEBUG_DUMP_UI: Boolean = false

        @Volatile
        var instance: GuardianAccessibilityService? = null
            private set
        @JvmStatic
        val currentRootNode: AccessibilityNodeInfo?
            get() {
                val svc: GuardianAccessibilityService? =
                    instance
                if (svc == null) return null
                try {
                    return svc.getRootInActiveWindow()
                } catch (e: Exception) {
                    return null
                }
            }
    }
}