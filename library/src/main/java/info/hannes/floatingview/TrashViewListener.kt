package info.hannes.floatingview

internal interface TrashViewListener {
    fun onUpdateActionTrashIcon()
    fun onTrashAnimationStarted(animationCode: Int)
    fun onTrashAnimationEnd(animationCode: Int)
}