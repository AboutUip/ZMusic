# 播放（`Xuan.player`）

可选能力（展示用）。建议在清单写出 `"player"`，供工坊展示。引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

提供当前播放快照、队列、播放控制、音量、倍速、循环模式、定时停止，以及当前曲目的喜欢切换。搜索和按 id 起播见 [CATALOG.md](./CATALOG.md)。不提供下载地址。

失败返回 `false` 或 `null`（见各调用），不抛异常、不结束插件。控制请求投到宿主播放层执行；脚本仍在本插件线程上，不会在播放线程里跑 JS。

## 曲目对象

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 曲目 id |
| `name` | `string` | 标题 |
| `artists` | `string` | 艺人展示名 |
| `album` | `string` 或 `null` | 专辑名 |
| `durationMs` | `number` | 时长毫秒 |
| `coverUrl` | `string` 或 `null` | 封面地址 |
| `liked` | `boolean` 或 `null` | 仅出现在 **当前曲** 上。未知为 `null` |

队列里的曲目对象 **不含** `liked`。

## 快照 `Xuan.player.get()`

返回普通对象。未 `Running` 或已结束时返回 `null`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `playing` | `boolean` | 播放意图（缓冲期间仍可能为 `true`） |
| `positionMs` | `number` | 当前进度毫秒 |
| `durationMs` | `number` | 当前曲时长毫秒；无曲目为 `0` |
| `liked` | `boolean` 或 `null` | 当前曲是否喜欢；无曲目或未知为 `null` |
| `track` | 曲目对象或 `null` | 当前曲 |
| `queue` | 对象 | 见下表 |
| `mode` | `string` | `order` / `repeatOne` / `shuffle` |
| `volume` | `number` | `0`～`1` |
| `rate` | `number` | 倍速，宿主夹在 `0.1`～`3` |
| `lyric` | 对象 | 当前行：`index` / `text` / `translation`。无行时 `index` 为 `-1` |
| `sleep` | 对象 | `active` / `remainingMs` / `waitForTrackEnd` / `pendingStopAfterTrack` |

### `queue`

| 字段 | 类型 | 说明 |
|---|---|---|
| `length` | `number` | 队列真实长度 |
| `index` | `number` | 当前下标；无队列为 `-1` |
| `tracks` | 数组 | 曲目对象列表，最多 **100** 首（从队首起） |
| `truncated` | `boolean` | 是否因上限截断 |

进度会随播放更新，可随时 `get()`。不要对进度做逐帧钩子；钩子规则见 [HOOK.md](./HOOK.md)。

## 控制

| 调用 | 成功 | 说明 |
|---|---|---|
| `play()` | `true` / `false` | 无队列则失败。已在播放则为成功且不再切换 |
| `pause()` | `true` / `false` | 已暂停则为成功且不再切换 |
| `next()` | `true` / `false` | 无下一首或无队列则失败 |
| `prev()` | `true` / `false` | 无上一首或无队列则失败 |
| `seek(ms)` | `true` / `false` | `ms` 须为 `≥ 0` 的有限整数 |
| `volume()` / `setVolume(level)` | 数字 / `true` | `level` 为有限数字，宿主夹到 `0`～`1` |
| `rate()` / `setRate(rate)` | 数字 / `true` | 宿主夹到 `0.1`～`3` |
| `mode()` / `setMode(name)` | 字符串 / `true` | `name` 为 `order`、`repeatOne`、`shuffle`。私人漫游或心动模式下 `shuffle` 失败 |
| `cycleMode()` | `true` | 按宿主顺序切换模式 |
| `playAt(index)` | `true` / `false` | 跳到队列下标。越界失败 |
| `clear()` | `true` | 清空队列并停止 |
| `open()` | `true` | 打开全屏播放页 |
| `sleep()` | 对象 | 当前定时停止 |
| `sleepStart(minutes, waitForTrackEnd?)` | `true` / `false` | `minutes` 为 `1`～`60` 的整数。第二参省略视为 `false` |
| `sleepCancel()` | `true` | 取消定时停止 |

成功表示宿主 **已接受** 该次请求，不保证音源立刻就绪。

## 喜欢

| 调用 | 返回 | 说明 |
|---|---|---|
| `liked()` | `true` / `false` / `null` | 当前曲；无曲目或未知为 `null` |
| `setLiked(liked)` | `true` / `false` | 须已登录且非游客，且当前有曲目。先改本地再请求远端；远端失败时宿主回滚 |

未登录、游客、离线、无当前曲：`setLiked` 失败。

## 歌词 `Xuan.lyric`

| 调用 | 返回 | 说明 |
|---|---|---|
| `get()` | 对象 | 已在内存中的原文 `lines`、译文 `translations`、当前 `index` / `text` / `translation`。每行含 `timeMs`、`text`、`words`。单侧最多 800 行 |
| `line()` | 对象或 `null` | 仅当前行。还没有歌词时为 `null` |
| `load(opts, fn)` | `true` / `false` | 异步，见 [CATALOG.md](./CATALOG.md)。`opts.id` 可省略，省略则用当前曲 |

进度变化不逐帧回调。当前行变化走 `player.lyric`，见 [HOOK.md](./HOOK.md)。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

var snap = Xuan.player.get();
if (snap && snap.track) {
  Xuan.player.play();
  Xuan.player.seek(0);
  Xuan.player.setLiked(true);
}
```
