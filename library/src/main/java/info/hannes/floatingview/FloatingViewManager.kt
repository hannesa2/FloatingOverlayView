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
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

class FloatingViewManager(private val mContext: Context, listener: FloatingViewListener?) : ScreenChangedListener, OnTouchListener, TrashViewListener {
    @IntDef(DISPLAY_MODE_SHOW_ALWAYS, DISPLAY_MODE_HIDE_ALWAYS, DISPLAY_MODE_HIDE_FULLSCREEN)
    @Retention(RetentionPolicy.SOURCE)
    annotation class DisplayMode

    @IntDef(MOVE_DIRECTION_DEFAULT, MOVE_DIRECTION_LEFT, MOVE_DIRECTION_RIGHT, MOVE_DIRECTION_NEAREST, MOVE_DIRECTION_NONE, MOVE_DIRECTION_THROWN)
    @Retention(RetentionPolicy.SOURCE)
    annotation class MoveDirection

    private val mResources: Resources
    private val mWindowManager: WindowManager
    private val mDisplayMetrics: DisplayMetrics
    private var mTargetFloatingView: FloatingView? = null
    private val mFullscreenObserverView: FullscreenObserverView
    private val mTrashView: TrashView
    private val mFloatingViewListener: FloatingViewListener?
    private val mFloatingViewRect: Rect
    private val mTrashViewRect: Rect
    private var mIsMoveAccept: Boolean

    @DisplayMode
    private var mDisplayMode: Int
    private val mSafeInsetRect: Rect
    private val mFloatingViewList: ArrayList<FloatingView>
    private val isIntersectWithTrash: Boolean
        get() {
            if (!mTrashView.isTrashEnabled) {
                return false
            }
            mTrashView.getWindowDrawingRect(mTrashViewRect)
            mTargetFloatingView!!.getWindowDrawingRect(mFloatingViewRect)
            return Rect.intersects(mTrashViewRect, mFloatingViewRect)
        }

    override fun onScreenChanged(windowRect: Rect?, visibility: Int) {
        // detect status bar
        val isFitSystemWindowTop = windowRect!!.top == 0
        val isHideStatusBar: Boolean
        isHideStatusBar = isFitSystemWindowTop

        // detect navigation bar
        val isHideNavigationBar: Boolean
        isHideNavigationBar = if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mWindowManager.defaultDisplay.getRealMetrics(mDisplayMetrics)
                windowRect.width() - mDisplayMetrics.widthPixels == 0 && windowRect.bottom - mDisplayMetrics.heightPixels == 0
            } else {
                mWindowManager.defaultDisplay.getMetrics(mDisplayMetrics)
                windowRect.width() - mDisplayMetrics.widthPixels > 0 || windowRect.height() - mDisplayMetrics.heightPixels > 0
            }
        } else {
            visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        val isPortrait = mResources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        // update FloatingView layout
        mTargetFloatingView!!.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, windowRect)
        if (mDisplayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return
        }
        mIsMoveAccept = false
        val state = mTargetFloatingView!!.state
        if (state == FloatingView.STATE_NORMAL) {
            val size = mFloatingViewList.size
            for (i in 0 until size) {
                val floatingView = mFloatingViewList[i]
                floatingView.visibility = if (isFitSystemWindowTop) View.GONE else View.VISIBLE
            }
            mTrashView.dismiss()
        } else if (state == FloatingView.STATE_INTERSECTING) {
            mTargetFloatingView!!.setFinishing()
            mTrashView.dismiss()
        }
    }

    override fun onUpdateActionTrashIcon() {
        mTrashView.updateActionTrashIcon(mTargetFloatingView!!.measuredWidth.toFloat(), mTargetFloatingView!!.measuredHeight.toFloat(), mTargetFloatingView!!.shape)
    }

    override fun onTrashAnimationStarted(@TrashView.AnimationState animationCode: Int) {
        if (animationCode == TrashView.ANIMATION_CLOSE || animationCode == TrashView.ANIMATION_FORCE_CLOSE) {
            val size = mFloatingViewList.size
            for (i in 0 until size) {
                val floatingView = mFloatingViewList[i]
                floatingView.setDraggable(false)
            }
        }
    }

    override fun onTrashAnimationEnd(@TrashView.AnimationState animationCode: Int) {
        val state = mTargetFloatingView!!.state
        if (state == FloatingView.STATE_FINISHING) {
            removeViewToWindow(mTargetFloatingView)
        }
        val size = mFloatingViewList.size
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            floatingView.setDraggable(true)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action
        if (action != MotionEvent.ACTION_DOWN && !mIsMoveAccept) {
            return false
        }
        val state = mTargetFloatingView!!.state
        mTargetFloatingView = v as FloatingView
        if (action == MotionEvent.ACTION_DOWN) {
            mIsMoveAccept = true
        } else if (action == MotionEvent.ACTION_MOVE) {
            val isIntersecting = isIntersectWithTrash
            val isIntersect = state == FloatingView.STATE_INTERSECTING
            if (isIntersecting) {
                mTargetFloatingView!!.setIntersecting(mTrashView.trashIconCenterX.toInt(), mTrashView.trashIconCenterY.toInt())
            }
            if (isIntersecting && !isIntersect) {
                mTargetFloatingView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                mTrashView.setScaleTrashIcon(true)
            } else if (!isIntersecting && isIntersect) {
                mTargetFloatingView!!.setNormal()
                mTrashView.setScaleTrashIcon(false)
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (state == FloatingView.STATE_INTERSECTING) {
                mTargetFloatingView!!.setFinishing()
                mTrashView.setScaleTrashIcon(false)
            }
            mIsMoveAccept = false
            if (mFloatingViewListener != null) {
                val isFinishing = mTargetFloatingView!!.state == FloatingView.STATE_FINISHING
                val params = mTargetFloatingView!!.windowLayoutParams
                mFloatingViewListener.onTouchFinished(isFinishing, params.x, params.y)
            }
        }
        if (state == FloatingView.STATE_INTERSECTING) {
            mTrashView.onTouchFloatingView(event, mFloatingViewRect.left.toFloat(), mFloatingViewRect.top.toFloat())
        } else {
            val params = mTargetFloatingView!!.windowLayoutParams
            mTrashView.onTouchFloatingView(event, params.x.toFloat(), params.y.toFloat())
        }
        return false
    }

    fun setFixedTrashIconImage(@DrawableRes resId: Int) {
        mTrashView.setFixedTrashIconImage(resId)
    }

    fun setActionTrashIconImage(@DrawableRes resId: Int) {
        mTrashView.setActionTrashIconImage(resId)
    }

    fun setFixedTrashIconImage(drawable: Drawable?) {
        mTrashView.setFixedTrashIconImage(drawable)
    }

    fun setActionTrashIconImage(drawable: Drawable?) {
        mTrashView.setActionTrashIconImage(drawable)
    }

    fun setDisplayMode(@DisplayMode displayMode: Int) {
        mDisplayMode = displayMode
        if (mDisplayMode == DISPLAY_MODE_SHOW_ALWAYS || mDisplayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (floatingView in mFloatingViewList) {
                floatingView.visibility = View.VISIBLE
            }
        } else if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (floatingView in mFloatingViewList) {
                floatingView.visibility = View.GONE
            }
            mTrashView.dismiss()
        }
    }

    var isTrashViewEnabled: Boolean
        get() = mTrashView.isTrashEnabled
        set(enabled) {
            mTrashView.isTrashEnabled = enabled
        }

    /**
     * Set the DisplayCutout's safe area
     * Note:You must set the Cutout obtained on portrait orientation.
     *
     * @param safeInsetRect DisplayCutout#getSafeInsetXXX
     */
    fun setSafeInsetRect(safeInsetRect: Rect?) {
        if (safeInsetRect == null) {
            mSafeInsetRect.setEmpty()
        } else {
            mSafeInsetRect.set(safeInsetRect)
        }
        val size = mFloatingViewList.size
        if (size == 0) {
            return
        }
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            floatingView.setSafeInsetRect(mSafeInsetRect)
        }
        // dirty hack
        mFullscreenObserverView.onGlobalLayout()
    }

    fun addViewToWindow(view: View, options: Options) {
        val isFirstAttach = mFloatingViewList.isEmpty()
        // FloatingView
        val floatingView = FloatingView(mContext)
        floatingView.setInitCoords(options.floatingViewX, options.floatingViewY)
        floatingView.setOnTouchListener(this)
        floatingView.shape = options.shape
        floatingView.setOverMargin(options.overMargin)
        floatingView.setMoveDirection(options.moveDirection)
        floatingView.usePhysics(options.usePhysics)
        floatingView.setAnimateInitialMove(options.animateInitialMove)
        floatingView.setSafeInsetRect(mSafeInsetRect)

        // set FloatingView size
        val targetParams = FrameLayout.LayoutParams(options.floatingViewWidth, options.floatingViewHeight)
        view.layoutParams = targetParams
        floatingView.addView(view)
        if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.visibility = View.GONE
        }
        mFloatingViewList.add(floatingView)
        mTrashView.setTrashViewListener(this)
        mWindowManager.addView(floatingView, floatingView.windowLayoutParams)
        if (isFirstAttach) {
            mWindowManager.addView(mFullscreenObserverView, mFullscreenObserverView.windowLayoutParams)
            mTargetFloatingView = floatingView
        } else {
            removeViewImmediate(mTrashView)
        }
        mWindowManager.addView(mTrashView, mTrashView.layoutParams)
    }

    private fun removeViewToWindow(floatingView: FloatingView?) {
        val matchIndex = mFloatingViewList.indexOf(floatingView)
        if (matchIndex != -1) {
            removeViewImmediate(floatingView)
            mFloatingViewList.removeAt(matchIndex)
        }
        if (mFloatingViewList.isEmpty()) {
            // 終了を通知
            mFloatingViewListener?.onFinishFloatingView()
        }
    }

    fun removeAllViewToWindow() {
        removeViewImmediate(mFullscreenObserverView)
        removeViewImmediate(mTrashView)
        val size = mFloatingViewList.size
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            removeViewImmediate(floatingView)
        }
        mFloatingViewList.clear()
    }

    private fun removeViewImmediate(view: View?) {
        try {
            mWindowManager.removeViewImmediate(view)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    class Options {
        var shape: Float
        var overMargin: Int
        var floatingViewX: Int
        var floatingViewY: Int
        var floatingViewWidth: Int
        var floatingViewHeight: Int

        @MoveDirection
        var moveDirection: Int

        /**
         * Use of physics-based animations or (default) ValueAnimation
         */
        var usePhysics: Boolean
        var animateInitialMove: Boolean

        init {
            shape = SHAPE_CIRCLE
            overMargin = 0
            floatingViewX = FloatingView.DEFAULT_X
            floatingViewY = FloatingView.DEFAULT_Y
            floatingViewWidth = FloatingView.DEFAULT_WIDTH
            floatingViewHeight = FloatingView.DEFAULT_HEIGHT
            moveDirection = MOVE_DIRECTION_DEFAULT
            usePhysics = true
            animateInitialMove = true
        }
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
            val safeInsetRect = Rect()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return safeInsetRect
            }
            val windowInsets = activity.window.decorView.rootWindowInsets ?: return safeInsetRect
            val displayCutout = windowInsets.displayCutout
            if (displayCutout != null) {
                safeInsetRect[displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight] = displayCutout.safeInsetBottom
            }
            return safeInsetRect
        }
    }

    init {
        mResources = mContext.resources
        mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mDisplayMetrics = DisplayMetrics()
        mFloatingViewListener = listener
        mFloatingViewRect = Rect()
        mTrashViewRect = Rect()
        mIsMoveAccept = false
        mDisplayMode = DISPLAY_MODE_HIDE_FULLSCREEN
        mSafeInsetRect = Rect()
        mFloatingViewList = ArrayList()
        mFullscreenObserverView = FullscreenObserverView(mContext, this)
        mTrashView = TrashView(mContext)
    }
}