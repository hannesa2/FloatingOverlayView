package info.hannes.floatingview

import android.graphics.Rect

internal interface ScreenChangedListener {
    fun onScreenChanged(windowRect: Rect?, visibility: Int)
}