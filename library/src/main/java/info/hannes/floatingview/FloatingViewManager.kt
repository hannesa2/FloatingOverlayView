package info.hannes.floatingview

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import java.util.*

class FloatingViewManager(private val context: Context, listener: FloatingViewListener?) : ScreenChangedListener, OnTouchListener, TrashViewListener {

    @IntDef(DISPLAY_MODE_SHOW_ALWAYS, DISPLAY_MODE_HIDE_ALWAYS, DISPLAY_MODE_HIDE_FULLSCREEN)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class DisplayMode

    @IntDef(MOVE_DIRECTION_DEFAULT, MOVE_DIRECTION_LEFT, MOVE_DIRECTION_RIGHT, MOVE_DIRECTION_NEAREST, MOVE_DIRECTION_NONE, MOVE_DIRECTION_THROWN)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class MoveDirection

    private val resources: Resources = context.resources
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics: DisplayMetrics = DisplayMetrics()
    private var targetFloatingView: FloatingView? = null
    private val fullscreenObserverView: FullscreenObserverView
    private val trashView: TrashView
    private val floatingViewListener: FloatingViewListener? = listener
    private val floatingViewRect: Rect = Rect()
    private val trashViewRect: Rect = Rect()
    private var moveAccept: Boolean

    @DisplayMode
    private var displayMode: Int
    private val safeInsetRect: Rect
    private val floatingViewList: ArrayList<FloatingView>
    private val isIntersectWithTrash: Boolean
        get() {
            if (!trashView.isTrashEnabled) {
                return false
            }
            trashView.getWindowDrawingRect(trashViewRect)
            targetFloatingView!!.getWindowDrawingRect(floatingViewRect)
            return Rect.intersects(trashViewRect, floatingViewRect)
        }

    init {
        moveAccept = false
        displayMode = DISPLAY_MODE_HIDE_FULLSCREEN
        safeInsetRect = Rect()
        floatingViewList = ArrayList()
        fullscreenObserverView = FullscreenObserverView(context, this)
        trashView = TrashView(context)
    }

    override fun onScreenChanged(windowRect: Rect?, visibility: Int) {
        // detect status bar
        val isFitSystemWindowTop = windowRect!!.top == 0
        val isHideStatusBar: Boolean
        isHideStatusBar = isFitSystemWindowTop

        // detect navigation bar
        val isHideNavigationBar: Boolean = if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                windowRect.width() - displayMetrics.widthPixels == 0 && windowRect.bottom - displayMetrics.heightPixels == 0
            } else {
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                windowRect.width() - displayMetrics.widthPixels > 0 || windowRect.height() - displayMetrics.heightPixels > 0
            }
        } else {
            visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        // update FloatingView layout
        targetFloatingView!!.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, windowRect)
        if (displayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return
        }
        moveAccept = false
        val state = targetFloatingView!!.state
        if (state == FloatingViewState.STATE_NORMAL) {
            val size = floatingViewList.size
            for (i in 0 until size) {
                val floatingView = floatingViewList[i]
                floatingView.visibility = if (isFitSystemWindowTop) View.GONE else View.VISIBLE
            }
            trashView.dismiss()
        } else if (state == FloatingViewState.STATE_INTERSECTING) {
            targetFloatingView!!.setFinishing()
            trashView.dismiss()
        }
    }

    override fun onUpdateActionTrashIcon() {
        trashView.updateActionTrashIcon(targetFloatingView!!.measuredWidth.toFloat(), targetFloatingView!!.measuredHeight.toFloat(), targetFloatingView!!.shape)
    }

    override fun onTrashAnimationStarted(@TrashView.AnimationState animationCode: Int) {
        if (animationCode == TrashView.ANIMATION_CLOSE || animationCode == TrashView.ANIMATION_FORCE_CLOSE) {
            val size = floatingViewList.size
            for (i in 0 until size) {
                val floatingView = floatingViewList[i]
                floatingView.setDraggable(false)
            }
        }
    }

    override fun onTrashAnimationEnd(@TrashView.AnimationState animationCode: Int) {
        val state = targetFloatingView!!.state
        if (state == FloatingViewState.STATE_FINISHING) {
            removeViewToWindow(targetFloatingView)
        }
        val size = floatingViewList.size
        for (i in 0 until size) {
            val floatingView = floatingViewList[i]
            floatingView.setDraggable(true)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action
        if (action != MotionEvent.ACTION_DOWN && !moveAccept) {
            return false
        }

        val state = targetFloatingView!!.state
        targetFloatingView = v as FloatingView
        if (action == MotionEvent.ACTION_DOWN) {
            moveAccept = true
        } else if (action == MotionEvent.ACTION_MOVE) {
            val isIntersecting = isIntersectWithTrash
            val isIntersect = state == FloatingViewState.STATE_INTERSECTING
            if (isIntersecting) {
                targetFloatingView!!.setIntersecting(trashView.trashIconCenterX.toInt(), trashView.trashIconCenterY.toInt())
            }
            if (isIntersecting && !isIntersect) {
                targetFloatingView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                trashView.setScaleTrashIcon(true)
            } else if (!isIntersecting && isIntersect) {
                targetFloatingView!!.setNormal()
                trashView.setScaleTrashIcon(false)
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (state == FloatingViewState.STATE_INTERSECTING) {
                targetFloatingView!!.setFinishing()
                trashView.setScaleTrashIcon(false)
            }
            moveAccept = false
            if (floatingViewListener != null) {
                val isFinishing = targetFloatingView!!.state == FloatingViewState.STATE_FINISHING
                val params = targetFloatingView!!.windowLayoutParams
                floatingViewListener.onTouchFinished(isFinishing, params.x, params.y)
            }
        }
        if (state == FloatingViewState.STATE_INTERSECTING) {
            trashView.onTouchFloatingView(event, floatingViewRect.left.toFloat(), floatingViewRect.top.toFloat())
        } else {
            val params = targetFloatingView!!.windowLayoutParams
            trashView.onTouchFloatingView(event, params.x.toFloat(), params.y.toFloat())
        }
        return false
    }

    fun setFixedTrashIconImage(@DrawableRes resId: Int) {
        trashView.setFixedTrashIconImage(resId)
    }

    fun setActionTrashIconImage(@DrawableRes resId: Int) {
        trashView.setActionTrashIconImage(resId)
    }

    fun setFixedTrashIconImage(drawable: Drawable?) {
        trashView.setFixedTrashIconImage(drawable)
    }

    fun setActionTrashIconImage(drawable: Drawable?) {
        trashView.setActionTrashIconImage(drawable)
    }

    fun setDisplayMode(@DisplayMode displayModeVal: Int) {
        displayMode = displayModeVal
        if (displayMode == DISPLAY_MODE_SHOW_ALWAYS || displayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (floatingView in floatingViewList) {
                floatingView.visibility = View.VISIBLE
            }
        } else if (displayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (floatingView in floatingViewList) {
                floatingView.visibility = View.GONE
            }
            trashView.dismiss()
        }
    }

    var isTrashViewEnabled: Boolean
        get() = trashView.isTrashEnabled
        set(enabled) {
            trashView.isTrashEnabled = enabled
        }

    /**
     * Set the DisplayCutout's safe area
     * Note:You must set the Cutout obtained on portrait orientation.
     *
     * @param safeInsetRect DisplayCutout#getSafeInsetXXX
     */
    fun setSafeInsetRect(safeInsetRectVal: Rect?) {
        if (safeInsetRectVal == null) {
            safeInsetRect.setEmpty()
        } else {
            safeInsetRect.set(safeInsetRectVal)
        }
        val size = floatingViewList.size
        if (size == 0) {
            return
        }
        for (i in 0 until size) {
            val floatingView = floatingViewList[i]
            floatingView.setSafeInsetRect(safeInsetRect)
        }
        // dirty hack
        fullscreenObserverView.onGlobalLayout()
    }

    fun addViewToWindow(view: View, options: Options) {
        val isFirstAttach = floatingViewList.isEmpty()
        // FloatingView
        val floatingView = FloatingView(context)
        floatingView.setInitCoords(options.floatingViewX, options.floatingViewY)
        floatingView.setOnTouchListener(this)
        floatingView.shape = options.shape
        floatingView.setOverMargin(options.overMargin)
        floatingView.setOverMarginX(options.overMarginX)
        floatingView.setOverMarginY(options.overMarginY)
        floatingView.setMoveDirection(options.moveDirection)
        floatingView.usePhysics(options.usePhysics)
        floatingView.setAnimateInitialMove(options.animateInitialMove)
        floatingView.setSafeInsetRect(safeInsetRect)

        // set FloatingView size
        val targetParams = FrameLayout.LayoutParams(options.floatingViewWidth, options.floatingViewHeight)
        view.layoutParams = targetParams
        floatingView.addView(view)
        if (displayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.visibility = View.GONE
        }
        floatingViewList.add(floatingView)
        trashView.setTrashViewListener(this)
        windowManager.addView(floatingView, floatingView.windowLayoutParams)
        if (isFirstAttach) {
            windowManager.addView(fullscreenObserverView, fullscreenObserverView.windowLayoutParams)
            targetFloatingView = floatingView
        } else {
            removeViewImmediate(trashView)
        }
        windowManager.addView(trashView, trashView.layoutParams)
    }

    private fun removeViewToWindow(floatingView: FloatingView?) {
        val matchIndex = floatingViewList.indexOf(floatingView)
        if (matchIndex != -1) {
            removeViewImmediate(floatingView)
            floatingViewList.removeAt(matchIndex)
        }
        if (floatingViewList.isEmpty()) {
            // 終了を通知
            floatingViewListener?.onFinishFloatingView()
        }
    }

    fun removeAllViewToWindow() {
        removeViewImmediate(fullscreenObserverView)
        removeViewImmediate(trashView)
        val size = floatingViewList.size
        for (i in 0 until size) {
            val floatingView = floatingViewList[i]
            removeViewImmediate(floatingView)
        }
        floatingViewList.clear()
    }

    private fun removeViewImmediate(view: View?) {
        try {
            windowManager.removeViewImmediate(view)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    class Options {
        var shape: Float = SHAPE_CIRCLE
        var overMargin: Int = 0
        var overMarginX: Int = 0
        var overMarginY: Int = 0
        var floatingViewX: Int = FloatingView.DEFAULT_X
        var floatingViewY: Int = FloatingView.DEFAULT_Y
        var floatingViewWidth: Int = FloatingView.DEFAULT_WIDTH
        var floatingViewHeight: Int = FloatingView.DEFAULT_HEIGHT

        @MoveDirection
        var moveDirection: Int = MOVE_DIRECTION_DEFAULT

        /**
         * Use of physics-based animations or (default) ValueAnimation
         */
        var usePhysics: Boolean = true
        var animateInitialMove: Boolean = true
    }

    companion object {
        const val DISPLAY_MODE_SHOW_ALWAYS = 1
        const val DISPLAY_MODE_HIDE_ALWAYS = 2
        const val DISPLAY_MODE_HIDE_FULLSCREEN = 3
        const val MOVE_DIRECTION_DEFAULT = 0
        const val MOVE_DIRECTION_LEFT = 1
        const val MOVE_DIRECTION_RIGHT = 2
        const val MOVE_DIRECTION_NONE = 3
        const val MOVE_DIRECTION_NEAREST = 4
        const val MOVE_DIRECTION_THROWN = 5
        const val SHAPE_CIRCLE = 1.0f
        const val SHAPE_RECTANGLE = 1.4142f

        /**
         * Find the safe area of DisplayCutout.
         *
         * @param activity [Activity] (Portrait and `windowLayoutInDisplayCutoutMode` != never)
         * @return Safe cutout insets.
         */
        fun findCutoutSafeArea(activity: Activity): Rect {
            val safeInsetRectInternal = Rect()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return safeInsetRectInternal
            }
            val windowInsets = activity.window.decorView.rootWindowInsets ?: return safeInsetRectInternal
            val displayCutout = windowInsets.displayCutout
            if (displayCutout != null) {
                safeInsetRectInternal[displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight] = displayCutout.safeInsetBottom
            }
            return safeInsetRectInternal
        }
    }

}