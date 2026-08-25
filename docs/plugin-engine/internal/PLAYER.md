# 播放（客户端）

作者契约见 [../PLAYER.md](../PLAYER.md)。

## 快照

`ZMusicApplication` 采集 `PlaybackBridge.ui` 与 `LikedPlaylistRepository`，映射为 `PluginPlaybackSnapshot`，调用 `PluginEngine.setPlaybackSnapshot`。

比较字段：当前曲 id、`playing`（用 `playWhenReady`）、喜欢、队列下标 / 长度 / 曲目 id 序列。变化时分别投 `player.track` / `player.state` / `player.liked` / `player.queue`。`positionMs` 只写入缓存，供 `get()`，不投钩子。

队列 `tracks` 最多 100 首，从下标 0 起；`length` 仍为真实长度。

## 控制

`PluginPlayerController` 由应用层实现。JS 线程只发命令；`Handler(Looper.getMainLooper())` 调 `PlaybackBridge`（`togglePlayPause` / `skipNext` / `skipPrevious` / `seekTo` / `ensureService`）。禁止把 QuickJS 句柄交给播放层。

`play` / `pause` 根据当前 `playWhenReady` 决定是否 `togglePlayPause`，避免重复切换。

喜欢：与播放页同一路径——`applyLocalLike` 后协程 `SongRepository.likeSong`；失败回滚。游客 / 无 cookie / 无当前曲 / 产品离线返回 `false`。

## 能力

展示名 `player`。运行时不检查清单。
