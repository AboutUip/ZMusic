# Android Architecture

本文描述 `ZMusic` **Android** 客户端的分层、主链路与进程内装配。Windows 为独立工程（见 [`WINDOWS.md`](./WINDOWS.md)）；跨端只对齐**行为**（播放模式、接口与缓存语义），不必对齐类名。

---

## 1. 技术栈

- UI: Jetpack Compose
- 播放: Media3 ExoPlayer + **MediaSessionService**（通知 / 前台服务由 Media3 独占）
- UI 控制: `MediaController` 拉起 Service；业务命令经进程内 `PlaybackBridge`
- 网络: OkHttp（`AppContainer` 内**一个** `OkHttpClient`）
- 状态: `StateFlow` + ViewModel + Bridge
- 安全存储: `EncryptedSharedPreferences`（会话）；播放队列快照用普通 `SharedPreferences`

---

## 2. 分层与禁令

```
ui (Compose) → ViewModel → repository → Ncm*Parse
                              ↘ NcmUserClient / NcmAuthClient（共享实例）
                 ViewModel → PlaybackBridge → PlaylistCoordinator
Application = 启动装配（AppContainer + PlaybackQueueSync）
```

| 层 | 允许 | 禁止 |
|----|------|------|
| Compose / Activity | 绑 StateFlow、发用户意图 | `NcmUserClient()` / `NcmAuthClient()`、解析 `JSONObject` |
| ViewModel | 调 repository / `PlaybackBridge` | 直接 HTTP、手写 JSON 解析 |
| repository | 调共享 client + `Ncm*Parse` | Compose 类型 |
| playback | 队列、起播、URL、歌词 | 依赖 `ui/` 包 |
| `NcmUserClient` | 薄 HTTP 门面 | 被 UI 直接 new |

单文件目标：新代码 / 拆出文件尽量小于 500 行。

---

## 3. 进程内装配

[`AppContainer`](../Android/app/src/main/java/com/kite/zmusic/AppContainer.kt) 创建共享 `NcmUserClient` / `NcmAuthClient` 与仓库。[`ZMusicApplication`](../Android/app/src/main/java/com/kite/zmusic/ZMusicApplication.kt) 只做启动：持有 container、把 `musicWillPlay` 接到 MV 停播、启动 [`PlaybackQueueSync`](../Android/app/src/main/java/com/kite/zmusic/playback/PlaybackQueueSync.kt)。

Compose 取依赖：`LocalContext.current.applicationContext as ZMusicApplication`。

---

## 4. 包图（`ui/*`）

| 包 | 职责 |
|----|------|
| `ui/main` | 主壳、Dock、迷你条、**overlay 路由**（`MainOverlay` + host） |
| `ui/home` / `ui/features` / `ui/library` | 三 Tab |
| `ui/catalog` | 歌单 / 专辑 / 排行榜等集合页（不再当全局路由器）。按 overlay 使用 `DailyFmViewModel` / `PlaylistDetailViewModel` / `AlbumDetailViewModel` / `ChartsCatalogViewModel` |
| `ui/player` | 全屏播放器（竖/横屏分文件）、评论、显示偏好编辑 |
| `ui/search` / `ui/artist` / `ui/mv` / `ui/settings` | 搜索、歌手、MV、设置 |
| `ui/notice` | 应用内灵动岛。**所有软件内提示走这里**，见 [`ANDROID-ISLAND-NOTICE.md`](./ANDROID-ISLAND-NOTICE.md) |
| `ui/lyricoverlay` | 通知栏歌词悬浮窗 Compose（仅应用外展示） |
| `ui/common` | `UrlImage`、`GlassAlertDialog`（与岛同窗液体玻璃，不用系统 Dialog） |
| `ui/theme` | `MainPalette` 与 Material 主题 |
| `ui/orientation` | 横竖屏过渡蒙版 |

点播前请求 `POST_NOTIFICATIONS`（拒绝仍可播，灵动岛提示后台可能受限）。

### Overlay 栈

主壳维护 `List<MainOverlay>`（日推、漫游、排行榜、搜索、设置、歌单、专辑、MV、歌手…）。Host 按栈做水平盖入动画。**不是** Navigation Compose 的二级图。

---

## 5. 播放层（`playback/*`）

| 组件 | 职责 |
|------|------|
| `PlaybackService` | 薄 `MediaSessionService`：Session + Coordinator；**不**手写 `startForeground`。通知栏左侧播放模式、右侧歌词开关；锁定后该右侧槽位变为取消锁定 |
| `LyricOverlayController` | 应用外 + 通知栏已开 + 已授权悬浮窗才挂 WindowManager |
| `PlaylistCoordinator` | 队列、NCM URL **按需解析**、歌词、模式、短 TTL 预取、私人 FM 续播；持续播放时不因音频焦点暂停
| `PlaybackBridge` | MediaController 启服、注册 Coordinator、UI `StateFlow`；**禁止空队列冲掉迷你条快照** |
| `PlaybackStateStore` | 唯一队列持久化 |
| `PlaybackViewModel` | UI 薄封装 |
| `PlaybackQueueSync` | 歌单分页缓存扩队列；播放页补全写回缓存 |
| `MvPlayback` | **独立** ExoPlayer。与歌曲互斥：`playbackBridge.musicWillPlay = { mvPlayback.stop() }`；MV 起播时 Bridge 绑定 MV 的 Player 到同一 MediaSession |

`PlaylistQueueHydrator` 是 Compose，放在 `ui/player`；`mergePlaylistQueue` 仍在 playback。

---

## 6. 数据层（`data/*`）

- HTTP：`NcmUserClient`、`NcmAuthClient`（container 单例）
- 解析：`NcmLibraryParse` / `NcmHomeParse` / `NcmArtistParse` / `NcmCommentParse` / `NcmMvParse`
- 会话：`SessionRepository`、`SessionWarmup`
- 仓库 / 缓存：`HomeFeedRepository`、`LikedPlaylistRepository`、`LibraryHomeRepository`、歌单/专辑曲目缓存、`LyricRepository`、收藏仓库、`SongRepository`、`CatalogRepository`、`CommentsRepository`、`SearchRepository`、`ArtistRepository`
- 偏好：音质、持续播放、液体玻璃、播放器显示（ARGB，不引用 Compose `Color`）、歌词悬浮窗

---

## 7. 核心播放数据流

1. UI `playQueue` → `PlaybackBridge`（无 Coordinator 则连接 MediaController 以启动 Service）
2. Service `onCreate` 创建 `PlaylistCoordinator`（注入共享 `NcmUserClient`）并 `bridge.attachCoordinator`
3. Coordinator `resolvePlayUrl`（`/song/url` → `/song/url/v1`）→ ExoPlayer `setMediaItem` / `prepare` / `play`
4. Media3 维护媒体通知与 mediaPlayback 前台服务
5. 结束前约 30s 预取下一首 URL（TTL ≤ 2min）；切歌仍以新鲜 resolve 为准
6. 歌词异步，不阻塞起播
7. Compose 订阅 `Bridge.ui`

---

## 8. 缓存

- 封面：`UrlImage` + `ArtworkLoader`，磁盘 `zmusic_image_cache`
- 歌词：Coordinator 内存 + `zmusic_lyrics` / 兼容旧目录
- 歌单 / 专辑曲目分页缓存；喜欢歌单快照；评论内存+磁盘缓存

---

## 9. UI/UX

- 竖屏 / 横屏播放器布局独立实现，交互语义一致
- 播放模式按钮仅在展开播放器
- 软件内反馈为顶部灵动岛胶囊；系统媒体通知仍由 Media3 负责
- 色板：`ui/theme/MainPalette`

---

## 10. 配置与环境

- 默认 API 基址：`app/build.gradle.kts` → `BuildConfig.NCM_API_BASE_URL`
- `local.properties` 可覆盖 `ncm.api.base.url`
- 应用内「服务器配置」可运行期覆盖并持久化
- Splash → 连通性探测 → 主流程
- 接口约定见 [`netease-new/`](./netease-new)

---

## 11. 播放模式（跨端语义）

| 枚举 | 含义 |
|------|------|
| `ORDER` | 列表循环 |
| `REPEAT_ONE` | 单曲循环 |
| `SHUFFLE` | 随机播放 |

循环切换：`ORDER` → `REPEAT_ONE` → `SHUFFLE` → `ORDER`。MV 有独立模式持久化，不复用歌曲模式。

---

## 12. 后续（本阶段不做）

- Gradle `:feature-*` / Hilt
- R8 minify
- 缓存 TTL / 版本策略
- 关键链路仪器测试（息屏连播、通知 play/pause 同步）
- 国产 ROM 后台白名单引导
