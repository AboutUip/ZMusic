package com.kite.zmusic.ui.settings

internal enum class WallpaperHelpTopic {
    Guide,
    Coverage,
    Canvas,
    Chrome,
    Profile,
    Limits,
}

internal data class WallpaperHelpSection(
    val heading: String,
    val body: String,
)

internal data class WallpaperHelpDoc(
    val title: String,
    val sections: List<WallpaperHelpSection>,
)

internal fun wallpaperHelp(topic: WallpaperHelpTopic): WallpaperHelpDoc = when (topic) {
    WallpaperHelpTopic.Guide -> WallpaperHelpDoc(
        title = "自定义背景说明",
        sections = listOf(
            WallpaperHelpSection(
                "这是什么",
                "打开后，主界面可以用你自己的图当底，而不是纯色主题底。浅色 / 深色只改字和卡片颜色，和这张图不冲突。弹窗、灵动岛、播放页故意不铺，避免把字和控件埋进图里。",
            ),
            WallpaperHelpSection(
                "建议怎么用",
                "1. 先打开「启用自定义背景」。\n2. 默认只铺主页。用相册选一张通用图，在预览里拖、捏到合适。\n3. 若某个页面要单独一张图或不同构图，点对应场景芯片，再选图。没单独配的勾选页会继承通用图。\n4. 横屏、竖屏是两套构图，切「横屏 / 竖屏」分别调，互不影响。\n5. 调好可点「锁定」，避免误触改掉位置。",
            ),
            WallpaperHelpSection(
                "为什么这么复杂",
                "每页比例不一样：主页有 Banner，个人页有头图，设置是列表，横屏还有左侧栏。一张图硬拉满会裁掉主体，所以才有「场景 + 横竖屏 + 构图」。若只想简单一点：只勾主页、只用通用图即可。",
            ),
        ) + wallpaperHelp(WallpaperHelpTopic.Limits).sections,
    )
    WallpaperHelpTopic.Coverage -> WallpaperHelpDoc(
        title = "铺在哪些页面",
        sections = listOf(
            WallpaperHelpSection(
                "覆盖是什么",
                "开关管「这套背景开不开」。勾选管「哪些页面真的铺图」。没勾选的页面仍用主题底色，构图还保存在本页，以后勾上立刻生效。",
            ),
            WallpaperHelpSection(
                "通用和分场景",
                "「通用」是默认图。某页勾了覆盖、但自己没选图，就用通用。某页自己选了图，只用自己的，不再继承。清除某页的图后，若通用还在，会重新继承。",
            ),
            WallpaperHelpSection(
                "默认",
                "新安装默认只铺主页。点「全部覆盖」会给所有可铺页面都开上；点「恢复默认」回到只铺主页。个人页勾选后，不再用网易云资料背景或你在用户空间里设的那张图。",
            ),
        ),
    )
    WallpaperHelpTopic.Canvas -> WallpaperHelpDoc(
        title = "预览与构图",
        sections = listOf(
            WallpaperHelpSection(
                "画布怎么算",
                "缩放 1 表示整张图刚好放进屏幕（可能上下或左右留边）。小于 1 图更小、露底色；大于 1 放大后再按位置裁切。拖动是在对齐图的左/上到右/下，和当前缩放无关。",
            ),
            WallpaperHelpSection(
                "切场景会丢吗",
                "不会。每个「场景 × 横竖屏」单独记住缩放和位置。切走再切回来还在。请先选对芯片再拖，别在通用里调完以为个人页也会变。",
            ),
            WallpaperHelpSection(
                "锁定 / 清除",
                "锁定后不能拖、不能换图，防止误触。清除只清当前场景当前方向的图；通用被清掉后，所有靠继承的页面会暂时回到主题底色。",
            ),
        ),
    )
    WallpaperHelpTopic.Chrome -> WallpaperHelpDoc(
        title = "组件边界",
        sections = listOf(
            WallpaperHelpSection(
                "三种边界",
                "纯色：卡片不透明，字最清楚。磨砂：卡片半透，能隐约看到底图。液态玻璃：折射采样，最吃性能，主页和功能页不改组件形状。只对已铺背景的页面生效。",
            ),
            WallpaperHelpSection(
                "和个人页的关系",
                "个人页只改下面的歌单列表边界，不改编头图。未勾选个人页时，这里的设置对个人页无效。",
            ),
        ),
    )
    WallpaperHelpTopic.Profile -> WallpaperHelpDoc(
        title = "个人页背景",
        sections = listOf(
            WallpaperHelpSection(
                "两套背景不要混",
                "未勾选「个人」时：用资料页背景图，或你在用户空间里换的本地图。勾选「个人」后：改用这里的自定义背景，资料图和用户空间图都不再显示。",
            ),
            WallpaperHelpSection(
                "翻页闪一下",
                "主页 / 功能 / 个人 是左右滑的三页。若三页构图或是否铺图不一致，滑动时各页应各画各的。若仍闪，把 logcat 过滤 ZMusicWallpaper 发给开发者。",
            ),
        ),
    )
    WallpaperHelpTopic.Limits -> WallpaperHelpDoc(
        title = "不会铺的地方",
        sections = listOf(
            WallpaperHelpSection(
                "永远不铺",
                "系统弹窗、灵动岛通知、播放页（含迷你条后面的全屏播放器）、以及本编辑页自己。编辑页留纯底，是为了让预览里的构图看得清。",
            ),
            WallpaperHelpSection(
                "常见误会",
                "开了开关但主页没图：还没选通用图，或主页没勾覆盖。某页突然变纯色：那页没勾选，或继承的通用图被清了。横屏对不齐：要切到横屏芯片单独构图，竖屏那套不会自动套过来。",
            ),
        ),
    )
}
