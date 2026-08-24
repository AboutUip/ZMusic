# 文本主题（客户端）

作者契约见 [../THEME.md](../THEME.md)。

## 组件

| 类型 | 位置 | 职责 |
|---|---|---|
| `PluginCapabilities` | `plugin/` | 已知能力白名单；清单解析 |
| `TextTheme` | `ui/theme/` | 默认 token、overlay、Compose 可观察 getter |
| `PluginTextThemeBridge` | `plugin/` 回调 / `AppContainer` 接线 | `set` / `clear` / `get` / 会话结束清 owner |
| `Xuan.theme` | `PluginSession.injectXuan` | 须 `capabilities` 含 `theme` 且 `hostApiAllowed` |

## 解析

1. `MainPalette.bind(MainColors)` 时同步 `TextTheme.bindDefaults(colors)`。
2. 播放页舞台 / onPhoto 默认不跟浅深 ink 走，写死在 `TextTheme` 默认表（与作者文档一致）。
3. UI：**细粒度**处读 `TextTheme.*`（Dock、顶栏、迷你播放器、岛、player、onPhoto）。常见正文三角：`MainPalette.Ink/Secondary/Hint` getter 转发到 `TextTheme` 的 title / subtitle / hint，以便全 App 正文随 `text.title` 等变更。`MainPalette.Accent` 仍为面色/控件强调，**字色强调**用 `TextTheme.Accent` / `Destructive`。

## 生命周期清 overlay

在 `PluginEngine`：会话 `stop`、禁用、卸载、离线停会话、引擎 `stop`，以及会话进入 `Error` 时，对对应 `pluginId` 调用 `clearIfOwner`。

## 能力门控

- 清单：`capabilities` 省略 / `[]` / 仅含白名单字符串（当前仅 `theme`）；未知字符串整包无效。
- 运行：`record.capabilities` 含 `theme` 才允许 `set`/`clear`/`get` 成功路径。
