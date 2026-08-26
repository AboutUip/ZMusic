# 外观（客户端）

作者契约见 [../LOOK.md](../LOOK.md)。

## 组件

| 类型 | 位置 | 职责 |
|---|---|---|
| `PluginLookRegions` | `plugin/` | 区名表 |
| `PluginLookPresent` / `PluginLookParams` | `plugin/` | 按区 overlay、解析、包内位图路径、所有权 |
| `Xuan.look` | `PluginSession.injectXuan` | `hostApiAllowed`；`set` 时用该插件 `extractDir` 解析 `pack` |
| `ZMusicTheme` | `ui/theme/` | `appearance` overlay 盖过 `ThemeStore` |
| `IslandNoticeRoot` | `ui/notice/` | `chrome.glass` 盖过 `ChromeGlassStore` 再 `LocalChromeGlassStyle` |
| `MainShell` | `ui/main/` | `chrome.wallpaper` 盖过 `ChromeWallpaperStore` |
| `LibraryScreen` | `ui/library/` | 壁纸生效态 + `library.profile` 路径 |
| `vinylPlateColors()` / 竖屏 `VinylTransitionStage` | `ui/player/` | `player.vinyl` |
| `NowPlayingScreen` / `NowPlayingScreenLayers` | `ui/player/` | `player.atmosphere` / `player.background` |

## 生命周期

不写 SharedPreferences。`PluginEngine.onSessionEnded` 与会话 `Error` 时 `clearIfOwner`。用户设置页仍显示并编辑用户自己的值。

位图路径指向解压根；会话结束 overlay 摘掉后不再绘制。SVG 拒绝。缺文件使该次 `set` 失败。

`chrome.wallpaper` 插件帧 `WallpaperFrame.coverBaseline = true`。绘制时 `scale` 乘 [ChromeWallpaperStore.defaultCanvasScale]（cover / contain × 1.5），与用户选图默认一致。用户设置帧仍相对 contain，不改已存偏好。

`player.background` 插件帧 `PlayerBackgroundPreset.coverFill = true`。竖屏背景用与壁纸相同的 [wallpaperCanvasPlacement]（`coverFill`：`scale` 1 = cover，无 1.5 倍）。Compose Crop + `graphicsLayer` 平移会在 `offsetY≠0.5` 时露出 `player.stage`。用户预设仍 Fit。偏好 `sanitized()` 丢掉 `coverFill`。

## 测试

JVM：`PluginLookPresentTest`（区名全表、解析容错、按区所有权、壁纸缺图、包内 png、插件帧 `coverBaseline` / `coverFill`）；`ChromeWallpaperLogicTest`（contain、插件 chrome 1.5×cover、插件播放页 cover，以及 offset 不露底）。无 Compose / 仪器测试。

## 能力

展示名 `look`。运行时不检查清单。
