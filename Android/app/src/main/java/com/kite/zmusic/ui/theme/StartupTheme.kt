package com.kite.zmusic.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.kite.zmusic.data.AppAppearance

/**
 * 冷启动窗口底色：先跟系统盖一层，再叠用户已存外观，避免 XML 浅色闪一下。
 * 色值与 [MainColors] 画布一致。
 */
object StartupTheme {
    /** 与 [MainColors.Light.page] 一致 */
    const val PAGE_LIGHT_ARGB: Int = 0xFFF6F7F9.toInt()
    /** 与 [MainColors.Dark.page] 一致 */
    const val PAGE_DARK_ARGB: Int = 0xFF121214.toInt()

    fun isSystemDark(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    /** 阶段一：仅按系统深浅盖住窗口（prefs 尚未参与）。 */
    fun applySystemCover(activity: Activity) {
        paintWindow(activity, isSystemDark(activity))
    }

    /** 阶段二：按用户保存的外观解析后覆盖窗口与 [MainPalette]。 */
    fun applyUserAppearance(activity: Activity, appearance: AppAppearance) {
        val dark = appearance.resolveDark(isSystemDark(activity))
        MainPalette.bind(if (dark) MainColors.Dark else MainColors.Light)
        paintWindow(activity, dark)
    }

    fun paintWindow(activity: Activity, dark: Boolean) {
        val page = if (dark) PAGE_DARK_ARGB else PAGE_LIGHT_ARGB
        val window = activity.window
        window.setBackgroundDrawable(ColorDrawable(page))
        @Suppress("DEPRECATION")
        window.navigationBarColor = page
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // edge-to-edge 后仍需同步图标明暗
        val decor = window.decorView
        WindowCompat.getInsetsController(window, decor).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        // 部分机型在首次 layout 前 decor 未就绪，下一帧再刷一次
        decor.post {
            WindowCompat.getInsetsController(window, decor).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        // 避免启动瞬间系统再画一层默认白底
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }
}
