package com.kite.zmusic.ui.common

/**
 * 预测性返回的纯逻辑，不依赖 Compose。
 *
 * 根因（见用户视频）：
 * 1. 父子两层 handler 同时 enabled，一次手势 pop 两次。
 * 2. 上一层按中心缩到 0.96，露边缝。
 * 3. 手势中途改 swipeEdge，左右对打。
 */
internal class PredictiveBackClaimStack {
    private val ids = mutableListOf<Any>()

    fun claim(id: Any) {
        if (id !in ids) ids.add(id)
    }

    fun release(id: Any) {
        ids.remove(id)
    }

    fun isTop(id: Any): Boolean = ids.lastOrNull() == id

    fun contains(id: Any): Boolean = id in ids

    val size: Int get() = ids.size

    fun debug(): String = ids.joinToString(",") { it.toString() }
}

internal const val BACK_EDGE_LEFT = 0
internal const val BACK_EDGE_RIGHT = 1

internal enum class PredictiveBackAxis {
    Horizontal,
    Vertical,
}

internal data class PredictiveBackLayerParams(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val originX: Float,
    val originY: Float,
    val alpha: Float,
)

internal fun lockSwipeEdge(locked: Int?, incoming: Int): Int = locked ?: incoming

internal fun predictiveHandlerEnabled(
    settingOn: Boolean,
    wantsBack: Boolean,
    isTop: Boolean,
): Boolean = settingOn && wantsBack && isTop

internal fun instantHandlerEnabled(
    settingOn: Boolean,
    wantsBack: Boolean,
    isTop: Boolean,
): Boolean = !settingOn && wantsBack && isTop

/**
 * 只平移、不缩放。缩放会让当前页和底下页之间露出主题底色（侧面缝隙）。
 * 进度 1 时移出整屏，松手提交后由页面卸载，不依赖残留 transform。
 */
internal fun predictiveBackLayerParams(
    progress: Float,
    swipeEdge: Int,
    axis: PredictiveBackAxis,
    width: Float,
    height: Float,
): PredictiveBackLayerParams {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) {
        return PredictiveBackLayerParams(0f, 0f, 1f, 1f, 0.5f, 0.5f, 1f)
    }
    val alpha = 1f - 0.06f * p
    return when (axis) {
        PredictiveBackAxis.Horizontal -> {
            val dir = if (swipeEdge == BACK_EDGE_RIGHT) -1f else 1f
            PredictiveBackLayerParams(
                translationX = dir * p * width,
                translationY = 0f,
                scaleX = 1f,
                scaleY = 1f,
                originX = if (dir > 0f) 0f else 1f,
                originY = 0.5f,
                alpha = alpha,
            )
        }
        PredictiveBackAxis.Vertical -> PredictiveBackLayerParams(
            translationX = 0f,
            translationY = p * height,
            scaleX = 1f,
            scaleY = 1f,
            originX = 0.5f,
            originY = 0f,
            alpha = alpha,
        )
    }
}

/** 底下那一层保持铺满，不要跟着缩小。 */
internal fun predictiveBackBehindScale(progress: Float): Float {
    progress.coerceIn(0f, 1f)
    return 1f
}
