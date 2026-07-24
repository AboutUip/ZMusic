<div align="center">

# ZMusic

**把世界调小一点。把歌，开大一点。**

一张封面 · 一行歌词 · 一整段属于你的时间

![License](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPL--2.0-4A90D9?style=flat-square)
![Android](https://img.shields.io/badge/Android-%E4%B8%BB%E5%8A%9B%E6%8E%A8%E8%BF%9B-3DDC84?style=flat-square)
![Windows](https://img.shields.io/badge/Windows-%E5%80%99%E9%80%89%E5%90%8C%E6%AD%A5-00A4EF?style=flat-square)
![Linux](https://img.shields.io/badge/Linux-%E5%B7%B2%E7%A1%AE%E5%AE%9A%20%C2%B7%20%E9%9D%9E%E8%BF%91%E6%9C%9F-FCC624?style=flat-square)

</div>

---

## 为什么是它

不是功能越多越好。  
ZMusic 只想把一件事做好——  
**让听歌，变得安静、顺滑、沉进去。**

少一点打扰，多一点沉浸。打开它，就是为了听。

## 你会喜欢的地方

|  |  |
|:---:|:---|
| **全屏播放** | 封面铺满视线，歌词轻轻跟上，世界只剩这一首歌 |
| **随时继续** | 关掉再打开，上一首还在等你 |
| **随心循环** | 顺序听、单曲循环、随机跳转，怎么舒服怎么来 |
| **歌词同行** | 迷你预览也好，全屏沉浸也好，字都跟着你走 |
| **横竖都好** | 竖着刷歌，横着躺听，布局都为这一刻准备过 |

## 项目核心

ZMusic 的内核不在 UI，而在四件稳定的事——

| 关键词 | 含义 |
|:---:|:---|
| **播放体验** | 起播顺滑、通知与前台服务由系统托管，切歌无断层 |
| **播放状态** | 队列快照可恢复，迷你条不会被空队列冲掉 |
| **可配置接口** | API 地址可经 `local.properties` 覆盖，默认云端可达 |
| **可维护缓存** | 封面、歌词独立缓存，URL 按 TTL 预取与刷新 |

各端代码独立实现，但都遵从同一套语义。

## 平台路线图

| 平台 | 状态 | 说明 |
|:---:|:---|:---|
| **Android** | **主力推进中** | 当前开发重心；功能与体验以 Android 为准绳迭代 |
| **Windows** | **候选同步推进** | 已列入同步推进名单；工程骨架就绪，可按节奏与 Android 对齐核心能力 |
| **Linux** | **已确定 · 非近期** | 未来会实现；近期不纳入排期，目录预留占位 |

各端互不绑定发版节奏；不要求功能或界面逐一对齐，仅在播放状态、接口与缓存链路上保持行为级一致。

## 各端实现

| 平台 | 技术栈 | 入口 | 现状 |
|:---:|:---|:---|:---|
| **Android** | Jetpack Compose · Media3 ExoPlayer · MediaSessionService · OkHttp | [`Android/`](./Android) | 主力开发 |
| **Windows** | WPF · .NET 9 · WPF-UI · CommunityToolkit.Mvvm | [`Windows/`](./Windows) | 候选同步；脚手架已就位 |
| **Linux** | 待定 | [`Linux/`](./Linux) | 远期规划；仅占位 |

Windows / Linux 实现网易云兼容 API、会话、播放与缓存时，可参考 Android 的数据层与播放协调层，以及 [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)；不必复刻 UI。

## 快速开始（Android）

1. 用 Android Studio 打开 [`Android/`](./Android)
2. 按需复制 `Android/local.properties.example` → `local.properties`，配置 SDK 与可选的 `ncm.api.base.url`
3. 同步 Gradle 后运行 `app` 模块

> API 基址可在编译期通过 `local.properties` 覆盖；运行期也可在 App 内「服务器配置」修改并持久化。请勿将含私有地址或密钥的 `local.properties` 提交到仓库。

## 文档导航

| 路径 | 内容 |
|:---|:---|
| [`docs/README.md`](./docs/README.md) | 文档总入口与推荐阅读顺序 |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Android 端架构、核心模块与数据流（亦作其他端逻辑参考） |
| [`docs/raw/home.md`](./docs/raw/home.md) | 网易云兼容 API 文档离线全文 |
| [`docs/netease/`](./docs/netease) | API 端点细分文档与功能特性索引 |

## 它适合谁

想安静听一会儿歌的人。  
想把播放器当成「背景」而不是「任务」的人。  
不想被一堆按钮打扰的人。

如果你只是想——  
**点开，按下，然后把自己交给音乐。**  
那它大概就是为你准备的。

---

<div align="center">

<sub>以温柔的方式存在 · <a href="./LICENSE">GPL-2.0</a></sub>

</div>
