# 运行时 API

入口脚本在独立环境中执行。宿主注入全局对象 **`Xuan`**（萱的拼音）。

本页描述引擎 `0.0.2` 向入口脚本提供的全部 API。未列出的能力（网络、播放、读取包内资源、模块加载等）不存在，不得调用。

只读版本与 `register` 在入口一开始即可使用。其它宿主 API（灵动岛通知、同步延迟、文本主题）仅在插件已成功注册 `Running` 之后可用；未 `Running` 时调用失败，不结束插件。可选能力 API 另须在清单 `capabilities` 中声明（见 [THEME.md](./THEME.md)）。

## 生命周期

1. 宿主在启动时执行 **已启用** 插件的 `entry`。未启用的插件不执行。
2. 入口执行后、尚未成功注册 `Initializing` 之前，状态为未初始化。
3. 插件必须先注册 `Initializing`，再注册 `Running`。
4. 成功注册 `Running` 之后，插件视为就绪。
5. 注册 `Error` 立即结束 **本次** 运行。插件保持启用，下次宿主启动时仍会执行入口。

宿主在全部相关插件就绪之后进入主界面。仍处于 `Initializing` 的插件不算就绪。本次已因 `Error` 结束的插件不再阻塞启动。

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
| `Xuan.engine.version` | `string` | 可读版本，当前 `"0.0.2"` |
| `Xuan.engine.versionNumber` | `number` | 由 `version` 得出的整数，当前 `2` |

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

## `Xuan.theme`

可选能力 `theme`。完整 token 表与所有权见 [THEME.md](./THEME.md)。须已声明 `capabilities` 含 `"theme"`，且已成功注册 `Running`。

### `Xuan.theme.set(partial)`

| 参数 | 类型 | 说明 |
|---|---|---|
| `partial` | `object` | 非空。键为 token 名，值为 `#RRGGBB` / `#AARRGGBB` 字符串 |

返回 `true` 表示已合并 overlay 且本插件成为 owner；`false` 表示拒绝（不抛异常、不结束插件）。单次调用要么全部写入要么全部失败。

下列调用 **失败**：

- 尚未 `Running`，或未声明 `theme` 能力
- `partial` 不是对象、为空、含未知 key，或任一颜色非法
- 本次运行已结束

### `Xuan.theme.clear()`

清空全部文本主题 overlay。仅 **当前 owner** 成功。返回 `true` / `false`。

### `Xuan.theme.get()`

返回当前生效色的普通对象（默认 ∪ overlay，值均为颜色字符串）。未 `Running` 或未声明能力时返回 `{}`。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.theme.set({ "text.dock.active": "#EC4141" });
var colors = Xuan.theme.get();
Xuan.theme.clear();
```
