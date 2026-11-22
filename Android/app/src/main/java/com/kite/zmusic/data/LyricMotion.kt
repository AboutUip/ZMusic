package com.kite.zmusic.data

/**
 * 歌词行的入场或离场。
 * [Keep] 维持播放页现在的样子。
 * [Scatter] 只作为离场：播放结束的那句化成粒子飘散；滑动歌词时反向聚回成字。
 */
enum class LyricMotion {
    Keep,
    Scatter,
    ;

    companion object {
        fun fromStored(raw: String?): LyricMotion = when (raw) {
            "scatter" -> Scatter
            else -> Keep
        }

        fun stored(mode: LyricMotion): String = when (mode) {
            Keep -> "keep"
            Scatter -> "scatter"
        }
    }
}
