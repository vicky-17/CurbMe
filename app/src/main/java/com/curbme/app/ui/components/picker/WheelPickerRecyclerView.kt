package com.curbme.app.ui.components.picker

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.curbme.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WheelPickerRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    interface WheelPickerRecyclerViewListener {
        fun didSelectItem(position: Int)
    }

    private var listener: WheelPickerRecyclerViewListener? = null

    fun setWheelListener(listener: WheelPickerRecyclerViewListener) {
        this.listener = listener
    }

    private val camera: Camera = Camera()
    private val wheelMatrix: Matrix = Matrix()
    private val snapHelper = WheelSnapHelper()
    private var hapticFeedbackLastTriggerPosition: Int = 0

    private val vibrator: Vibrator? = try {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (_: Exception) {
        null
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundId: Int = try {
        soundPool.load(context, R.raw.tick, 1)
    } catch (_: Exception) {
        0
    }

    /**
     * The internal state when scrolling to the specified position and ignoring the vibration feedback.
     *
     * To disable vibration feedback externally, please use `isHapticFeedbackEnabled`.
     *
     * @see isHapticFeedbackEnabled
     * */
    private var ignoreHapticFeedback: Boolean = false

    var currentPosition: Int = NO_POSITION
        private set(value) {
            if (field == value) {
                return
            }
            field = value
            if (scrollState == SCROLL_STATE_IDLE) {
                listener?.didSelectItem(value)
            }
        }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        adapter?.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onChanged() {
                refreshCurrentPosition()
            }
        })
    }

    init {
        val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        linearLayoutManager.stackFromEnd = true
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        snapHelper.attachToRecyclerView(this)
        overScrollMode = OVER_SCROLL_NEVER
        setHasFixedSize(true)
        addItemDecoration(OffsetItemDecoration())
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateItemDecorations()
    }

    override fun scrollToPosition(position: Int) {
        scrollToCenterPosition(position)
    }

    override fun smoothScrollToPosition(position: Int) {
        smoothScrollToCenterPosition(position)
    }

    private fun smoothScrollToCenterPosition(position: Int, completion: (() -> Unit)? = null) {
        val listener: OnScrollListener = object: OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != SCROLL_STATE_IDLE) {
                    return
                }
                recyclerView.removeOnScrollListener(this)
                refreshCurrentPosition()
                completion?.invoke()
            }
        }
        super.smoothScrollToPosition(position)
        post {
            do {
                val layoutManager = this.layoutManager ?: break
                val view = layoutManager.findViewByPosition(position) ?: break

                val snapDistance =
                    snapHelper.calculateDistanceToFinalSnap(layoutManager, view) ?: break
                if (snapDistance[0] != 0 || snapDistance[1] != 0) {
                    addOnScrollListener(listener)
                    smoothScrollBy(snapDistance[0], snapDistance[1])
                    return@post
                }
                completion?.invoke()
            } while (false)
        }
    }

    fun smoothScrollToCenterPosition(
        position: Int,
        ignoreHapticFeedback: Boolean,
        completion: (() -> Unit)? = null
    ) {
        if (ignoreHapticFeedback && isHapticFeedbackEnabled) {
            this.ignoreHapticFeedback = true
            smoothScrollToCenterPosition(position) {
                this.ignoreHapticFeedback = false
                completion?.invoke()
            }
        } else {
            smoothScrollToCenterPosition(position, completion)
        }
    }

    private var scrollToCenterPositionListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private fun scrollToCenterPosition(position: Int, completion: (() -> Unit)? = null) {
        viewTreeObserver.removeOnGlobalLayoutListener(scrollToCenterPositionListener)
        scrollToCenterPositionListener = object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                do {
                    val layoutManager = this@WheelPickerRecyclerView.layoutManager ?: break
                    val view = layoutManager.findViewByPosition(position) ?: break

                    val snapDistance =
                        snapHelper.calculateDistanceToFinalSnap(layoutManager, view) ?: break
                    if (snapDistance[0] != 0 || snapDistance[1] != 0) {
                        scrollBy(snapDistance[0], snapDistance[1])
                    }
                } while (false)
                refreshCurrentPosition()
                completion?.invoke()
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(scrollToCenterPositionListener)
        super.scrollToPosition(position)
    }

    fun scrollToCenterPosition(
        position: Int,
        ignoreHapticFeedback: Boolean,
        completion: (() -> Unit)? = null
    ) {
        if (ignoreHapticFeedback && isHapticFeedbackEnabled) {
            this.ignoreHapticFeedback = true
            scrollToCenterPosition(position) {
                this.ignoreHapticFeedback = false
                completion?.invoke()
            }
        } else {
            scrollToCenterPosition(position, completion)
        }
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)

        val visibleCenterItemPosition = visibleCenterItemPosition()
        if (visibleCenterItemPosition == NO_POSITION) {
            return
        }

        if (currentPosition == NO_POSITION) {
            currentPosition = visibleCenterItemPosition
        }

        if (hapticFeedbackLastTriggerPosition != visibleCenterItemPosition) {
            hapticFeedbackLastTriggerPosition = visibleCenterItemPosition
            if (!ignoreHapticFeedback) {
                // Play custom tick.wav sound via SoundPool
                if (soundId != 0) {
                    try {
                        soundPool.play(soundId, 0.8f, 0.8f, 1, 0, 1.0f)
                    } catch (_: Exception) {}
                }

                // Direct vibration motor pulse (bypasses system UI haptic toggle)
                try {
                    if (vibrator?.hasVibrator() == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(10L)
                        }
                    }
                } catch (_: Exception) {}

                if (isHapticFeedbackEnabled) {
                    try {
                        performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE) {
            currentPosition = visibleCenterItemPosition()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            soundPool.release()
        } catch (_: Exception) {}
    }

    fun refreshCurrentPosition() {
        currentPosition = visibleCenterItemPosition()
    }

    private fun visibleCenterItemPosition(): Int {
        val linearLayoutManager = (layoutManager as? LinearLayoutManager) ?: return NO_POSITION

        val firstIndex = linearLayoutManager.findFirstVisibleItemPosition()
        val lastIndex = linearLayoutManager.findLastVisibleItemPosition()
        for (i in firstIndex..lastIndex) {
            val holder = findViewHolderForAdapterPosition(i) ?: continue
            val child: View = holder.itemView
            val centerY: Int = height / 2
            if (child.top <= centerY && child.bottom >= centerY) {
                return i
            }
        }
        return NO_POSITION
    }

    // reference: https://github.com/devilist/RecyclerWheelPicker/blob/master/recyclerwheelpicker/src/main/java/com/devilist/recyclerwheelpicker/widget/RecyclerWheelPicker.java
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val centerY = (height - paddingBottom - paddingTop) / 2
        val childCenterY = child.top + child.height / 2F
        val factor = (centerY - childCenterY) * 1f / centerY
        val alphaFactor = 1 - 0.7f * abs(factor)
        child.alpha = alphaFactor * alphaFactor * alphaFactor
        val scaleFactor = 1 - 0.3f * abs(factor)
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor

        val rotateRadius: Float = (2.0F * centerY / PI).toFloat()
        val rad = (centerY - childCenterY) * 1f / rotateRadius
        val offsetY = centerY - childCenterY - rotateRadius * sin(rad) * 1.3F
        child.translationY = offsetY

        canvas.save()
        camera.save()
        camera.translate(0F, 0F, rotateRadius * (1 - cos(rad)))
        camera.rotateX(rad * 180 / Math.PI.toFloat())
        camera.getMatrix(wheelMatrix)
        camera.restore()
        wheelMatrix.preTranslate(-child.width / 2F, -childCenterY)
        wheelMatrix.postTranslate(child.width / 2F, childCenterY)
        canvas.concat(wheelMatrix)
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restore()
        return result
    }
}

internal class WheelSnapHelper : LinearSnapHelper() {
    override fun findSnapView(layoutManager: RecyclerView.LayoutManager?): View? {
        return (layoutManager as? LinearLayoutManager)?.takeIf { isValidSnap(it) }
            ?.run { super.findSnapView(layoutManager) }
    }

    private fun isValidSnap(linearLayoutManager: LinearLayoutManager): Boolean {
        val firstCompletelyVisibleItemPosition =
            linearLayoutManager.findFirstCompletelyVisibleItemPosition()
        val lastCompletelyVisibleItemPosition =
            linearLayoutManager.findLastCompletelyVisibleItemPosition()
        return firstCompletelyVisibleItemPosition != 0 && lastCompletelyVisibleItemPosition != linearLayoutManager.itemCount - 1
    }
}

internal class OffsetItemDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val offset = (parent.height - view.layoutParams.height) / 2
        if (parent.getChildAdapterPosition(view) == 0) {
            (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 0
            outRect.top = offset
        } else if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 0
            outRect.bottom = offset
        }
    }
}
