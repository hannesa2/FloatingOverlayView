package info.hannes.floatingview

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.*
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationUpdateListener
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.math.*

/**
 * http://stackoverflow.com/questions/18503050/how-to-create-draggabble-system-alert-in-android
 */
internal class FloatingView(context: Context, val docking: Boolean) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val windowLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private var velocityTracker: VelocityTracker? = null
    private var viewConfiguration: ViewConfiguration? = null
    private var moveThreshold = 0f
    private var maximumFlingVelocity = 0f
    private var maximumXVelocity = 0f
    private var maximumYVelocity = 0f
    private var throwMoveThreshold = 0f
    private val metrics: DisplayMetrics = DisplayMetrics()
    private var touchDownTime: Long = 0
    private var screenTouchDownX = 0f
    private var screenTouchDownY = 0f
    private var moveAccept = false
    private var screenTouchX = 0f
    private var screenTouchY = 0f
    private var localTouchX = 0f
    private var localTouchY = 0f
    private var initX = 0
    private var initY = 0
    private var initialAnimationRunning = false
    private var animateInitialMove = false
    private val baseStatusBarHeight: Int
    private var baseStatusBarRotatedHeight = 0
    private var statusBarHeight = 0
    private var baseNavigationBarHeight = 0

    /**
     * Navigation bar's height
     * Placed bottom on the screen(tablet)
     * Or placed vertically on the screen(phone)
     */
    private var baseNavigationBarRotatedHeight = 0
    private var navigationBarVerticalOffset = 0
    private var navigationBarHorizontalOffset = 0
    private var touchXOffset = 0
    private var touchYOffset = 0
    private var moveEdgeAnimator: ValueAnimator? = null
    private val moveEdgeInterpolator: TimeInterpolator
    private val moveLimitRect1: Rect
    private val positionLimitRect: Rect
    private var draggable = false
    var shape = 0f
    private val animationHandler: FloatingAnimationHandler
    private val longPressHandler: LongPressHandler
    private var overMargin = 0
    private var overMarginX = 0
    private var overMarginY = 0
    private var onTouchListener: OnTouchListener? = null
    private var longPressed = false
    private var moveDirection: Int

    /**
     * Use dynamic physics-based animations or not
     */
    private var usePhysics: Boolean
    private val tablet: Boolean
    private var rotation: Int

    /**
     * Cutout safe inset rect(Same as FloatingViewManager's mSafeInsetRect)
     */
    private val safeInsetRect: Rect

    init {
        windowManager.defaultDisplay.getMetrics(metrics)
        windowLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        windowLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        windowLayoutParams.type = OVERLAY_TYPE
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        windowLayoutParams.format = PixelFormat.TRANSLUCENT
        windowLayoutParams.gravity = Gravity.LEFT or Gravity.BOTTOM
        animationHandler = FloatingAnimationHandler(this)
        longPressHandler = LongPressHandler(this)
        moveEdgeInterpolator = OvershootInterpolator(MOVE_TO_EDGE_OVERSHOOT_TENSION)
        moveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT
        usePhysics = false
        val resources = context.resources
        tablet = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
        rotation = windowManager.defaultDisplay.rotation
        moveLimitRect1 = Rect()
        positionLimitRect = Rect()
        safeInsetRect = Rect()
        baseStatusBarHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height")
        // Check landscape resource id
        val statusBarLandscapeResId = resources.getIdentifier("status_bar_height_landscape", "dimen", "android")
        baseStatusBarRotatedHeight = if (statusBarLandscapeResId > 0) {
            getSystemUiDimensionPixelSize(resources, "status_bar_height_landscape")
        } else {
            baseStatusBarHeight
        }

        // Init physics-based animation properties
        updateViewConfiguration()

        // Detect NavigationBar
        if (hasSoftNavigationBar()) {
            baseNavigationBarHeight = getSystemUiDimensionPixelSize(resources, "navigation_bar_height")
            val resName = if (tablet) "navigation_bar_height_landscape" else "navigation_bar_width"
            baseNavigationBarRotatedHeight = getSystemUiDimensionPixelSize(resources, resName)
        } else {
            baseNavigationBarHeight = 0
            baseNavigationBarRotatedHeight = 0
        }
        viewTreeObserver.addOnPreDrawListener(this)
    }

    private fun hasSoftNavigationBar(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val realDisplayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(realDisplayMetrics)
            return realDisplayMetrics.heightPixels > metrics.heightPixels || realDisplayMetrics.widthPixels > metrics.widthPixels
        }

        // old device check flow
        // Navigation bar exists (config_showNavigationBar is true, or both the menu key and the back key are not exists)
        val context = context
        val resources = context.resources
        val hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey()
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        val showNavigationBarResId = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        val hasNavigationBarConfig = showNavigationBarResId != 0 && resources.getBoolean(showNavigationBarResId)
        return hasNavigationBarConfig || !hasMenuKey && !hasBackKey
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshLimitRect()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewConfiguration()
        refreshLimitRect()
    }

    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        if (initX == DEFAULT_X) {
            initX = 0
        }
        if (initY == DEFAULT_Y) {
            initY = metrics.heightPixels - statusBarHeight - measuredHeight
        }
        windowLayoutParams.x = initX
        windowLayoutParams.y = initY
        if (moveDirection == FloatingViewManager.MOVE_DIRECTION_NONE) {
            moveTo(initX, initY, initX, initY, false)
        } else {
            initialAnimationRunning = true
            moveToEdge(initX, initY, animateInitialMove)
        }
        draggable = true
        updateViewLayout()
        return true
    }

    fun onUpdateSystemLayout(isHideStatusBar: Boolean, isHideNavigationBar: Boolean, isPortrait: Boolean, windowRect: Rect) {
        updateStatusBarHeight(isHideStatusBar, isPortrait)
        updateTouchXOffset(isHideNavigationBar, windowRect.left)
        touchYOffset = if (isPortrait) safeInsetRect.top else 0
        updateNavigationBarOffset(isHideNavigationBar, isPortrait, windowRect)
        refreshLimitRect()
    }

    private fun updateStatusBarHeight(isHideStatusBar: Boolean, isPortrait: Boolean) {
        if (isHideStatusBar) {
            // 1.(No Cutout)No StatusBar(=0)
            // 2.(Has Cutout)StatusBar is not included in mMetrics.heightPixels (=0)
            statusBarHeight = 0
            return
        }

        // Has Cutout
        val hasTopCutout = safeInsetRect.top != 0
        if (hasTopCutout) {
            statusBarHeight = if (isPortrait) {
                0
            } else {
                baseStatusBarRotatedHeight
            }
            return
        }

        // No cutout
        statusBarHeight = if (isPortrait) {
            baseStatusBarHeight
        } else {
            baseStatusBarRotatedHeight
        }
    }

    private fun updateTouchXOffset(isHideNavigationBar: Boolean, windowLeftOffset: Int) {
        val hasBottomCutout = safeInsetRect.bottom != 0
        if (hasBottomCutout) {
            touchXOffset = windowLeftOffset
            return
        }

        // No cutout
        // touch X offset(navigation bar is displayed and it is on the left side of the device)
        touchXOffset = if (!isHideNavigationBar && windowLeftOffset > 0) baseNavigationBarRotatedHeight else 0
    }

    private fun updateNavigationBarOffset(isHideNavigationBar: Boolean, isPortrait: Boolean, windowRect: Rect) {
        var currentNavigationBarHeight = 0
        var currentNavigationBarWidth = 0
        var navigationBarVerticalDiff = 0
        val hasSoftNavigationBar = hasSoftNavigationBar()
        // auto hide navigation bar(Galaxy S8, S9 and so on.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val realDisplayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(realDisplayMetrics)
            currentNavigationBarHeight = realDisplayMetrics.heightPixels - windowRect.bottom
            currentNavigationBarWidth = realDisplayMetrics.widthPixels - metrics.widthPixels
            navigationBarVerticalDiff = baseNavigationBarHeight - currentNavigationBarHeight
        }
        if (!isHideNavigationBar) {
            navigationBarVerticalOffset = if (navigationBarVerticalDiff != 0 && baseNavigationBarHeight == 0 ||
                    !hasSoftNavigationBar && baseNavigationBarHeight != 0) {
                if (hasSoftNavigationBar) {
                    // 1.auto hide mode -> show mode
                    // 2.show mode -> auto hide mode -> home
                    0
                } else {
                    // show mode -> home
                    -currentNavigationBarHeight
                }
            } else {
                // normal device
                0
            }
            navigationBarHorizontalOffset = 0
            return
        }

        // If the portrait, is displayed at the bottom of the screen
        if (isPortrait) {
            // auto hide navigation bar
            navigationBarVerticalOffset = if (!hasSoftNavigationBar && baseNavigationBarHeight != 0) {
                0
            } else {
                baseNavigationBarHeight
            }
            navigationBarHorizontalOffset = 0
            return
        }

        // If it is a Tablet, it will appear at the bottom of the screen.
        // If it is Phone, it will appear on the side of the screen
        if (tablet) {
            navigationBarVerticalOffset = baseNavigationBarRotatedHeight
            navigationBarHorizontalOffset = 0
        } else {
            navigationBarVerticalOffset = 0
            navigationBarHorizontalOffset = if (!hasSoftNavigationBar && baseNavigationBarRotatedHeight != 0) {
                0
            } else if (hasSoftNavigationBar && baseNavigationBarRotatedHeight == 0) {
                currentNavigationBarWidth
            } else {
                baseNavigationBarRotatedHeight
            }
        }
    }

    private fun updateViewConfiguration() {
        viewConfiguration = ViewConfiguration.get(context)
        moveThreshold = viewConfiguration!!.scaledTouchSlop.toFloat()
        maximumFlingVelocity = viewConfiguration!!.scaledMaximumFlingVelocity.toFloat()
        maximumXVelocity = maximumFlingVelocity / MAX_X_VELOCITY_SCALE_DOWN_VALUE
        maximumYVelocity = maximumFlingVelocity / MAX_Y_VELOCITY_SCALE_DOWN_VALUE
        throwMoveThreshold = maximumFlingVelocity / THROW_THRESHOLD_SCALE_DOWN_VALUE
    }

    /**
     * Update the PositionLimitRect and MoveLimitRect according to the screen size change.
     */
    private fun refreshLimitRect() {
        cancelAnimation()
        val oldPositionLimitWidth = positionLimitRect.width()
        val oldPositionLimitHeight = positionLimitRect.height()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = measuredWidth
        val height = measuredHeight
        val newScreenWidth = metrics.widthPixels
        val newScreenHeight = metrics.heightPixels
        moveLimitRect1[-width, -height * 2, newScreenWidth + width + navigationBarHorizontalOffset] = newScreenHeight + height + navigationBarVerticalOffset
        positionLimitRect[-overMargin - overMarginX, 0, newScreenWidth - width + overMargin + overMarginY + navigationBarHorizontalOffset] = newScreenHeight - statusBarHeight - height + navigationBarVerticalOffset

        // Initial animation stop when the device rotates
        val newRotation = windowManager.defaultDisplay.rotation
        if (animateInitialMove && rotation != newRotation) {
            initialAnimationRunning = false
        }

        // When animation is running and the device is not rotating
        if (initialAnimationRunning && rotation == newRotation) {
            moveToEdge(windowLayoutParams.x, windowLayoutParams.y, true)
        } else {
            // If there is a screen change during the operation, move to the appropriate position
            if (moveAccept) {
                moveToEdge(windowLayoutParams.x, windowLayoutParams.y, false)
            } else {
                val newX = (windowLayoutParams.x * positionLimitRect.width() / oldPositionLimitWidth.toFloat() + 0.5f).toInt()
                val goalPositionX = Math.min(Math.max(positionLimitRect.left, newX), positionLimitRect.right)
                val newY = (windowLayoutParams.y * positionLimitRect.height() / oldPositionLimitHeight.toFloat() + 0.5f).toInt()
                val goalPositionY = Math.min(Math.max(positionLimitRect.top, newY), positionLimitRect.bottom)
                moveTo(windowLayoutParams.x, windowLayoutParams.y, goalPositionX, goalPositionY, false)
            }
        }
        rotation = newRotation
    }

    override fun onDetachedFromWindow() {
        if (moveEdgeAnimator != null) {
            moveEdgeAnimator!!.removeAllUpdateListeners()
        }
        super.onDetachedFromWindow()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return dispatchTouchEvent(event, true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return dispatchTouchEvent(event, false)
    }

    private fun dispatchTouchEvent(event: MotionEvent, isOnInterceptTouchEvent: Boolean): Boolean {
        if (visibility != View.VISIBLE) {
            return false
        }
        if (!draggable) {
            return false
        }

        // Block while initial display animation is running
        if (initialAnimationRunning) {
            return false
        }
        screenTouchX = event.rawX
        screenTouchY = event.rawY
        val action = event.action
        var isWaitForMoveToEdge = false
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAnimation()
            screenTouchDownX = screenTouchX
            screenTouchDownY = screenTouchY
            localTouchX = event.x
            localTouchY = event.y
            moveAccept = false
            setScale(SCALE_PRESSED)
            if (velocityTracker == null) {
                // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                velocityTracker = VelocityTracker.obtain()
            } else {
                // Reset the velocity tracker back to its initial state.
                velocityTracker!!.clear()
            }
            animationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
            animationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            animationHandler.sendAnimationMessage(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            longPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            longPressHandler.sendEmptyMessageDelayed(LongPressHandler.LONG_PRESSED, LONG_PRESS_TIMEOUT.toLong())
            touchDownTime = event.downTime
            addMovement(event)
            initialAnimationRunning = false
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (moveAccept) {
                longPressed = false
                longPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            }
            if (touchDownTime != event.downTime) {
                return !isOnInterceptTouchEvent
            }
            if (!moveAccept && Math.abs(screenTouchX - screenTouchDownX) < moveThreshold && Math.abs(screenTouchY - screenTouchDownY) < moveThreshold) {
                return !isOnInterceptTouchEvent
            }
            moveAccept = true
            animationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
            // compute offset and restore
            addMovement(event)
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // compute velocity tracker
            if (velocityTracker != null) {
                velocityTracker!!.computeCurrentVelocity(CURRENT_VELOCITY_UNITS)
            }
            val tmpIsLongPressed = longPressed
            longPressed = false
            longPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            if (touchDownTime != event.downTime) {
                return true
            }
            animationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            setScale(SCALE_NORMAL)

            // destroy VelocityTracker (#103)
            if (!moveAccept && velocityTracker != null) {
                velocityTracker!!.recycle()
                velocityTracker = null
            }

            // When ACTION_UP is done (when not pressed or moved)
            if (action == MotionEvent.ACTION_UP && !tmpIsLongPressed && !moveAccept) {
            } else {
                // Make a move after checking whether it is finished or not
                isWaitForMoveToEdge = true
            }
        }
        if (onTouchListener != null) {
            onTouchListener!!.onTouch(this, event)
        }

        // Lazy execution of moveToEdge
        if (isWaitForMoveToEdge && animationHandler.state != FloatingViewState.STATE_FINISHING) {
            // include device rotation
            if (docking)
                moveToEdge(true)
            if (velocityTracker != null) {
                velocityTracker!!.recycle()
                velocityTracker = null
            }
        }
        return !isOnInterceptTouchEvent || moveAccept
    }

    private fun addMovement(event: MotionEvent) {
        Timber.d("()")
        val deltaX = event.rawX - event.x
        val deltaY = event.rawY - event.y
        event.offsetLocation(deltaX, deltaY)
        velocityTracker!!.addMovement(event)
        event.offsetLocation(-deltaX, -deltaY)
    }

    private fun onLongClick() {
        longPressed = true
        val size = childCount
        for (i in 0 until size) {
            getChildAt(i).performLongClick()
        }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != View.VISIBLE) {
            cancelLongPress()
            setScale(SCALE_NORMAL)
            if (moveAccept) {
                moveToEdge(false)
            }
            animationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            longPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
        }
        super.setVisibility(visibility)
    }

    override fun setOnTouchListener(listener: OnTouchListener) {
        onTouchListener = listener
    }

    private fun moveToEdge(withAnimation: Boolean) {
        Timber.d("() withAnimation=$withAnimation")
        val currentX = xByTouch
        val currentY = yByTouch
        moveToEdge(currentX, currentY, withAnimation)
    }

    private fun moveToEdge(startX: Int, startY: Int, withAnimation: Boolean) {
        Timber.d("() startX:$startX startY=$startY withAnimation=$withAnimation")
        val goalPositionX = getGoalPositionX(startX, startY)
        val goalPositionY = getGoalPositionY(startX, startY)
        moveTo(startX, startY, goalPositionX, goalPositionY, withAnimation)
    }

    private fun moveTo(currentX: Int, currentY: Int, goalPositionXVal: Int, goalPositionYVal: Int, withAnimation: Boolean) {
        Timber.d("()")
        var goalPositionX = goalPositionXVal
        var goalPositionY = goalPositionYVal
        goalPositionX = min(max(positionLimitRect.left, goalPositionX), positionLimitRect.right)
        goalPositionY = min(max(positionLimitRect.top, goalPositionY), positionLimitRect.bottom)
        if (withAnimation) {
            // Use physics animation
            val usePhysicsAnimation = usePhysics && velocityTracker != null && moveDirection != FloatingViewManager.MOVE_DIRECTION_NEAREST
            if (usePhysicsAnimation) {
                startPhysicsAnimation(goalPositionX, currentY)
            } else {
                startObjectAnimation(currentX, currentY, goalPositionX, goalPositionY)
            }
        } else {
            if (windowLayoutParams.x != goalPositionX || windowLayoutParams.y != goalPositionY) {
                windowLayoutParams.x = goalPositionX
                windowLayoutParams.y = goalPositionY
                updateViewLayout()
            }
        }
        localTouchX = 0f
        localTouchY = 0f
        screenTouchDownX = 0f
        screenTouchDownY = 0f
        moveAccept = false
    }

    /**
     * Start Physics-based animation
     *
     * @param goalPositionX goal position X coordinate
     * @param currentY      current Y coordinate
     */
    private fun startPhysicsAnimation(goalPositionX: Int, currentY: Int) {
        Timber.d("()")
        // start X coordinate animation
        val containsLimitRectWidth = windowLayoutParams.x < positionLimitRect.right && windowLayoutParams.x > positionLimitRect.left
        // If MOVE_DIRECTION_NONE, play fling animation
        if (moveDirection == FloatingViewManager.MOVE_DIRECTION_NONE && containsLimitRectWidth) {
            val velocityX = min(max(velocityTracker!!.xVelocity, -maximumXVelocity), maximumXVelocity)
            startFlingAnimationX(velocityX)
        } else {
            startSpringAnimationX(goalPositionX)
        }

        // start Y coordinate animation
        val containsLimitRectHeight = windowLayoutParams.y < positionLimitRect.bottom && windowLayoutParams.y > positionLimitRect.top
        val velocityY = -min(max(velocityTracker!!.yVelocity, -maximumYVelocity), maximumYVelocity)
        if (containsLimitRectHeight) {
            startFlingAnimationY(velocityY)
        } else {
            startSpringAnimationY(currentY, velocityY)
        }
    }

    private fun startObjectAnimation(currentX: Int, currentY: Int, goalPositionX: Int, goalPositionY: Int) {
        Timber.d("()")
        if (goalPositionX == currentX) {
            //to move only y coord
            moveEdgeAnimator = ValueAnimator.ofInt(currentY, goalPositionY)
            moveEdgeAnimator?.addUpdateListener { animation ->
                windowLayoutParams.y = (animation.animatedValue as Int)
                updateViewLayout()
                updateInitAnimation(animation)
            }
        } else {
            // To move only x coord (to left or right)
            windowLayoutParams.y = goalPositionY
            moveEdgeAnimator = ValueAnimator.ofInt(currentX, goalPositionX)
            moveEdgeAnimator?.addUpdateListener { animation ->
                windowLayoutParams.x = (animation.animatedValue as Int)
                updateViewLayout()
                updateInitAnimation(animation)
            }
        }
        moveEdgeAnimator?.apply {
            duration = MOVE_TO_EDGE_DURATION
            interpolator = moveEdgeInterpolator
            start()
        }
    }

    private fun startSpringAnimationX(goalPositionX: Int) {
        Timber.d("()")
        // springX
        val springX = SpringForce(goalPositionX.toFloat())
        springX.dampingRatio = ANIMATION_SPRING_X_DAMPING_RATIO
        springX.stiffness = ANIMATION_SPRING_X_STIFFNESS
        // springAnimation
        val springAnimationX = SpringAnimation(FloatValueHolder())
        springAnimationX.setStartVelocity(velocityTracker!!.xVelocity)
        springAnimationX.setStartValue(windowLayoutParams.x.toFloat())
        springAnimationX.spring = springX
        springAnimationX.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
        springAnimationX.addUpdateListener(OnAnimationUpdateListener { animation, value, velocity ->
            val x = value.roundToInt()
            // Not moving, or the touch operation is continuing
            if (windowLayoutParams.x == x || velocityTracker != null) {
                return@OnAnimationUpdateListener
            }
            // update x coordinate
            windowLayoutParams.x = x
            updateViewLayout()
        })
        springAnimationX.start()
    }

    private fun startSpringAnimationY(currentY: Int, velocityY: Float) {
        Timber.d("()")
        // Create SpringForce
        val springY = SpringForce(if (currentY < metrics.heightPixels / 2) positionLimitRect.top.toFloat() else positionLimitRect.bottom.toFloat())
        springY.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        springY.stiffness = SpringForce.STIFFNESS_LOW

        // Create SpringAnimation
        val springAnimationY = SpringAnimation(FloatValueHolder())
        springAnimationY.setStartVelocity(velocityY)
        springAnimationY.setStartValue(windowLayoutParams.y.toFloat())
        springAnimationY.spring = springY
        springAnimationY.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
        springAnimationY.addUpdateListener(OnAnimationUpdateListener { animation, value, velocity ->
            val y = value.roundToInt()
            // Not moving, or the touch operation is continuing
            if (windowLayoutParams.y == y || velocityTracker != null) {
                return@OnAnimationUpdateListener
            }
            // update y coordinate
            windowLayoutParams.y = y
            updateViewLayout()
        })
        springAnimationY.start()
    }

    private fun startFlingAnimationX(velocityX: Float) {
        Timber.d("()")
        val flingAnimationX = FlingAnimation(FloatValueHolder())
        flingAnimationX.setStartVelocity(velocityX)
        flingAnimationX.setMaxValue(positionLimitRect.right.toFloat())
        flingAnimationX.setMinValue(positionLimitRect.left.toFloat())
        flingAnimationX.setStartValue(windowLayoutParams.x.toFloat())
        flingAnimationX.friction = ANIMATION_FLING_X_FRICTION
        flingAnimationX.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
        flingAnimationX.addUpdateListener(OnAnimationUpdateListener { animation, value, velocity ->
            val x = value.roundToInt()
            // Not moving, or the touch operation is continuing
            if (windowLayoutParams.x == x || velocityTracker != null) {
                return@OnAnimationUpdateListener
            }
            // update y coordinate
            windowLayoutParams.x = x
            updateViewLayout()
        })
        flingAnimationX.start()
    }

    private fun startFlingAnimationY(velocityY: Float) {
        Timber.d("()")
        val flingAnimationY = FlingAnimation(FloatValueHolder())
        flingAnimationY.setStartVelocity(velocityY)
        flingAnimationY.setMaxValue(positionLimitRect.bottom.toFloat())
        flingAnimationY.setMinValue(positionLimitRect.top.toFloat())
        flingAnimationY.setStartValue(windowLayoutParams.y.toFloat())
        flingAnimationY.friction = ANIMATION_FLING_Y_FRICTION
        flingAnimationY.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
        flingAnimationY.addUpdateListener(OnAnimationUpdateListener { animation, value, velocity ->
            val y = value.roundToInt()
            // Not moving, or the touch operation is continuing
            if (windowLayoutParams.y == y || velocityTracker != null) {
                return@OnAnimationUpdateListener
            }
            // update y coordinate
            windowLayoutParams.y = y
            updateViewLayout()
        })
        flingAnimationY.start()
    }

    /**
     * Check if it is attached to the Window and call WindowManager.updateLayout()
     */
    private fun updateViewLayout() {
        if (!ViewCompat.isAttachedToWindow(this)) {
            return
        }
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    /**
     * Update animation initialization flag
     *
     * @param animation [ValueAnimator]
     */
    private fun updateInitAnimation(animation: ValueAnimator) {
        if (animateInitialMove && animation.duration <= animation.currentPlayTime) {
            initialAnimationRunning = false
        }
    }

    /**
     * Get the final point of movement (X coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of X coordinate
     */
    private fun getGoalPositionX(startX: Int, startY: Int): Int {
        var goalPositionX = startX

        // Move to left or right edges
        if (moveDirection == FloatingViewManager.MOVE_DIRECTION_DEFAULT) {
            val isMoveRightEdge = startX > (metrics.widthPixels - width) / 2
            goalPositionX = if (isMoveRightEdge) positionLimitRect.right else positionLimitRect.left
        } else if (moveDirection == FloatingViewManager.MOVE_DIRECTION_LEFT) {
            goalPositionX = positionLimitRect.left
        } else if (moveDirection == FloatingViewManager.MOVE_DIRECTION_RIGHT) {
            goalPositionX = positionLimitRect.right
        } else if (moveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            val distLeftRight = min(startX, positionLimitRect.width() - startX)
            val distTopBottom = min(startY, positionLimitRect.height() - startY)
            if (distLeftRight < distTopBottom) {
                val isMoveRightEdge = startX > (metrics.widthPixels - width) / 2
                goalPositionX = if (isMoveRightEdge) positionLimitRect.right else positionLimitRect.left
            }
        } else if (moveDirection == FloatingViewManager.MOVE_DIRECTION_THROWN) {
            goalPositionX = if (velocityTracker != null && velocityTracker!!.xVelocity > throwMoveThreshold) {
                positionLimitRect.right
            } else if (velocityTracker != null && velocityTracker!!.xVelocity < -throwMoveThreshold) {
                positionLimitRect.left
            } else {
                val isMoveRightEdge = startX > (metrics.widthPixels - width) / 2
                if (isMoveRightEdge) positionLimitRect.right else positionLimitRect.left
            }
        }
        return goalPositionX
    }

    /**
     * Get the final point of movement (Y coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of Y coordinate
     */
    private fun getGoalPositionY(startX: Int, startY: Int): Int {
        var goalPositionY = startY

        // Move to top/bottom/left/right edges
        if (moveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            val distLeftRight = min(startX, positionLimitRect.width() - startX)
            val distTopBottom = min(startY, positionLimitRect.height() - startY)
            if (distLeftRight >= distTopBottom) {
                val isMoveTopEdge = startY < (metrics.heightPixels - height) / 2
                goalPositionY = if (isMoveTopEdge) positionLimitRect.top else positionLimitRect.bottom
            }
        }
        return goalPositionY
    }

    private fun cancelAnimation() {
        Timber.d("()")
        if (moveEdgeAnimator != null && moveEdgeAnimator!!.isStarted) {
            moveEdgeAnimator!!.cancel()
            moveEdgeAnimator = null
        }
    }

    private fun setScale(newScale: Float) {
        Timber.d("()")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            val childCount = childCount
            for (i in 0 until childCount) {
                val targetView = getChildAt(i)
                targetView.scaleX = newScale
                targetView.scaleY = newScale
            }
        } else {
            scaleX = newScale
            scaleY = newScale
        }
    }

    fun setDraggable(isDraggable: Boolean) {
        Timber.d("()")
        draggable = isDraggable
    }

    fun setOverMargin(margin: Int) {
        Timber.d("()")
        overMargin = margin
    }

    fun setOverMarginX(margin: Int) {
        Timber.d("()")
        overMarginX = margin
    }

    fun setOverMarginY(margin: Int) {
        Timber.d("()")
        overMarginY = margin
    }

    fun setMoveDirection(moveDirectionVal: Int) {
        Timber.d("()")
        moveDirection = moveDirectionVal
    }

    /**
     * Use dynamic physics-based animations or not
     * Warning: Can not be used before API 16
     *
     * @param usePhysicsVal Setting this to false will revert to using a ValueAnimator (default is true)
     */
    fun usePhysics(usePhysicsVal: Boolean) {
        Timber.d("()")
        usePhysics = usePhysicsVal
    }

    fun setInitCoords(x: Int, y: Int) {
        Timber.d("()")
        initX = x
        initY = y
    }

    fun setAnimateInitialMove(animateInitialMove: Boolean) {
        Timber.d("()")
        this.animateInitialMove = animateInitialMove
    }

    fun getWindowDrawingRect(outRect: Rect) {
        val currentX = xByTouch
        val currentY = yByTouch
        outRect[currentX, currentY, currentX + width] = currentY + height
    }

    private val xByTouch: Int
        get() = (screenTouchX - localTouchX - touchXOffset).toInt()

    private val yByTouch: Int
        get() = (metrics.heightPixels + navigationBarVerticalOffset - (screenTouchY - localTouchY + height - touchYOffset)).toInt()

    fun setNormal() {
        Timber.d("()")
        animationHandler.state = FloatingViewState.STATE_NORMAL
        animationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
    }

    fun setIntersecting(centerX: Int, centerY: Int) {
        Timber.d("()")
        animationHandler.state = FloatingViewState.STATE_INTERSECTING
        animationHandler.updateTargetPosition(centerX.toFloat(), centerY.toFloat())
    }

    fun setFinishing() {
        Timber.d("()")
        animationHandler.state = FloatingViewState.STATE_FINISHING
        moveAccept = false
        visibility = View.GONE
    }

    val state: FloatingViewState
        get() = animationHandler.state

    /**
     * Set the cutout's safe inset area
     */
    fun setSafeInsetRect(safeInsetRectVal: Rect) {
        safeInsetRect.set(safeInsetRectVal)
    }

    internal class FloatingAnimationHandler(floatingView: FloatingView) : Handler(Looper.getMainLooper()) {
        private var startTime: Long = 0
        private var startX = 0f
        private var startY = 0f
        private var mState: FloatingViewState
        private var mIsChangeState = false
        private var touchPositionX = 0f
        private var touchPositionY = 0f
        private var mTargetPositionX = 0f
        private var mTargetPositionY = 0f
        private val mFloatingView: WeakReference<FloatingView> = WeakReference(floatingView)
        override fun handleMessage(msg: Message) {
            val floatingView = mFloatingView.get()
            if (floatingView == null) {
                removeMessages(ANIMATION_IN_TOUCH)
                return
            }
            val animationCode = msg.what
            val animationType = msg.arg1
            val params = floatingView.windowLayoutParams
            if (mIsChangeState || animationType == TYPE_FIRST) {
                startTime = if (mIsChangeState) SystemClock.uptimeMillis() else 0
                startX = params.x.toFloat()
                startY = params.y.toFloat()
                mIsChangeState = false
            }
            val elapsedTime = SystemClock.uptimeMillis() - startTime.toFloat()
            val trackingTargetTimeRate = min(elapsedTime / CAPTURE_DURATION_MILLIS, 1.0f)
            if (mState == FloatingViewState.STATE_NORMAL) {
                val basePosition = calcAnimationPosition(trackingTargetTimeRate)
                val moveLimitRect = floatingView.moveLimitRect1
                val targetPositionX = min(max(moveLimitRect.left, touchPositionX.toInt()), moveLimitRect.right).toFloat()
                val targetPositionY = min(max(moveLimitRect.top, touchPositionY.toInt()), moveLimitRect.bottom).toFloat()
                params.x = (startX + (targetPositionX - startX) * basePosition).toInt()
                params.y = (startY + (targetPositionY - startY) * basePosition).toInt()
                floatingView.updateViewLayout()
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            } else if (mState == FloatingViewState.STATE_INTERSECTING) {
                val basePosition = calcAnimationPosition(trackingTargetTimeRate)
                val targetPositionX = mTargetPositionX - floatingView.width / 2
                val targetPositionY = mTargetPositionY - floatingView.height / 2
                params.x = (startX + (targetPositionX - startX) * basePosition).toInt()
                params.y = (startY + (targetPositionY - startY) * basePosition).toInt()
                floatingView.updateViewLayout()
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            }
        }

        fun sendAnimationMessageDelayed(animation: Int, delayMillis: Long) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis)
        }

        fun sendAnimationMessage(animation: Int) {
            sendMessage(newMessage(animation, TYPE_FIRST))
        }

        fun updateTouchPosition(positionX: Float, positionY: Float) {
            touchPositionX = positionX
            touchPositionY = positionY
        }

        fun updateTargetPosition(centerX: Float, centerY: Float) {
            mTargetPositionX = centerX
            mTargetPositionY = centerY
        }

        var state: FloatingViewState
            get() = mState
            set(newState) {
                if (mState != newState) {
                    mIsChangeState = true
                }
                mState = newState
            }

        companion object {
            private const val ANIMATION_REFRESH_TIME_MILLIS = 10L
            private const val CAPTURE_DURATION_MILLIS = 300L
            const val ANIMATION_IN_TOUCH = 1
            private const val TYPE_FIRST = 1
            private const val TYPE_UPDATE = 2
            private fun calcAnimationPosition(timeRate: Float): Float {
                // y=0.55sin(8.0564x-π/2)+0.55
                return if (timeRate <= 0.4) {
                    (0.55 * sin(8.0564 * timeRate - Math.PI / 2) + 0.55).toFloat()
                } else {
                    (4 * (0.417 * timeRate - 0.341).pow(2.0) - 4 * (0.417 - 0.341).pow(2.0) + 1).toFloat()
                }
            }

            /**
             * @param animation ANIMATION_IN_TOUCH
             * @param type      TYPE_FIRST,TYPE_UPDATE
             * @return Message
             */
            private fun newMessage(animation: Int, type: Int): Message {
                val message = Message.obtain()
                message.what = animation
                message.arg1 = type
                return message
            }
        }

        init {
            mState = FloatingViewState.STATE_NORMAL
        }
    }

    internal class LongPressHandler(view: FloatingView) : Handler(Looper.getMainLooper()) {
        private val mFloatingView: WeakReference<FloatingView> = WeakReference(view)
        override fun handleMessage(msg: Message) {
            val view = mFloatingView.get()
            if (view == null) {
                removeMessages(LONG_PRESSED)
                return
            }
            view.onLongClick()
        }

        companion object {
            const val LONG_PRESSED = 0
        }

    }

    companion object {
        private const val SCALE_PRESSED = 0.9f
        private const val SCALE_NORMAL = 1.0f
        private const val MOVE_TO_EDGE_DURATION = 450L
        private const val MOVE_TO_EDGE_OVERSHOOT_TENSION = 1.25f
        private const val ANIMATION_SPRING_X_DAMPING_RATIO = 0.7f
        private const val ANIMATION_SPRING_X_STIFFNESS = 350f
        private const val ANIMATION_FLING_X_FRICTION = 1.7f
        private const val ANIMATION_FLING_Y_FRICTION = 1.7f
        private const val CURRENT_VELOCITY_UNITS = 1000
        private val LONG_PRESS_TIMEOUT = (1.5f * ViewConfiguration.getLongPressTimeout()).toInt()
        private const val MAX_X_VELOCITY_SCALE_DOWN_VALUE = 9f
        private const val MAX_Y_VELOCITY_SCALE_DOWN_VALUE = 8f
        private const val THROW_THRESHOLD_SCALE_DOWN_VALUE = 9f
        const val DEFAULT_X = Int.MIN_VALUE
        const val DEFAULT_Y = Int.MIN_VALUE
        const val DEFAULT_WIDTH = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DEFAULT_HEIGHT = ViewGroup.LayoutParams.WRAP_CONTENT
        private var OVERLAY_TYPE = 0
        private fun getSystemUiDimensionPixelSize(resources: Resources, resName: String): Int {
            var pixelSize = 0
            val resId = resources.getIdentifier(resName, "dimen", "android")
            if (resId > 0) {
                pixelSize = resources.getDimensionPixelSize(resId)
            }
            return pixelSize
        }

        init {
            OVERLAY_TYPE = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        }
    }
}