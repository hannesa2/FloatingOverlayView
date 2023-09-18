package info.hannes.floatingview

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager

/**
 * http://stackoverflow.com/questions/18551135/receiving-hidden-status-bar-entering-a-full-screen-activity-event-on-a-service/19201933#19201933
 */
internal class FullscreenObserverView(context: Context?, private val screenChangedListener: ScreenChangedListener?) : View(context), OnGlobalLayoutListener, OnSystemUiVisibilityChangeListener {
    val windowLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private var lastUiVisibility: Int
    private val windowRect: Rect

    init {
        windowLayoutParams.width = 1
        windowLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        windowLayoutParams.type = OVERLAY_TYPE
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowLayoutParams.format = PixelFormat.TRANSLUCENT
        windowRect = Rect()
        lastUiVisibility = NO_LAST_VISIBILITY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(this)
        setOnSystemUiVisibilityChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)
        setOnSystemUiVisibilityChangeListener(null)
        super.onDetachedFromWindow()
    }

    override fun onGlobalLayout() {
        if (screenChangedListener != null) {
            getWindowVisibleDisplayFrame(windowRect)
            screenChangedListener.onScreenChanged(windowRect, lastUiVisibility)
        }
    }

    override fun onSystemUiVisibilityChange(visibility: Int) {
        lastUiVisibility = visibility
        if (screenChangedListener != null) {
            getWindowVisibleDisplayFrame(windowRect)
            screenChangedListener.onScreenChanged(windowRect, visibility)
        }
    }

    companion object {
        const val NO_LAST_VISIBILITY = -1
        private var OVERLAY_TYPE = 0

        init {
            OVERLAY_TYPE = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        }
    }
}