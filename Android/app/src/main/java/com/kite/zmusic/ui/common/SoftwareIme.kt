package com.kite.zmusic.ui.common

import android.app.Activity
import android.content.Context
import android.os.IBinder
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 同时走 Android View 焦点、IMM 和 WindowInsets。
 * 部分输入法在 ComposeView 仍持有 InputConnection、或用户再次点搜索框显式唤出键盘时，
 * 会忽略仅有的 SoftwareKeyboardController.hide()。
 */
fun hideSoftwareIme(
    view: View,
    activity: Activity? = null,
    windowToken: IBinder? = null,
) {
    val focused = view.findFocus() ?: activity?.currentFocus
    val token = windowToken ?: focused?.windowToken ?: view.windowToken
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    focused?.clearFocus()
    view.clearFocus()
    activity?.currentFocus?.clearFocus()
    if (token != null) {
        imm?.hideSoftInputFromWindow(token, 0)
        imm?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
    }
    val act = activity ?: view.context as? Activity
    val window = act?.window
    if (window != null) {
        WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.ime())
    }
}
