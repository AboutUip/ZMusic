# 宿主表面与操作槽（客户端）

作者契约见 [../HOOK.md](../HOOK.md) 的 `ui.*` 与 [../UI.md](../UI.md) 的 `Xuan.ui.action`。

## 手势

Compose 用 `Modifier.pluginSurface(surface, target)`（`ui/plugin/PluginHostGestures.kt`）。短按 **不 consume**，只 `emitUiGesture("press")`，原有 clickable 照常。长按 consume，调用 `PluginEngine.handleSurfaceLongPress`：先投 `ui.longPress`，若该表面有插件操作槽则弹出宿主 sheet（可带一项宿主默认），否则执行宿主原长按。

播放页黑胶继续用 `vinylLightTapGestures`，以免挡住横滑切歌 / 下滑退出；长按回调里同样走 `handleSurfaceLongPress`。横屏在「选歌」开启时，把该项作为 sheet 里的宿主默认，无插件项时行为与从前相同。

溢出菜单（`track.overflow`）不另开一层：把插件操作追加到现有 `TrackOverflowMenu`，并投 `ui.menu`。

## 操作槽

`PluginUiBridge` 保存 `PluginActionEntry` 列表。`presentSurfaceMenu` 在已有插件项且当前没有 alert / 插件 sheet / 另一份表面菜单时成功。`PluginContextMenuHost` 画玻璃 sheet。会话结束 `clearActions`。

未知表面名使 `action.set` / `action.on` 失败。表面名表在 `PluginSurfaces`。

## 目标对象

`PluginUiTarget.toMap()` 交给钩子与 `action` 的 `press` 事件。`kind` 为 `track` / `album` / `playlist` / `artist` / `mv` / `chart` / `user` / `image`。
