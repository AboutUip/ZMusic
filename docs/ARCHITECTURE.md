# Android Architecture

本文描述 `ZMusic` Android 客户端当前可维护的主链路：从 UI 交互到播放内核，再到接口请求与缓存。

---

## 1. 技术栈

- UI: Jetpack Compose
- 播放: Media3 / ExoPlayer
- 网络: OkHttp
- 状态: `StateFlow` + `ViewModel`
- 安全存储: `EncryptedSharedPreferences`

---

## 2. 模块分层

## UI 层（`ui/*`）

- `ui/main`：主壳、导航、Dock、迷你播放器挂载
- `ui/library`：歌单列表与详情页
- `ui/player`：全屏播放器、歌词视图、控制条、播放模式按钮
- `ui/common`：公共组件（如 `UrlImage`）

## 状态与播放协调层（`playback/*`）

- `PlaybackViewModel` 负责：
  - 队列与索引
  - 播放状态（播放中、缓冲、时长、进度）
  - 播放模式（顺序/单曲循环/随机）
  - 歌词加载与缓存
  - 最近播放恢复

## 数据层（`data/*`）

- `NcmUserClient`：接口请求
- `NcmLibraryParse` / `NcmPlaybackParse`：JSON 解析
- `LrcParser`：歌词文本解析为时间轴行
- `SessionRepository`：会话状态与 cookie 来源

---

## 3. 核心播放数据流

1. UI 触发点播（歌单 or 单曲）
2. `PlaybackViewModel.playQueue()` 写入队列和起始索引
3. `loadAndPlayIndex()` 拉取播放链接
4. 歌词优先命中缓存，未命中再请求并落盘
5. ExoPlayer `setMediaItem` -> `prepare` -> `playWhenReady=true`
6. `Player.Listener` 回写播放状态到 `StateFlow`
7. Compose UI 订阅状态并重组

---

## 4. 缓存设计

## 封面缓存（`ui/common/UrlImage.kt`）

- 内存：`LruCache<String, ImageBitmap>`
- 磁盘：`cacheDir/zmusic_image_cache`
- 读取顺序：内存 -> 磁盘 -> 网络

## 歌词缓存（`playback/PlaybackViewModel.kt`）

- 内存：`LruCache<String, List<LrcLine>>`
- 磁盘：`cacheDir/zmusic_lyrics_cache`
- Key 组成：`songId + cookie hash`
- 读取顺序：缓存 -> 网络 -> 回写缓存

---

## 5. UI/UX 设计要点（当前版本）

- 竖屏：信息头 + 封面区 + 歌词预览 + 紧凑控制区
- 横屏：封面与歌词左右分区 + 底部密集控制条
- 全屏交互：弱化冗余按钮，保留手势和关键操作
- 播放模式按钮：仅在展开播放器显示，不在迷你播放器显示

---

## 6. 配置与环境

- API 地址通过 `local.properties` 注入：

```properties
ncm.api.base.url=http://你的主机:端口
```

- 构建注入字段：`BuildConfig.NCM_API_BASE_URL`
- 业务读取入口：`com.kite.zmusic.config.NcmApiConfig.baseUrl`

---

## 7. 后续建议

- 为缓存增加 TTL 与版本策略（避免长期陈旧）
- 补充关键链路测试（歌词缓存命中/失效、恢复播放）
- 为 UI 关键参数建立可调试配置（便于不同机型快速调优）
