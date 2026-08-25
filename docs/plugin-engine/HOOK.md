# 钩子（`Xuan.hook`）

引擎 **`0.1.0`** 起的核心 API，不是可选 `capabilities`。所有已成功注册 `Running` 的插件共用 **一条宿主总线**：谁 `add` 谁收到，`run` 会通知所有监听者。

不拦截、不改写事件。监听器的返回值被忽略。播放控制走 [PLAYER.md](./PLAYER.md)，不经过本总线。

仅在已成功注册 `Running` 之后可用。失败返回 `false`，不抛异常、不结束插件。

## 名称与标识

| | 规则 |
|---|---|
| 事件名 `name` | 非空字符串，大小写敏感。去掉首尾空白后为空则失败 |
| 监听 id | 非空字符串，**每个插件自己的命名空间**。不同插件可以用同一个 id |
| 回调 | 必须是函数。同一插件对同一 `name`+`id` 再次 `add` 视为替换 |

宿主预定义事件见下表。任意其它 `name` 也允许：用于插件自定义事件，宿主不会自己 `run`。

## 宿主事件

| `name` | 何时 | 回调参数 |
|---|---|---|
| `app.online` | 从离线恢复在线 | 无 |
| `app.offline` | 进入离线 | 无 |
| `app.appearance` | 浅色 / 深色切换 | 一个对象：`{ dark: boolean }` |
| `user.session` | 登录态变化 | 一个对象：`{ loggedIn: boolean }` |
| `player.track` | 当前曲目变化（含变为无曲目） | 一个参数：曲目对象或 `null`。字段见 [PLAYER.md](./PLAYER.md) |
| `player.state` | 播放 / 暂停意图变化 | `{ playing: boolean }` |
| `player.queue` | 队列长度、当前下标或曲目 id 序列变化 | `{ length, index, tracks, truncated }` |
| `player.liked` | 当前曲目的喜欢状态变化 | `{ liked: boolean \| null }` |
| `app.foreground` | 应用进入前台 | 无 |
| `app.background` | 应用进入后台 | 无 |
| `ui.press` | 用户点了宿主表面上的对象 | 见下文「界面手势」 |
| `ui.longPress` | 用户长按了宿主表面上的对象 | 同上 |
| `ui.menu` | 宿主即将或已经出示该表面的菜单（例如曲目溢出） | 同上 |

进度毫秒的连续变化 **不** 投递事件。需要进度时用 `Xuan.timer` 配合 `Xuan.player.get()`。

### 界面手势

`ui.press` / `ui.longPress` / `ui.menu` 的回调参数是一个对象：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | `string` | `press` / `longPress` / `menu` |
| `surface` | `string` | 具名表面，见 [UI.md](./UI.md)「操作槽」 |
| `target` | 对象 | 被指向的对象。`kind` 为 `track` / `album` / `playlist` / `artist` / `mv` / `chart` / `user` / `image`；另有 `id` / `name` / `subtitle` / `imageUrl`（均可 `null`）。曲目可能带 `album`、`durationMs` |

这些事件 **只观察**。返回值忽略。不替换宿主单击（例如进详情、播放）。长按若插件用 `Xuan.ui.action` 往该表面登记了条目，宿主会弹出 sheet（可含一项宿主原有动作）；没有插件条目时，宿主原长按照常（例如横屏黑胶选歌）。

以后追加事件名不升引擎版本；未在上表的名称若宿主尚未投递，则只是永远收不到，包仍然有效。

### 当前值

`add` 成功后、返回之前，若该 `name` 有当前值，宿主立刻调用这一次新监听器：

- `app.online` 只在已经在线时补一次；`app.offline` 只在已经离线时补一次。
- `app.foreground` 只在已经在前台时补一次；`app.background` 只在已经在后台时补一次。
- `app.appearance` / `user.session` 总会补一次（带当前对象）。
- `player.track` / `player.state` / `player.queue` / `player.liked` 总会补一次（带当前快照；无曲目时 `player.track` 的参数为 `null`）。

`ui.press` / `ui.longPress` / `ui.menu` 与自定义 `name` 没有当前值，不补。

## API

### `Xuan.hook.add(name, id, fn)`

登记监听。返回 `true` / `false`。

失败：未 `Running`、参数非法、本次运行已结束。

### `Xuan.hook.remove(name, id)`

移除本插件的这一条。没有这条也返回 `true`（幂等）。失败（`false`）仅未 `Running` / 参数非法 / 已结束。

### `Xuan.hook.run(name, ...args)`

在总线上投递。所有已 `Running` 且监听了该 `name` 的插件都会收到。返回 `true` 表示已投递（哪怕一个监听器都没有）。

`args` 必须是 JSON 能表达的值（`null` / 布尔 / 有限数字 / 字符串 / 数组 / 不含函数的普通对象）。函数、循环引用、不可 JSON 化的值使 **整次** `run` 失败。

投递顺序：插件 `id` 字典序；同一插件内按 `add` 的先后。某个监听器抛错：记入该插件日志，继续后面的监听器，不结束任何插件。

会话结束时，该插件的全部监听自动去掉。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.hook.add("app.appearance", "mine", function(ev) {
  // add 成功时会先被叫一次，ev.dark 为当前值
});

Xuan.hook.add("player.track", "mine", function(track) {
  if (track) {
    Xuan.notice.show(track.name);
  }
});

Xuan.hook.add("my.custom", "mine", function(payload) {
  Xuan.notice.show(payload.msg);
});

Xuan.hook.add("ui.longPress", "mine", function(ev) {
  if (ev.target && ev.target.imageUrl) {
    Xuan.notice.show(String(ev.surface));
  }
});

Xuan.hook.run("my.custom", { msg: "你好" });
Xuan.hook.remove("app.appearance", "mine");
```
