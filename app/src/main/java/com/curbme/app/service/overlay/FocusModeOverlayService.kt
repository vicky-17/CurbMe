package com.curbme.app.service.overlay

import android.R
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.curbme.app.core.utils.Constants
import com.curbme.app.data.local.db.AppDatabase
import com.curbme.app.data.local.db.entity.FocusSessionEntity
import com.curbme.app.service.accessibility.GuardianAccessibilityService
import com.curbme.app.ui.overlay.OverlayLifecycleOwner
import com.curbme.app.ui.theme.CurbMeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.Volatile

class FocusModeOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var progressJob: Job? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var shutdownReceiver: BroadcastReceiver? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _remainingSecondsFlow = MutableStateFlow(0)
    val remainingSecondsFlow: StateFlow<Int> = _remainingSecondsFlow.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        registerScreenOffReceiver()
        registerShutdownReceiver()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        val action = intent?.action ?: ACTION_RESUME_FOCUS

        if (action == ACTION_STOP_FOCUS) {
            Log.i(TAG, "Received ACTION_STOP_FOCUS — stopping overlay service")
            hideOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_RESUME_FOCUS) {
            Log.i(TAG, "Received ACTION_RESUME_FOCUS — attempting recovery/resumption of active session")
            handleSessionStart(requestedDurationSeconds = null)
        } else if (action == ACTION_START_FOCUS) {
            val requestedDurationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 10) ?: 10
            handleSessionStart(requestedDurationSeconds = requestedDurationSeconds)
        } else {
            handleSessionStart(requestedDurationSeconds = null)
        }

        return START_STICKY
    }

    private fun handleSessionStart(requestedDurationSeconds: Int?) {
        serviceScope.launch(Dispatchers.IO) {
            var activeSession = FocusSessionManager.getActiveSession(this@FocusModeOverlayService)

            // Only create a new session if requested explicitly via ACTION_START_FOCUS with duration > 0
            if (activeSession == null && requestedDurationSeconds != null && requestedDurationSeconds > 0) {
                Log.i(TAG, "No active session found — creating new session with duration ${requestedDurationSeconds}s")
                activeSession = FocusSessionManager.startNewSession(this@FocusModeOverlayService, requestedDurationSeconds)
            }

            if (activeSession == null) {
                Log.w(TAG, "No active Focus session found to display or resume — stopping service")
                serviceScope.launch(Dispatchers.Main) {
                    hideOverlay()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }

            val remainingSeconds = FocusSessionManager.calculateRemainingSeconds(this@FocusModeOverlayService, activeSession)
            Log.i(TAG, "Active Focus session ${activeSession.id} loaded: $remainingSeconds seconds remaining")

            if (remainingSeconds <= 0) {
                Log.i(TAG, "Active Focus session ${activeSession.id} expired — marking COMPLETED")
                FocusSessionManager.markCompleted(this@FocusModeOverlayService, activeSession)
                serviceScope.launch(Dispatchers.Main) {
                    hideOverlay()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }

            _remainingSecondsFlow.value = remainingSeconds

            serviceScope.launch(Dispatchers.Main) {
                showOverlay(remainingSeconds, activeSession)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved called — persisting session progress synchronously")
        flushProgressToDbSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy called — cleaning up overlay and persisting progress synchronously")
        flushProgressToDbSync()
        unregisterScreenOffReceiver()
        unregisterShutdownReceiver()
        unregisterNetworkCallback()
        serviceJob.cancel()
        hideOverlay()
        isFocusModeActive = false
    }

    private fun showOverlay(remainingSeconds: Int, session: FocusSessionEntity) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay — permission missing")
            stopSelf()
            return
        }

        // Idempotency check: duplicate overlay requests re-sync without adding duplicate views
        if (composeView != null) {
            Log.i(TAG, "Overlay is already active — re-syncing remaining flow value")
            _remainingSecondsFlow.value = remainingSeconds
            return
        }

        isFocusModeActive = true

        val accessibilitySvc = GuardianAccessibilityService.instance
        val targetWindowManager = if (accessibilitySvc != null) {
            accessibilitySvc.getSystemService(WINDOW_SERVICE) as WindowManager
        } else {
            getSystemService(WINDOW_SERVICE) as WindowManager
        }
        windowManager = targetWindowManager

        val windowType = if (accessibilitySvc != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            setContent {
                CurbMeTheme {
                    FocusModeOverlayContent(
                        durationSeconds = remainingSeconds,
                        activeSession = session,
                        remainingSecondsFlow = remainingSecondsFlow,
                        onFinished = {
                            onNaturalCompletion(session)
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, params)
            lifecycleOwner?.onStart()
            lifecycleOwner?.onResume()
            startProgressPersistLoop()
            Log.i(TAG, "Focus mode overlay added successfully for session ${session.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding focus mode overlay", e)
            isFocusModeActive = false
            composeView = null
            stopSelf()
        }
    }

    private fun startProgressPersistLoop() {
        progressJob?.cancel()
        progressJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000L)
                flushProgressToDbSync()

                val latestSession = FocusSessionManager.getActiveSession(this@FocusModeOverlayService)
                if (latestSession != null) {
                    val dbRemainingSec = FocusSessionManager.calculateRemainingSeconds(this@FocusModeOverlayService, latestSession)

                    if (dbRemainingSec <= 0) {
                        Log.i(TAG, "Database remaining time <= 0 — ending Focus session and overlay")
                        serviceScope.launch(Dispatchers.Main) {
                            onNaturalCompletion(latestSession)
                        }
                        break
                    }

                    val currentUiSec = _remainingSecondsFlow.value
                    if (Math.abs(dbRemainingSec - currentUiSec) > 2) {
                        Log.i(TAG, "Overlay divergence detected (DB=$dbRemainingSec, UI=$currentUiSec) — re-syncing overlay countdown")
                        _remainingSecondsFlow.value = dbRemainingSec
                    }
                }
            }
        }
    }

    /**
     * Executes a single atomic DAO UPDATE query in a NonCancellable block.
     * Advances elapsedTimeMs and startElapsedRealtimeMs in ONE atomic operation.
     */
    private fun flushProgressToDbSync() {
        try {
            runBlocking(NonCancellable + Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(this@FocusModeOverlayService).focusSessionDao()
                val latestSession = dao.getActiveSession() ?: return@runBlocking
                val currentBootCount = FocusSessionManager.getBootCount(this@FocusModeOverlayService)

                if (currentBootCount != -1 && currentBootCount == latestSession.bootCount) {
                    val nowElapsedRealtime = SystemClock.elapsedRealtime()
                    val nowWallClock = System.currentTimeMillis()

                    if (nowElapsedRealtime >= latestSession.startElapsedRealtimeMs) {
                        val rowsUpdated = dao.updateProgressSameBoot(
                            sessionId = latestSession.id,
                            currentBootCount = currentBootCount,
                            nowElapsedRealtimeMs = nowElapsedRealtime,
                            nowWallClockMs = nowWallClock
                        )
                        Log.d(TAG, "Atomic sync flush for session ${latestSession.id}: rowsUpdated=$rowsUpdated")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during flushProgressToDbSync", e)
        }
    }

    private fun onNaturalCompletion(session: FocusSessionEntity) {
        Log.i(TAG, "Focus timer reached zero — completing session ${session.id}")
        serviceScope.launch(Dispatchers.IO) {
            val latest = FocusSessionManager.getActiveSession(this@FocusModeOverlayService) ?: session
            FocusSessionManager.markCompleted(this@FocusModeOverlayService, latest)
            serviceScope.launch(Dispatchers.Main) {
                stopFocusMode(this@FocusModeOverlayService)
            }
        }
    }

    private fun hideOverlay() {
        progressJob?.cancel()
        progressJob = null

        lifecycleOwner?.apply {
            onPause()
            onStop()
            onDestroy()
        }
        lifecycleOwner = null

        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.i(TAG, "Focus mode overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing focus mode overlay", e)
            }
        }
        composeView = null
        isFocusModeActive = false
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    Log.i(TAG, "Screen off detected — flushing Focus session progress to DB")
                    flushProgressToDbSync()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(this, screenOffReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister screenOffReceiver", e)
            }
        }
        screenOffReceiver = null
    }

    private fun registerShutdownReceiver() {
        if (shutdownReceiver != null) return
        shutdownReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "Shutdown broadcast received (${intent?.action}) — flushing Focus session progress to DB synchronously")
                flushProgressToDbSync()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
            addAction("com.htc.intent.action.QUICKBOOT_POWEROFF")
        }
        ContextCompat.registerReceiver(this, shutdownReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterShutdownReceiver() {
        shutdownReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister shutdownReceiver", e)
            }
        }
        shutdownReceiver = null
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Network available — triggering NTP fetch retry if Focus session active")
                serviceScope.launch(Dispatchers.IO) {
                    FocusSessionManager.fetchAndUpdateNtpOffset(this@FocusModeOverlayService)
                }
            }
        }
        try {
            networkCallback?.let {
                connectivityManager?.registerDefaultNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register networkCallback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister networkCallback", e)
            }
        }
        networkCallback = null
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_SILENT)
            .setContentTitle("Focus Mode Active")
            .setContentText("Screen locked for mindful focus pause.")
            .setSmallIcon(R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "FocusModeOverlayService"
        private const val NOTIFICATION_ID = 9081

        const val ACTION_START_FOCUS = "ACTION_START_FOCUS"
        const val ACTION_RESUME_FOCUS = "ACTION_RESUME_FOCUS"
        const val ACTION_STOP_FOCUS = "ACTION_STOP_FOCUS"
        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"

        @Volatile
        var isFocusModeActive: Boolean = false
            private set

        /**
         * Starts a new focus mode session with explicit user duration.
         */
        fun startFocusMode(context: Context, durationSeconds: Int = 10) {
            val intent = Intent(context, FocusModeOverlayService::class.java).apply {
                action = ACTION_START_FOCUS
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Resumes an existing focus mode session if active in DB.
         * NEVER creates a new session.
         */
        fun resumeFocusMode(context: Context) {
            val intent = Intent(context, FocusModeOverlayService::class.java).apply {
                action = ACTION_RESUME_FOCUS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopFocusMode(context: Context) {
            val intent = Intent(context, FocusModeOverlayService::class.java).apply {
                action = ACTION_STOP_FOCUS
            }
            context.startService(intent)
        }
    }
}
