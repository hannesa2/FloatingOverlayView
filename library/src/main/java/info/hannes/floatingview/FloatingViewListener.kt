package info.hannes.floatingview

interface FloatingViewListener {
    fun onFinishFloatingView()
    fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int)
}