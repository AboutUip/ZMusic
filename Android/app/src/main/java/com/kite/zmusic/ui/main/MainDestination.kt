package com.kite.zmusic.ui.main

/**
 * 主导航模块（后续可接真实页面与路由）。
 */
enum class MainDestination(
    val shortLabel: String,
    val titleZh: String,
    val blurb: String,
    val glyph: String,
) {
    Home(
        shortLabel = "HUB",
        titleZh = "发现",
        blurb = "推荐与浏览",
        glyph = "◈",
    ),
    Library(
        shortLabel = "LIB",
        titleZh = "我的",
        blurb = "歌单与收藏",
        glyph = "▣",
    ),
    Search(
        shortLabel = "FIND",
        titleZh = "搜索",
        blurb = "曲目与歌单",
        glyph = "⌕",
    ),
    Settings(
        shortLabel = "SYS",
        titleZh = "系统",
        blurb = "设置与账号",
        glyph = "⚙",
    ),
}
