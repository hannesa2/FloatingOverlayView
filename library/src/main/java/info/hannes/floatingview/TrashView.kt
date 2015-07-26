package info.hannes.floatingview

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.IntDef
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.ref.WeakReference

internal class TrashView(context: Context) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {

    @IntDef(ANIMATION_NONE, ANIMATION_OPEN, ANIMATION_CLOSE, ANIMATION_FORCE_CLOSE)
    @Retention(RetentionPolicy.SOURCE)
    internal annotation class AnimationState

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val layoutParams: WindowManager.LayoutParams
    private val metrics: DisplayMetrics = DisplayMetrics()
    private val rootView: ViewGroup
    private val mTrashIconRootView: FrameLayout
    private val fixedTrashIconView: ImageView
    private val actionTrashIconView: ImageView
    private var actionTrashIconBaseWidth = 0
    private var actionTrashIconBaseHeight = 0
    private var actionTrashIconMaxScale = 0f
    private val mBackgroundView: FrameLayout
    private var enterScaleAnimator: ObjectAnimator? = null
    private var exitScaleAnimator: ObjectAnimator? = null
    private var animationHandler: AnimationHandler
    private var trashViewListener: TrashViewListener? = null
    private var misEnabled: Boolean

    companion object {
        private const val BACKGROUND_HEIGHT = 164
        private const val TARGET_CAPTURE_HORIZONTAL_REGION = 30.0f
        private const val TARGET_CAPTURE_VERTICAL_REGION = 4.0f
        private const val TRASH_ICON_SCALE_DURATION_MILLIS = 200L
        const val ANIMATION_NONE = 0
        const val ANIMATION_OPEN = 1
        const val ANIMATION_CLOSE = 2
        const val ANIMATION_FORCE_CLOSE = 3
        private val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()
        private var OVERLAY_TYPE = 0

        init {
            OVERLAY_TYPE = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewLayout()
    }

    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        mTrashIconRootView.translationY = mTrashIconRootView.measuredHeight.toFloat()
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        trashViewListener!!.onUpdateActionTrashIcon()
    }

    private fun updateViewLayout() {
        windowManager.defaultDisplay.getMetrics(metrics)
        layoutParams.x = (metrics.widthPixels - width) / 2
        layoutParams.y = 0

        // Update view and layout
        trashViewListener!!.onUpdateActionTrashIcon()
        animationHandler.onUpdateViewLayout()
        windowManager.updateViewLayout(this, layoutParams)
    }

    fun dismiss() {
        animationHandler.removeMessages(ANIMATION_OPEN)
        animationHandler.removeMessages(ANIMATION_CLOSE)
        animationHandler.sendAnimationMessage(ANIMATION_FORCE_CLOSE)
        setScaleTrashIconImmediately(false)
    }

    fun getWindowDrawingRect(outRect: Rect) {
        val iconView = if (hasActionTrashIcon()) actionTrashIconView else fixedTrashIconView
        val iconPaddingLeft = iconView.paddingLeft.toFloat()
        val iconPaddingTop = iconView.paddingTop.toFloat()
        val iconWidth = iconView.width - iconPaddingLeft - iconView.paddingRight
        val iconHeight = iconView.height - iconPaddingTop - iconView.paddingBottom
        val x = mTrashIconRootView.x + iconPaddingLeft
        val y = rootView.height - mTrashIconRootView.y - iconPaddingTop - iconHeight
        val left = (x - TARGET_CAPTURE_HORIZONTAL_REGION * metrics.density).toInt()
        val top = -rootView.height
        val right = (x + iconWidth + TARGET_CAPTURE_HORIZONTAL_REGION * metrics.density).toInt()
        val bottom = (y + iconHeight + TARGET_CAPTURE_VERTICAL_REGION * metrics.density).toInt()
        outRect[left, top, right] = bottom
    }

    fun updateActionTrashIcon(width: Float, height: Float, shape: Float) {
        if (!hasActionTrashIcon()) {
            return
        }
        animationHandler.targetWidth = width
        animationHandler.targetHeight = height
        val newWidthScale = width / actionTrashIconBaseWidth * shape
        val newHeightScale = height / actionTrashIconBaseHeight * shape
        actionTrashIconMaxScale = Math.max(newWidthScale, newHeightScale)
        enterScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(actionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, actionTrashIconMaxScale), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, actionTrashIconMaxScale))
        enterScaleAnimator!!.interpolator = OvershootInterpolator()
        enterScaleAnimator!!.duration = TRASH_ICON_SCALE_DURATION_MILLIS
        exitScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(actionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, 1.0f), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, 1.0f))
        exitScaleAnimator!!.interpolator = OvershootInterpolator()
        exitScaleAnimator!!.duration = TRASH_ICON_SCALE_DURATION_MILLIS
    }

    val trashIconCenterX: Float
        get() {
            val iconView = if (hasActionTrashIcon()) actionTrashIconView else fixedTrashIconView
            val iconViewPaddingLeft = iconView.paddingLeft.toFloat()
            val iconWidth = iconView.width - iconViewPaddingLeft - iconView.paddingRight
            val x = mTrashIconRootView.x + iconViewPaddingLeft
            return x + iconWidth / 2
        }

    val trashIconCenterY: Float
        get() {
            val iconView = if (hasActionTrashIcon()) actionTrashIconView else fixedTrashIconView
            val iconViewHeight = iconView.height.toFloat()
            val iconViewPaddingBottom = iconView.paddingBottom.toFloat()
            val iconHeight = iconViewHeight - iconView.paddingTop - iconViewPaddingBottom
            val y = rootView.height - mTrashIconRootView.y - iconViewHeight + iconViewPaddingBottom
            return y + iconHeight / 2
        }

    private fun hasActionTrashIcon(): Boolean {
        return actionTrashIconBaseWidth != 0 && actionTrashIconBaseHeight != 0
    }

    fun setFixedTrashIconImage(resId: Int) {
        fixedTrashIconView.setImageResource(resId)
    }

    fun setActionTrashIconImage(resId: Int) {
        actionTrashIconView.setImageResource(resId)
        val drawable = actionTrashIconView.drawable
        if (drawable != null) {
            actionTrashIconBaseWidth = drawable.intrinsicWidth
            actionTrashIconBaseHeight = drawable.intrinsicHeight
        }
    }

    fun setFixedTrashIconImage(drawable: Drawable?) {
        fixedTrashIconView.setImageDrawable(drawable)
    }

    fun setActionTrashIconImage(drawable: Drawable?) {
        actionTrashIconView.setImageDrawable(drawable)
        if (drawable != null) {
            actionTrashIconBaseWidth = drawable.intrinsicWidth
            actionTrashIconBaseHeight = drawable.intrinsicHeight
        }
    }

    private fun setScaleTrashIconImmediately(isEnter: Boolean) {
        cancelScaleTrashAnimation()
        actionTrashIconView.scaleX = if (isEnter) actionTrashIconMaxScale else 1.0f
        actionTrashIconView.scaleY = if (isEnter) actionTrashIconMaxScale else 1.0f
    }

    fun setScaleTrashIcon(isEnter: Boolean) {
        if (!hasActionTrashIcon()) {
            return
        }
        cancelScaleTrashAnimation()
        if (isEnter) {
            enterScaleAnimator!!.start()
        } else {
            exitScaleAnimator!!.start()
        }
    }

    var isTrashEnabled: Boolean
        get() = misEnabled
        set(enabled) {
            if (misEnabled == enabled) {
                return
            }
            misEnabled = enabled
            if (!misEnabled) {
                dismiss()
            }
        }

    private fun cancelScaleTrashAnimation() {
        if (enterScaleAnimator != null && enterScaleAnimator!!.isStarted) {
            enterScaleAnimator!!.cancel()
        }
        if (exitScaleAnimator != null && exitScaleAnimator!!.isStarted) {
            exitScaleAnimator!!.cancel()
        }
    }

    fun setTrashViewListener(listener: TrashViewListener?) {
        trashViewListener = listener
    }

    fun onTouchFloatingView(event: MotionEvent, x: Float, y: Float) {
        val action = event.action
        if (action == MotionEvent.ACTION_DOWN) {
            animationHandler.updateTargetPosition(x, y)
            animationHandler.removeMessages(ANIMATION_CLOSE)
            animationHandler.sendAnimationMessageDelayed(ANIMATION_OPEN, LONG_PRESS_TIMEOUT.toLong())
        } else if (action == MotionEvent.ACTION_MOVE) {
            animationHandler.updateTargetPosition(x, y)
            if (!animationHandler.isAnimationStarted(ANIMATION_OPEN)) {
                animationHandler.removeMessages(ANIMATION_OPEN)
                animationHandler.sendAnimationMessage(ANIMATION_OPEN)
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            animationHandler.removeMessages(ANIMATION_OPEN)
            animationHandler.sendAnimationMessage(ANIMATION_CLOSE)
        }
    }

    internal class AnimationHandler(trashView: TrashView) : Handler() {
        private var startTime: Long = 0
        private var startAlpha = 0f
        private var startTransitionY = 0f
        private var startedCode: Int
        private var targetPositionX = 0f
        private var targetPositionY = 0f
        var targetWidth = 0f
        var targetHeight = 0f
        private val trashIconLimitPosition: Rect
        private var moveStickyYRange = 0f
        private val overshootInterpolator: OvershootInterpolator
        private val weakTrashView: WeakReference<TrashView> = WeakReference(trashView)

        override fun handleMessage(msg: Message) {
            val trashView = weakTrashView.get()
            if (trashView == null) {
                removeMessages(ANIMATION_OPEN)
                removeMessages(ANIMATION_CLOSE)
                removeMessages(ANIMATION_FORCE_CLOSE)
                return
            }
            if (!trashView.isTrashEnabled) {
                return
            }
            val animationCode = msg.what
            val animationType = msg.arg1
            val backgroundView = trashView.mBackgroundView
            val trashIconRootView = trashView.mTrashIconRootView
            val listener = trashView.trashViewListener
            val screenWidth = trashView.metrics.widthPixels.toFloat()
            val trashViewX = trashView.layoutParams.x.toFloat()
            if (animationType == TYPE_FIRST) {
                startTime = SystemClock.uptimeMillis()
                startAlpha = backgroundView.alpha
                startTransitionY = trashIconRootView.translationY
                startedCode = animationCode
                listener?.onTrashAnimationStarted(startedCode)
            }
            val elapsedTime = SystemClock.uptimeMillis() - startTime.toFloat()
            if (animationCode == ANIMATION_OPEN) {
                val currentAlpha = backgroundView.alpha
                if (currentAlpha < MAX_ALPHA) {
                    val alphaTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f)
                    val alpha = Math.min(startAlpha + alphaTimeRate, MAX_ALPHA)
                    backgroundView.alpha = alpha
                }
                if (elapsedTime >= TRASH_OPEN_START_DELAY_MILLIS) {
                    val screenHeight = trashView.metrics.heightPixels.toFloat()
                    val positionX = trashViewX + (targetPositionX + targetWidth) / (screenWidth + targetWidth) * trashIconLimitPosition.width() + trashIconLimitPosition.left
                    val targetPositionYRate = Math.min(2 * (targetPositionY + targetHeight) / (screenHeight + targetHeight), 1.0f)
                    val stickyPositionY = moveStickyYRange * targetPositionYRate + trashIconLimitPosition.height() - moveStickyYRange
                    val translationYTimeRate = Math.min((elapsedTime - TRASH_OPEN_START_DELAY_MILLIS) / TRASH_OPEN_DURATION_MILLIS, 1.0f)
                    val positionY = trashIconLimitPosition.bottom - stickyPositionY * overshootInterpolator.getInterpolation(translationYTimeRate)
                    trashIconRootView.translationX = positionX
                    trashIconRootView.translationY = positionY
                    // clear drag view garbage
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        clearClippedChildren(trashView.rootView)
                        clearClippedChildren(trashView.mTrashIconRootView)
                    }
                }
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            } else if (animationCode == ANIMATION_CLOSE) {
                val alphaElapseTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f)
                val alpha = Math.max(startAlpha - alphaElapseTimeRate, MIN_ALPHA)
                backgroundView.alpha = alpha
                val translationYTimeRate = Math.min(elapsedTime / TRASH_CLOSE_DURATION_MILLIS, 1.0f)
                if (alphaElapseTimeRate < 1.0f || translationYTimeRate < 1.0f) {
                    val position = startTransitionY + trashIconLimitPosition.height() * translationYTimeRate
                    trashIconRootView.translationY = position
                    sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
                } else {
                    trashIconRootView.translationY = trashIconLimitPosition.bottom.toFloat()
                    startedCode = ANIMATION_NONE
                    listener?.onTrashAnimationEnd(ANIMATION_CLOSE)
                }
            } else if (animationCode == ANIMATION_FORCE_CLOSE) {
                backgroundView.alpha = 0.0f
                trashIconRootView.translationY = trashIconLimitPosition.bottom.toFloat()
                startedCode = ANIMATION_NONE
                listener?.onTrashAnimationEnd(ANIMATION_FORCE_CLOSE)
            }
        }

        fun sendAnimationMessageDelayed(animation: Int, delayMillis: Long) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis)
        }

        fun sendAnimationMessage(animation: Int) {
            sendMessage(newMessage(animation, TYPE_FIRST))
        }

        fun isAnimationStarted(animationCode: Int): Boolean {
            return startedCode == animationCode
        }

        fun updateTargetPosition(x: Float, y: Float) {
            targetPositionX = x
            targetPositionY = y
        }

        fun onUpdateViewLayout() {
            val trashView = weakTrashView.get() ?: return
            val density = trashView.metrics.density
            val backgroundHeight = trashView.mBackgroundView.measuredHeight.toFloat()
            val offsetX = TRASH_MOVE_LIMIT_OFFSET_X * density
            val trashIconHeight = trashView.mTrashIconRootView.measuredHeight
            val left = (-offsetX).toInt()
            val top = ((trashIconHeight - backgroundHeight) / 2 - TRASH_MOVE_LIMIT_TOP_OFFSET * density).toInt()
            val right = offsetX.toInt()
            trashIconLimitPosition[left, top, right] = trashIconHeight
            moveStickyYRange = backgroundHeight * 0.20f
        }

        companion object {
            private const val ANIMATION_REFRESH_TIME_MILLIS = 10L
            private const val BACKGROUND_DURATION_MILLIS = 200L
            private const val TRASH_OPEN_START_DELAY_MILLIS = 200L
            private const val TRASH_OPEN_DURATION_MILLIS = 400L
            private const val TRASH_CLOSE_DURATION_MILLIS = 200L
            private const val OVERSHOOT_TENSION = 1.0f
            private const val TRASH_MOVE_LIMIT_OFFSET_X = 22
            private const val TRASH_MOVE_LIMIT_TOP_OFFSET = -4
            private const val TYPE_FIRST = 1
            private const val TYPE_UPDATE = 2
            private const val MAX_ALPHA = 1.0f
            private const val MIN_ALPHA = 0.0f

            /**
             * Clear the animation garbage of the target view.
             */
            private fun clearClippedChildren(viewGroup: ViewGroup) {
                viewGroup.clipChildren = true
                viewGroup.invalidate()
                viewGroup.clipChildren = false
            }

            private fun newMessage(animation: Int, type: Int): Message {
                val message = Message.obtain()
                message.what = animation
                message.arg1 = type
                return message
            }
        }

        init {
            startedCode = ANIMATION_NONE
            trashIconLimitPosition = Rect()
            overshootInterpolator = OvershootInterpolator(OVERSHOOT_TENSION)
        }
    }

    init {
        windowManager.defaultDisplay.getMetrics(metrics)
        animationHandler = AnimationHandler(this)
        misEnabled = true
        layoutParams = WindowManager.LayoutParams()
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.type = OVERLAY_TYPE
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.gravity = Gravity.LEFT or Gravity.BOTTOM
        rootView = FrameLayout(context)
        rootView.clipChildren = false
        mTrashIconRootView = FrameLayout(context)
        mTrashIconRootView.clipChildren = false
        fixedTrashIconView = ImageView(context)
        actionTrashIconView = ImageView(context)
        mBackgroundView = FrameLayout(context)
        mBackgroundView.alpha = 0.0f
        val gradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x50000000))
        mBackgroundView.background = gradientDrawable
        val backgroundParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (BACKGROUND_HEIGHT * metrics.density).toInt())
        backgroundParams.gravity = Gravity.BOTTOM
        rootView.addView(mBackgroundView, backgroundParams)
        val actionTrashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        actionTrashIconParams.gravity = Gravity.CENTER
        mTrashIconRootView.addView(actionTrashIconView, actionTrashIconParams)
        val fixedTrashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fixedTrashIconParams.gravity = Gravity.CENTER
        mTrashIconRootView.addView(fixedTrashIconView, fixedTrashIconParams)
        val trashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        trashIconParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        rootView.addView(mTrashIconRootView, trashIconParams)
        addView(rootView)
        viewTreeObserver.addOnPreDrawListener(this)
    }
}