package com.curbme.app.service.overlay

import android.R
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.curbme.app.core.utils.Constants
import com.curbme.app.service.accessibility.GuardianAccessibilityService
import com.curbme.app.ui.overlay.OverlayLifecycleOwner
import com.curbme.app.ui.theme.CurbMeTheme
import kotlin.concurrent.Volatile

class FocusModeOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action ?: ACTION_START_FOCUS
        if (action == ACTION_STOP_FOCUS) {
            hideOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 10)

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        showOverlay(durationSeconds)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        isFocusModeActive = false
    }

    private fun showOverlay(durationSeconds: Int) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay - permission missing")
            stopSelf()
            return
        }

        if (composeView != null) return

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
                        durationSeconds = durationSeconds,
                        onFinished = {
                            stopFocusMode(this@FocusModeOverlayService)
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, params)
            lifecycleOwner?.onStart()
            lifecycleOwner?.onResume()
            Log.i(TAG, "Focus mode overlay added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding focus mode overlay", e)
            isFocusModeActive = false
            composeView = null
            stopSelf()
        }
    }

    private fun hideOverlay() {
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
        const val ACTION_STOP_FOCUS = "ACTION_STOP_FOCUS"
        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"

        @Volatile
        var isFocusModeActive: Boolean = false
            private set

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

        fun stopFocusMode(context: Context) {
            val intent = Intent(context, FocusModeOverlayService::class.java).apply {
                action = ACTION_STOP_FOCUS
            }
            context.startService(intent)
        }
    }
}
