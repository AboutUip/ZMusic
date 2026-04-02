# ZMusic

> 一个面向沉浸式播放体验的音乐客户端（当前主线：`Android`）。
> UI 采用 Jetpack Compose，播放内核基于 Media3（ExoPlayer），接口形态兼容 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)。

---

## 为什么是这个项目

`ZMusic` 的目标不是“功能堆叠”，而是把以下三件事做到顺滑、统一、可持续迭代：

- 全屏播放体验（横竖屏差异化布局 + 低干扰交互）
- 轻量但可靠的播放状态管理（队列、模式、恢复）
- 可控的数据链路（接口地址可配置、文档离线化、缓存可追踪）

---

## 5 分钟上手（新同学必看）

### 1) 环境要求

- Android Studio（建议最新稳定版）
- JDK 17（构建工具要求）
- Android SDK（`compileSdk=36`, `minSdk=29`, `targetSdk=36`）

### 2) 打开并运行

1. 用 Android Studio 打开 `Android/`
2. 等待 Gradle Sync 完成
3. 运行 `app` 模块

### 3) 配置 API 地址（推荐先做）

在 `Android/local.properties`（已 git ignore）加入：

```properties
ncm.api.base.url=http://你的主机:端口
```

注意：

- 不要带结尾 `/`
- 编译时会注入到 `BuildConfig.NCM_API_BASE_URL`
- 代码统一通过 `com.kite.zmusic.config.NcmApiConfig.baseUrl` 读取

---

## 当前功能快照（Android）

- 沉浸式播放器（横竖屏分别优化布局）
- 迷你播放器常驻主界面
- 播放模式：顺序 / 单曲循环 / 随机
- 最近播放歌曲恢复（应用重启后可快速继续）
- 歌词预览 + 全屏歌词
- 横竖屏手势退出与系统栏联动策略

---

## 缓存策略（已接入）

### 封面缓存

- 位置：`ui/common/UrlImage.kt`
- 机制：内存 LRU + 磁盘缓存（`cacheDir/zmusic_image_cache`）
- 读取顺序：内存 -> 磁盘 -> 网络

### 歌词缓存

- 位置：`playback/PlaybackViewModel.kt`
- 机制：内存 LRU + 磁盘缓存（`cacheDir/zmusic_lyrics_cache`）
- 读取顺序：缓存命中直接使用，未命中再请求接口并回写缓存

---

## 项目结构

| 路径 | 说明 |
|---|---|
| `Android/` | Android 客户端工程（Compose + Media3 + OkHttp） |
| `Android/app/src/main/java/com/kite/zmusic/ui` | UI 层：主壳、歌单、播放器、通用组件 |
| `Android/app/src/main/java/com/kite/zmusic/playback` | 播放状态与 ExoPlayer 协调 |
| `Android/app/src/main/java/com/kite/zmusic/data` | API 请求、解析、会话与数据模型 |
| `docs/` | 文档中心（入门、架构、接口离线说明） |

---

## 常用开发命令

在 `Android/` 目录：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

---

## 文档导航

- 文档入口：`docs/README.md`
- 架构与模块说明：`docs/ARCHITECTURE.md`
- 网易云兼容 API 离线索引：`docs/raw/home.md`

---

## 协作约定（提交前）

- 不提交个人环境配置（如 `local.properties`）
- 不把临时调试地址硬编码进仓库
- UI 调整尽量横竖屏都验证一遍
- 提交信息建议包含：变更范围 + 用户可感知效果

---

## License

如需开源发布，建议在仓库根目录补充明确的 `LICENSE` 文件并在此处声明。
