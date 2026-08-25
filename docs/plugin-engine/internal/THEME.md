# 主题（客户端）

作者契约见 [../THEME.md](../THEME.md)。

## 组件

| 类型 | 位置 | 职责 |
|---|---|---|
| `PluginCapabilities` | `plugin/` | 已知能力白名单；清单解析 |
| `TextTheme` / `TextThemeKeys` | `ui/theme/` | 全量 token 默认表、overlay、Compose 可观察 getter |
| `MainPalette` | `ui/theme/` | 面色 / 铬 getter **全部** `TextTheme.resolve`；`glassFill` 仅 overlay 命中 `chrome.glassFill` 时短路 |
| `MainControls` | `ui/theme/` | 滑条走 `face.accent` / `face.trackOff`；开关拇指走 `control.thumb` |
| `ZMusicTheme` | `ui/theme/Theme.kt` | `colorScheme` 读 `MainPalette` / `TextTheme` getter，随 overlay 重组 |
| `PluginTextThemeBridge` | `plugin/` 回调 / `AppContainer` 接线 | `set` / `clear` / `get` / 会话结束清 owner |
| `Xuan.theme` | `PluginSession.injectXuan` | `hostApiAllowed`；**不再**检查清单是否声明 `theme` |

## 解析

1. `MainPalette.bind(MainColors)` 时同步 `TextTheme.bindDefaults(colors)`。`snapshot` / `isDark` 仍是宿主默认，不含 overlay。
2. 播放页舞台 token 默认不跟浅深 ink 走，写死在默认表（与作者文档一致）。
3. 覆盖面：
   - 已读 `MainPalette.*` / `TextTheme.*` 的界面自动跟上（首页、设置、歌单、Dock、岛、插件树控件等）。
   - 播放页：`PlayerTransport` 进度与播放钮、`NowPlayingScreenLayers` / 自定义背景编辑底走 `player.*`；舞台控件字色仍走 `text.player.*`。
   - 迷你条进度底槽走 `face.hairline`。
   - 登录 / 启动主色、资料 VIP 条、封面占位黑胶标签走 `face.accent`。
   - 插件树 `color` 经 `TextTheme.namedColor`：全名 ∈ `TextThemeKeys.ALL`，或省略 `text.` 前缀。
4. 正文三角：`MainPalette.Ink/Secondary/Hint` 仍转发 `text.title` / `text.subtitle` / `text.hint`。`MainPalette.Accent` 现为 `face.accent`；字色强调继续用 `TextTheme.Accent`。

## 生命周期清 overlay

在 `PluginEngine`：会话 `stop`、禁用、卸载、离线停会话、引擎 `stop`，以及会话进入 `Error` 时，对对应 `pluginId` 调用 `clearIfOwner`。

## 测试

JVM：`TextThemeTest`（全表逐 key overlay → 宿主 getter、`control.thumb`、`player.*`、文本 getter、`namedColor` 各族简称）、`PluginTextThemeBridgeTest`（hex 含 alpha、未知 key 不写）。无 Compose / 仪器测试。

## 能力门控

- 清单：`capabilities` 省略 / `[]` / 字符串数组；未知名忽略；非字符串则清单无效。
- 运行：不检查 `record.capabilities`。只要求 `Running` 且参数合法。
