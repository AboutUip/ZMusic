# 钩子（客户端）

作者契约见 [../HOOK.md](../HOOK.md)。

## 职责

| 位置 | 职责 |
|---|---|
| `PluginEngine` | 全局总线：登记、按插件 id 字典序投递、会话结束摘掉该插件全部监听；`setHostFacts` / `setPlaybackSnapshot` 决定是否 `run` 宿主事件；`emitUiGesture` / `handleSurfaceLongPress` 投 `ui.*` |
| `PluginSession` | 注入 `Xuan.hook`；在 **本插件线程** 上调用 JS 函数；`hold`/`release` 回调 |
| 应用层 | 网络 / 外观 / 登录变化调用 `setHostFacts`；Activity 计数更新 `foreground`；播放层 `StateFlow` 映射为快照后 `setPlaybackSnapshot`。禁止在播放线程进 Context |

## 调用线程

所有 JS 回调必须投到该插件的 executor，与入口、`delay`、timer、http、界面结果同一条线程。禁止在 UI 或播放线程进 Context。

`delay` 阻塞期间到期的 hook 排在 `delay` 返回之后。

## `add` 当前值

对 `app.online` / `app.offline` / `app.appearance` / `user.session` / `app.foreground` / `app.background` / `player.*`：登记成功后、`add` 返回前，在同一插件线程用当前快照调用这一次。`app.online` 与 `app.offline` 只投其中与现状相符的那一个。`app.foreground` 与 `app.background` 同理。播放四项总会补。

`ui.press` / `ui.longPress` / `ui.menu` 不补。进度字段变化不得触发 `player.state`。

## `run` 参数

跨插件必须经宿主。用 JSON 能表达的值拷一份再发给每个监听器。拷失败则整次 `run` 失败。

监听器抛错：写入该插件 journal，继续。不弹故障窗、不 `Error`。

## 哨兵

入口结束后 Context 仍在。hook 回调执行期间视为「正在跑 JS」：标记哨兵，回调返回后清除。回调中崩溃则隔离，与入口崩溃相同。

## 能力

`hook` 不是 `capabilities` 项。只检查 `hostApiAllowed`。
