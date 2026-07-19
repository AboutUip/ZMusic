# ZMusic

> 面向**沉浸式播放**的音乐客户端；后端接口形态兼容 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)。  
> **Android** 与 **Windows** 为**相互独立**的工程与代码库，不要求 UI 或功能一一对应。

---

## 项目核心（双端共同把握）

无论哪一平台，实现上都应围绕同一条产品主线，避免散落成“杂功能集合”：

1. **播放体验**：以全屏/低干扰的收听流程为中心（布局与交互各端可自行设计）。
2. **播放状态**：队列、当前曲目、播放模式、进度与恢复要**可靠、可推理**（各端技术栈不同，但语义应对齐）。
3. **数据链路**：接口基址**可配置**；依赖 `docs/` 中离线 API 说明理解契约；封面/歌词等缓存策略应**可追踪、可维护**。

**Windows 开发可参考 Android**：不要求一致，但在**请求路径、Cookie/会话、解析字段、播放与缓存链路**上，`Android/` 里的 `data/*`、`playback/*` 与 `docs/ARCHITECTURE.md` 是已落地的对照实现，可用来减少踩坑。

---

## 为什么是这个项目

`ZMusic` 的目标不是“功能堆叠”，而是落实上节「项目核心」三件事，并做到顺滑、统一、可持续迭代。

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

### 3) 配置 API 地址

Android 端**默认固定**为 `http://47.110.72.65:3000`（见 `Android/app/build.gradle.kts`）。若需连本机或其它环境，可在 `Android/local.properties`（已 git ignore）覆盖：

```properties
ncm.api.base.url=http://你的主机:端口
```

注意：

- 不要带结尾 `/`
- 编译时会注入到 `BuildConfig.NCM_API_BASE_URL`
- 代码统一通过 `com.kite.zmusic.config.NcmApiConfig.baseUrl` 读取
- 本地调试示例：模拟器访问开发机可用 `http://10.0.2.2:3000`；真机请用电脑局域网 IP

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
| `Android/` | Android 客户端（Compose + Media3 + OkHttp），**可参考的完整业务实现** |
| `Android/app/src/main/java/com/kite/zmusic/ui` | UI：主壳、歌单、播放器、通用组件 |
| `Android/app/src/main/java/com/kite/zmusic/playback` | 播放状态与 ExoPlayer 协调 |
| `Android/app/src/main/java/com/kite/zmusic/data` | API 请求、解析、会话与数据模型 |
| `Windows/` | Windows 客户端（WinUI 3 + C++/WinRT + Windows App SDK），与 Android **独立演进** |
| `docs/` | 文档中心（架构说明、接口离线资料）；详见 `docs/README.md` |

---

## 常用开发命令

### Android

在 `Android/` 目录：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

### Windows

使用 **Visual Studio 2022** 打开 `Windows/ZMusic.sln`，还原 NuGet 后编译；输出与中间文件见 `.gitignore` 说明。

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

本项目以 [GNU General Public License v2.0](LICENSE)（GPL-2.0）发布，许可证全文见仓库根目录 `LICENSE`。
