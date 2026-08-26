# 插件界面（客户端）

作者契约见 [../UI.md](../UI.md)。

## 组件

| 类型 | 位置 | 职责 |
|---|---|---|
| `PluginUiTree` | `plugin/` | 解析 / 补丁 / 计数；JVM 可测 |
| `PluginUiBridge` | `plugin/` | 槽位、操作槽、页定义、导航命令、弹窗 / sheet / 表面菜单；不持有 JS |
| `PluginCollections` | `plugin/` | 具名陈列区常量与已知表 |
| `PluginCollectionPresent` / `PluginCollectionParams` | `plugin/` | 按区 overlay、解析、合并、所有权；Compose 读 `of` |
| `LibraryCollectionItems` / `CollectionCover` | `ui/library/` | 个人页歌单 / 专辑按 overlay 画行或砖；黑胶走 `VinylDiscPlate` |
| `PluginUiTreeView` | `ui/plugin/` | Compose 画树；`color` 走 `TextTheme.namedColor` |
| `PluginPageScreen` | `ui/plugin/` | 叠层页或 Dock 目的页（`PluginPageChrome`） |
| `PluginAlertHost` / `PluginSheetHost` / `PluginContextMenuHost` | `ui/plugin/` | 玻璃框 |
| `Modifier.pluginSurface` | `ui/plugin/` | 宿主封面等：观察按压、长按弹出操作槽 |
| `FeaturesScreen` / `SettingsScreen` | 读 `slots` | `features.grid` / `settings.rows` |
| `Xuan.ui` | `PluginSession` | 注入；`page.on` / `slot.on` / `action.on` 的 JS 函数只在插件线程调用；`collection` 走 `PluginCollectionPresent` |

## 陈列

`Xuan.ui.collection`。区名表在 `PluginCollections.KNOWN`。每区一份 overlay，后一次成功 `set` 成为该区 owner 并按字段合并。`clear` 仅 owner。`PluginEngine.onSessionEnded` 与会话 `Error` 时 `clearIfOwner`。

宿主默认：`library.collected.albums` 为 grid / tile / round / 3 列；其余已知区为 list / row / round，`columns` 预存 3。黑胶是宿主食谱，不是 JS 路径。

不做：整页组件树替换个人页、按行回调、用 theme token 冒充列数、HTML / WebView 陈列。

## 线程

树与槽位是普通数据。Compose 只收集 `StateFlow`。`press` / `change` / `open` / `leave` 经 `PluginUiBridge.emit` → 插件 executor。禁止在 Compose 里 `evaluate`。

控件的 `value`：手势当时在桥上 `patch` 该节点，再 `emit` `change`，避免等 JS 才动。

## 导航

`open` 发出 `PluginUiCommand.OpenPage`。`stackKey` 为 `plugin-page-{id}-{page}-{instance}`，同一插件可多层。`MainShell` 限制每插件 8 层。`back` 仅当顶层属于该插件时 `popOverlay`。系统返回不询问插件。

`LaunchedEffect` / `DisposableEffect` 发 `open` / `leave`。Pager 上的探针页仅在选中时发 `open`。

调试开启且探针已定义 `tune` 时，宿主把该页编进主 Pager（竖屏可横滑）与横屏侧栏，与 Home / Features / Profile **切换**，不走叠层、不藏 Dock。社区插件打开的页仍走 `OpenPage` 叠层。`dev.zmusic.probe` 的 `page.open` 也切到该 Pager 页。这不是槽位 API，社区插件不能加 Dock 标签。

## 能力

展示名 `ui`。运行时不检查清单。陈列属同一能力，不另起 capability 名。

## 测试

JVM：`PluginCollectionPresentTest`（已知区全表、解析容错、按区所有权、合并、`clearIfOwner`）。无 Compose / 仪器测试。
