# ZMusic

多平台音乐客户端（当前阶段：**Android**）。接口形态与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 兼容；业务与网络层仍在迭代中。

## 仓库结构

| 目录 | 说明 |
|------|------|
| `Android/` | Android 应用（Kotlin、Jetpack Compose、Media3） |
| `Android/dev/` | 客户端实现进度与开发笔记（可选） |
| `docs/` | 离线接口说明与拆分文档（不含具体部署地址） |

## Android 运行环境

- Android Studio（建议最新稳定版）或兼容的 Gradle/JDK 环境  
- JDK 17（随 Android Gradle Plugin 要求）  
- 在 `Android/` 目录打开工程，同步 Gradle 后运行 `app`

首次构建前可在 `Android/` 下准备 `local.properties`，至少包含 SDK 路径，可参考 `Android/local.properties.example`。

## 动态配置 API 服务地址

默认基地址为 **`http://47.108.183.165:3000`**（编译进 `BuildConfig.NCM_API_BASE_URL`）。

在 **`Android/local.properties`**（该文件已被 Git 忽略）中加入一行即可覆盖，例如：

```properties
ncm.api.base.url=http://你的主机:端口
```

- 不要末尾斜杠。  
- 代码侧统一读取：`com.kite.zmusic.config.NcmApiConfig.baseUrl`（后续接入 Retrofit / OkHttp 时使用）。

## 文档与合规

- 接口字段说明见 `docs/README.md` 与 `docs/raw/home.md`。  
- 启动页字体 **Great Vibes**（SIL OFL），说明见 `Android/dev/notes/fonts.md`。

## 开源说明

更换或隐藏默认服务器地址时，只需改 `local.properties` 或调整 `app/build.gradle.kts` 中的默认值；**勿**将仅个人使用的 `local.properties` 提交到仓库。
