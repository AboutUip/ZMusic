# Android Architecture

本文描述 `ZMusic` **Android** 客户端当前可维护的主链路：从 UI 交互到播放内核，再到接口请求与缓存。

**与 Windows 的关系**：Windows 客户端为**独立工程**（`Windows/`），实现方式不必与本文一致。若需对齐**项目核心**（播放状态语义、接口与缓存链路），可将下文中的 **数据层、播放协调层、缓存与配置** 当作**行为级参考**；具体类名与 UI 仅适用于 Android。

---

## 1. 技术栈

- UI: Jetpack Compose
- 播放: Media3 ExoPlayer + **MediaSessionService**（通知 / 前台服务由 Media3 独占）
- UI 控制: `MediaController` 拉起 Service；业务命令经进程内 `PlaybackBridge`
- 网络: OkHttp
- 状态: `StateFlow` + ViewModel + Bridge
- 安全存储: `EncryptedSharedPreferences`（会话）；播放队列快照用普通 `SharedPreferences`

---

## 2. 模块分层

## UI 层（`ui/*`）

- `ui/main`：主壳、Dock、迷你条；点播前请求 `POST_NOTIFICATIONS`（拒绝仍可播，并提示后台可能受限）
- `ui/library` / `ui/player`：歌单与全屏播放器
- `ui/common`：`UrlImage` 等

## 播放层（`playback/*`）

| 组件 | 职责 |
|------|------|
| `PlaybackService` | 薄 `MediaSessionService`：持有 Session + Coordinator；**不**手写 `startForeground` |
| `PlaylistCoordinator` | 队列、NCM URL **按需解析**、歌词、模式、短 TTL 预取 |
| `PlaybackBridge` | Application 单例：MediaController 启服、注册 Coordinator、UI `StateFlow`；**禁止空队列冲掉迷你条快照** |
| `PlaybackStateStore` | 唯一队列持久化（恢复迷你条） |
| `PlaybackViewModel` | UI 薄封装 |

## 数据层（`data/*`）

- `NcmUserClient` / parsers / `SessionRepository`（Application 单例）

---

## 3. 核心播放数据流

1. UI `playQueue` → `PlaybackBridge`（若无 Coordinator 则 `MediaController` 连接以启动 Service）
2. Service `onCreate` 创建 `PlaylistCoordinator` 并 `bridge.attachCoordinator`
3. Coordinator `resolvePlayUrl`（`/song/url` → `/song/url/v1`）→ ExoPlayer `setMediaItem` / `prepare` / `play`
4. Media3 根据 Player 状态自动维护媒体通知与 mediaPlayback 前台服务
5. 结束前约 30s 预取下一首 URL（TTL ≤ 2min）；切歌仍以新鲜 resolve 为准
6. 歌词异步，不阻塞起播
7. Compose 订阅 `Bridge.ui`

---

## 4. 缓存设计

- 封面：`UrlImage` + `ArtworkLoader`（通知），磁盘目录 `zmusic_image_cache`
- 歌词：Coordinator 内存 + `zmusic_lyrics_cache`

---

## 5. UI/UX 要点

- 竖屏 / 横屏播放器布局不变
- 播放模式按钮仅在展开播放器

---

## 6. 配置与环境

- 默认 API：`http://47.110.72.65:3000`；`local.properties` 可覆盖 `ncm.api.base.url`
- Splash → 连通性探测 → 主流程

---

## 7. 后续建议

- 缓存 TTL / 版本策略
- 关键链路仪器测试（息屏连播、通知 play/pause 同步）
- 国产 ROM 后台白名单引导（产品决策后再做）
