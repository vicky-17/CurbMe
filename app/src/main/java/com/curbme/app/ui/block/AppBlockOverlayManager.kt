package com.curbme.app.ui.block

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.service.accessibility.GuardianAccessibilityService
import com.curbme.app.ui.overlay.OverlayLifecycleOwner
import com.curbme.app.ui.theme.CurbMeTheme
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppBlockOverlayManager(private val context: Context, private val dataStoreManager: DataStoreManager) : ViewModelStoreOwner {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private suspend fun pressBackTwice() {
        GuardianAccessibilityService.instance?.let { service ->
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(150L)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(150L)
        }
    }

    fun show(appName: String, packageName: String, reason: String, planType: String) {
        if (composeView != null || isOverlayShowing) return

        isOverlayShowing = true

        scope.launch {
            // First press back button twice before displaying overlay
            pressBackTwice()

            if (!isOverlayShowing || composeView != null) return@launch

            val owner = OverlayLifecycleOwner().apply { onCreate(); onStart(); onResume() }
            lifecycleOwner = owner
            
            composeView = ComposeView(context).apply {
                setParentCompositionContext(null)
                
                // Set owners so Compose can function in a WindowManager overlay
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(this@AppBlockOverlayManager)

                setContent {
                    CurbMeTheme {
                        AppBlockContent(
                            appName = appName,
                            reason = reason,
                            planType = planType,
                            onGoHome = { returnToHome() },
                            onRegain = { minutes -> 
                                regainApp(packageName, minutes)
                                hide()
                            }
                        )
                    }
                }
            }
            
            // System-level flags for a "sticky" full-screen experience
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.CENTER
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            try {
                windowManager.addView(composeView, params)
                Log.i(TAG, "Overlay shown for: $appName ($planType)")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view. Does app have SYSTEM_ALERT_WINDOW permission?", e)
                isOverlayShowing = false
                composeView = null
            }
        }
    }

    fun hide() {
        isOverlayShowing = false
        composeView?.let {
            try {
                windowManager.removeView(it)
                lifecycleOwner?.apply { onPause(); onStop(); onDestroy() }
                lifecycleOwner = null
                composeView = null
                Log.i(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
    }

    private fun returnToHome() {
        scope.launch {
            pressBackTwice()
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            hide()
        }
    }

    private fun regainApp(packageName: String, minutes: Int) {
        scope.launch {
            dataStoreManager.updateSettings { settings ->
                val newMap = settings.appBypassMap.toMutableMap()
                newMap[packageName] = System.currentTimeMillis() + (minutes * 60 * 1000L)
                settings.copy(appBypassMap = newMap)
            }
        }
    }

    companion object {
        private const val TAG = "AppBlockOverlayManager"
        
        @Volatile
        var isOverlayShowing: Boolean = false
            private set
    }
}
