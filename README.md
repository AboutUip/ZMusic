<div align="center">

# 🎧 ZMusic

**把世界调小一点。把歌，开大一点。**

一张封面 · 一行歌词 · 一整段属于你的时间

![License](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPL--2.0-4A90D9?style=flat-square)
![Android](https://img.shields.io/badge/Android-%E4%B8%BB%E5%8A%9B-3DDC84?style=flat-square)
![Windows](https://img.shields.io/badge/Windows-%E5%90%8C%E6%AD%A5%E6%8E%A8%E8%BF%9B-00A4EF?style=flat-square)
![Linux](https://img.shields.io/badge/Linux-%E5%B7%B2%E7%A1%AE%E5%AE%9A%20%C2%B7%20%E9%9D%9E%E8%BF%91%E6%9C%9F-FCC624?style=flat-square)

</div>

---

不是功能越多越好。  
ZMusic 只想把一件事做好——  
**让听歌，变得安静、顺滑、沉进去。**

少一点打扰，多一点沉浸。打开它，就是为了听。

## ✨ 特性

- **沉浸播放** —— 全屏播放器、歌词异步加载，起播不被阻塞；三种播放模式（列表循环 / 单曲循环 / 随机）
- **应用内灵动岛** —— 所有软件内提示统一为顶部液体玻璃胶囊（非 Toast / Snackbar），带封面与过冲动画，见 [`ANDROID-ISLAND-NOTICE.md`](./docs/ANDROID-ISLAND-NOTICE.md)
- **液体玻璃设计** —— Kyant Backdrop 真实折射玻璃，Dock、确认弹窗与灵动岛同窗采样
- **系统级播控** —— Media3 MediaSessionService 独占通知与前台服务，锁屏 / 通知栏可控
- **智能缓存** —— 封面磁盘缓存、歌词缓存、下一首 URL 短 TTL 预取
- **扫码与手机号登录** —— 与网易云兼容 API 打通，会话安全存储
- **跨端对齐** —— Android 与 Windows 两端独立实现，播放语义、鉴权与接口行为级一致

## 🛠️ 技术栈

| | Android（主力） | Windows（同步推进） |
|---|---|---|
| UI | Jetpack Compose | WPF · WPF-UI · Acrylic |
| 播放 | Media3 ExoPlayer + MediaSessionService | `System.Windows.Media.MediaPlayer` |
| 状态 | StateFlow + ViewModel + PlaybackBridge | CommunityToolkit.Mvvm |
| 网络 | OkHttp | HttpClient + System.Text.Json |
| 会话 | EncryptedSharedPreferences | DPAPI 本地文件 |

## 📦 各端状态

| 平台 | 状态 | 说明 |
|:---|:---|:---|
| [Android](./Android) | ✅ 主力 | 完整主链路：点播、歌单、歌词、灵动岛、系统通知 |
| [Windows](./Windows) | 🚧 推进中 | Splash / 扫码登录 / 喜欢歌单 / 迷你条已实现；推荐页等仍在完善 |
| [Linux](./Linux) | 📅 远期占位 | 计划中，非近期 |

## 🚀 快速开始

```bash
# Android（需要 Android Studio / JDK）
cd Android
./gradlew assembleDebug          # Windows 下用 gradlew.bat
```

```bash
# Windows 客户端
dotnet run --project Windows/ZMusic.csproj
```

API 基址默认内置；本地调试可覆盖（文档不写死服务地址）：

```properties
# Android/local.properties
ncm.api.base.url=http://127.0.0.1:3000
```

```powershell
# Windows（PowerShell）
$env:ZMUSIC_NCM_API_BASE_URL = "http://127.0.0.1:3000"
```

## 🗂️ 仓库结构

```
ZMusic/
├── Android/          Android 客户端（Compose + Media3）
├── Windows/          Windows 客户端（WPF · .NET 9）
├── Linux/            Linux 占位（远期）
├── Distribution/     发行脚本与安装包工程
├── docs/             架构、分发与接口文档
└── artifacts/        构建产物（已 gitignore）
```

## 📚 文档

| 文档 | 内容 |
|:---|:---|
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Android 架构与主链路 |
| [`docs/ANDROID-ISLAND-NOTICE.md`](./docs/ANDROID-ISLAND-NOTICE.md) | 应用内灵动岛通知设计 |
| [`docs/WINDOWS.md`](./docs/WINDOWS.md) | Windows 架构与已实现能力 |
| [`docs/WINDOWS-DISTRIBUTION.md`](./docs/WINDOWS-DISTRIBUTION.md) | Windows 安装包分发 |
| [`docs/netease-new/`](./docs/netease-new) | 网易云兼容 API 接口文档 |

细节一律以 [`docs/`](./docs) 为准。

## 📜 许可

本项目以 [GNU GPL v2.0](./LICENSE) 开源，以温柔的方式存在。

---

<div align="center">

<sub>少一点打扰，多一点沉浸 · <a href="./LICENSE">GPL-2.0</a></sub>

</div>
