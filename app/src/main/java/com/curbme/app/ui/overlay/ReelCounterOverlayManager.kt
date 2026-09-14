package com.curbme.app.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Manages a floating counter overlay positioned on screen over short-video viewers
 * (YouTube Shorts, Instagram Reels, TikTok, Snapchat, etc.).
 * Uses TYPE_ACCESSIBILITY_OVERLAY to run seamlessly within accessibility service context.
 */
class ReelCounterOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: TextView? = null
    var isOverlayVisible = false
        private set
    private var isAddedToWindow = false
    private var lastDisplayedCount: Int? = null

    fun showCount(count: Int) {
        mainHandler.post {
            try {
                if (overlayView == null) {
                    createOverlayView()
                }

                if (lastDisplayedCount != count) {
                    overlayView?.text = count.toString()
                    lastDisplayedCount = count
                }

                val view = overlayView ?: return@post

                if (!isAddedToWindow) {
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = dpToPx(72) // Positioned below system status bar
                    }

                    try {
                        windowManager.addView(view, params)
                        isAddedToWindow = true
                    } catch (_: Exception) {}
                }

                if (view.visibility != View.VISIBLE) {
                    view.visibility = View.VISIBLE
                }
                isOverlayVisible = true
            } catch (_: Exception) {}
        }
    }

    /**
     * Smoothly hides the overlay without removing it from WindowManager (prevents flickering).
     */
    fun hideOverlay() {
        if (!isOverlayVisible && overlayView?.visibility == View.GONE) return

        mainHandler.post {
            try {
                overlayView?.visibility = View.GONE
            } catch (_: Exception) {}
            isOverlayVisible = false
        }
    }

    /**
     * Completely removes the view from WindowManager (used when switching away from target apps or destroying).
     */
    fun removeOverlay() {
        mainHandler.post {
            try {
                if (overlayView != null && isAddedToWindow) {
                    try {
                        windowManager.removeView(overlayView)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            isOverlayVisible = false
            isAddedToWindow = false
            overlayView = null
            lastDisplayedCount = null
        }
    }

    fun destroy() {
        removeOverlay()
    }

    private fun createOverlayView() {
        val tv = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            alpha = 0.85f
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#B3080E1A"))
                setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
            }
        }
        overlayView = tv
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
