package com.kite.zmusic.ui.player

/**
 * 把连转累积角折成 [-180, 180] 的最短归正量，视觉上等于当前盘面朝向。
 */
internal fun vinylShortestUprightDeg(deg: Float): Float {
    if (!deg.isFinite()) return 0f
    val norm = ((deg % 360f) + 360f) % 360f
    return if (norm <= 180f) norm else norm - 360f
}

/**
 * 飞层黑胶旋转：迷你条封面始终是 0°。
 * 离场用冻结角 × 进度，沿最短路径收到正；进场 fromDeg=0 保持不转。
 * 横竖屏共用同一条飞层，避免切到飞层时瞬间归正。
 */
internal fun flightVinylRotationDeg(progress: Float, fromDeg: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (!fromDeg.isFinite() || kotlin.math.abs(fromDeg) < 0.4f) return 0f
    return fromDeg * p
}
