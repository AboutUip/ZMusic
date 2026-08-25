# 运行时 API

入口脚本在独立环境中执行。宿主注入全局对象 **`Xuan`**（萱的拼音）。

本页描述引擎 `0.1.0` 向入口脚本提供的 API 索引。各对象的完整字段与失败条件见对应专页。

只读版本与 `register` 在入口一开始即可使用。其它宿主 API 仅在插件已成功注册 `Running` 之后可用；未 `Running` 时调用失败，不结束插件。

`capabilities` 供工坊展示，**不是**调用门闩。已实现的 API 对 `Running` 插件一律可调。清单写法见 [MANIFEST.md](./MANIFEST.md)。

## 生命周期

1. 宿主在启动时执行 **已启用** 插件的 `entry`。未启用的插件不执行。
2. 入口执行后、尚未成功注册 `Initializing` 之前，状态为未初始化。
3. 插件必须先注册 `Initializing`，再注册 `Running`。
4. 成功注册 `Running` 之后，插件视为就绪。入口可以返回；**Context 继续留着**，以便 `hook` / `timer` / `http` 回调。
5. 注册 `Error` 立即结束 **本次** 运行。插件保持启用，下次宿主启动时仍会执行入口。

宿主在全部相关插件就绪之后进入主界面。仍处于 `Initializing` 的插件不算就绪。本次已因 `Error` 结束的插件不再阻塞启动。

入口、`delay`、`hook` 回调、`timer` 回调、`http` 回调、界面结果回调跑在 **该插件同一条线程** 上，互相排队。`delay` 期间到期的定时器、钩子与网络回调，在 `delay` 返回之后才会执行。

## `Xuan.zmusic`

只读。对应宿主应用程序版本。整数由可读字符串按 [VERSIONING.md](./VERSIONING.md) 引擎三段规则算出。

| 属性 | 类型 | 说明 |
|---|---|---|
| `Xuan.zmusic.version` | `string` | 可读版本，例如 `"1.2.3"` |
| `Xuan.zmusic.versionNumber` | `number` | 由 `version` 得出的整数，例如 `10203` |

## `Xuan.engine`

只读。对应插件引擎版本。整数由可读字符串按 [VERSIONING.md](./VERSIONING.md) 算出。

| 属性 | 类型 | 说明 |
|---|---|---|
| `Xuan.engine.version` | `string` | 可读版本，当前 `"0.1.0"` |
| `Xuan.engine.versionNumber` | `number` | 由 `version` 得出的整数，当前 `100` |

上述属性禁止赋值。

## `Xuan.runtime.State`

| 值 | 含义 |
|---|---|
| `Initializing` | 初始化 |
| `Running` | 运行中（就绪） |
| `Error` | 错误 |

## `Xuan.runtime.register(state)`

向宿主声明当前状态。`state` 必须是 `Xuan.runtime.State` 的值。

| 调用 | 条件 | 结果 |
|---|---|---|
| `Initializing` | 尚未成功注册过该状态 | 进入初始化 |
| `Running` | 已成功注册 `Initializing`，且尚未注册 `Running` | 进入运行中 |
| `Error` | 任意时刻 | 立即结束本次运行 |

下列调用 **失败**（不改变状态，不结束插件）：

- 同一状态成功注册第二次
- 反向，例如已 `Running` 再注册 `Initializing`
- 未成功注册 `Initializing` 即注册 `Running`

失败时插件继续运行，可随后进行合法注册或注册 `Error`。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);

Xuan.runtime.register(Xuan.runtime.State.Running);
```

发生无法继续的情况时：

```javascript
Xuan.runtime.register(Xuan.runtime.State.Error);
```

## `Xuan.notice.show(message, coverUrl?)`

向宿主顶部灵动岛投递一条通知。仅在已成功注册 `Running` 之后可用。

| 参数 | 类型 | 说明 |
|---|---|---|
| `message` | `string` | 岛内文案。空白视为无效 |
| `coverUrl` | `string`（可选） | 封面地址。省略、空串或非字符串视为无封面 |

返回 `true` 表示宿主已接受；返回 `false` 表示拒绝。拒绝时不抛异常、不改变运行状态、不结束插件。

下列调用 **失败**（返回 `false`）：

- 尚未成功注册 `Running`
- `message` 不是字符串，或去掉首尾空白后为空
- 本次运行已结束

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.notice.show("已就绪");
Xuan.notice.show("正在播放", "https://example.com/cover.jpg");
```

## `Xuan.delay(ms)`

同步阻塞 **本插件** 的执行线程，效果类似 `sleep`。仅在已成功注册 `Running` 之后可用。可多次调用；每一次都必须单独满足时限。注册 `Running` 之后的等待 **不** 阻塞宿主进入主界面。

新代码优先用下文 `Xuan.timer`，避免长时间占住插件线程。

| 参数 | 类型 | 说明 |
|---|---|---|
| `ms` | `number` | 延迟毫秒。必须是 `1`～`60000` 的整数（含 1 分钟） |

返回 `true` 表示已完整等待；返回 `false` 表示拒绝或等待被中断。拒绝时立即返回，不等待，不抛异常、不改变运行状态、不结束插件。

下列调用 **失败**（返回 `false`）：

- 尚未成功注册 `Running`
- `ms` 不是整数，或不在 `1`～`60000`
- 本次运行已结束
- 等待期间会话被停止

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.delay(500);
Xuan.notice.show("半秒后");
```

## `Xuan.timer`

非阻塞定时器。到期后在 **本插件线程** 上调用函数，不阻塞宿主。名称属于 **本插件**：两个插件可以都叫 `"tick"`。

仅 `Running` 之后可用。会话结束时全部定时器取消。

| 调用 | 含义 |
|---|---|
| `Xuan.timer.simple(ms, fn)` | 到期一次。无法按名取消 |
| `Xuan.timer.create(name, ms, reps, fn)` | 按名创建或替换。`reps` 为 `0` 表示无限次 |
| `Xuan.timer.remove(name)` | 取消。本来没有也返回 `true` |
| `Xuan.timer.exists(name)` | 是否有这个名字的定时器 |

`ms`：正整数毫秒，最小 `1`，不设上限。`name`：非空字符串。`fn`：函数。`reps`：整数，`0` = 无限，`≥1` = 次数。

失败返回 `false`（`exists` 在失败时也是 `false`）。`simple` / `create` 成功为 `true`。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.timer.simple(1000, function() {
  Xuan.notice.show("一秒后");
});

Xuan.timer.create("tick", 5000, 0, function() {
  Xuan.notice.show("每五秒");
});

Xuan.timer.remove("tick");
```

## `Xuan.hook`

全局钩子总线。完整事件表与规则见 [HOOK.md](./HOOK.md)。

| 调用 | 作用 |
|---|---|
| `Xuan.hook.add(name, id, fn)` | 监听 |
| `Xuan.hook.remove(name, id)` | 取消本插件这一条 |
| `Xuan.hook.run(name, ...args)` | 投递给所有监听者 |

## `Xuan.require` / `Xuan.pack`

包内 `require`（CommonJS）与读资源。见 [PACK.md](./PACK.md)。

## `Xuan.player`

当前播放快照、队列只读、播放控制与喜欢切换。见 [PLAYER.md](./PLAYER.md)。清单建议声明 `player`，不是调用门闩。

## `Xuan.http`

出站 `http` / `https`。见 [HTTP.md](./HTTP.md)。清单建议声明 `http`，不是调用门闩。

## `Xuan.store`

按插件 id 隔离的持久键值。见 [STORE.md](./STORE.md)。清单建议声明 `store`，不是调用门闩。

## `Xuan.ui`

宿主绘制的槽位入口、组件树页面、多级叠层、弹窗、sheet，以及宿主表面的操作槽。见 [UI.md](./UI.md)。清单建议声明 `ui`，不是调用门闩。

## `Xuan.media` / `Xuan.share` / `Xuan.clipboard`

保存图片到相册、系统分享、剪贴板。见 [DEVICE.md](./DEVICE.md)。清单建议分别声明 `media` / `share` / `clipboard`，不是调用门闩。

## `Xuan.theme`

宿主主题（文本 / 面色 / 铬 / 控件 / 舞台）。完整 token 表与所有权见 [THEME.md](./THEME.md)。引擎 `0.1.0` 起 **不** 要求清单声明 `theme`；仅须 `Running`。声明仍建议写上，给工坊展示。

### `Xuan.theme.set(partial)`

| 参数 | 类型 | 说明 |
|---|---|---|
| `partial` | `object` | 非空。键为 token 名，值为 `#RRGGBB` / `#AARRGGBB` 字符串 |

返回 `true` 表示已合并 overlay 且本插件成为 owner；`false` 表示拒绝（不抛异常、不结束插件）。单次调用要么全部写入要么全部失败。

下列调用 **失败**：

- 尚未 `Running`
- `partial` 不是对象、为空、含未知 key，或任一颜色非法
- 本次运行已结束

### `Xuan.theme.clear()`

清空全部主题 overlay。仅 **当前 owner** 成功。返回 `true` / `false`。

### `Xuan.theme.get()`

返回当前生效色的普通对象（默认 ∪ overlay，含全部 token，值均为颜色字符串）。未 `Running` 时返回空对象 `{}`。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.theme.set({ "face.accent": "#EC4141", "text.accent": "#EC4141" });
var colors = Xuan.theme.get();
Xuan.theme.clear();
```
