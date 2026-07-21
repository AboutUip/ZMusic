<div align="center">

# ZMusic

**把世界调小一点。把歌，开大一点。**

一张封面 · 一行歌词 · 一整段属于你的时间

![Platform](https://img.shields.io/badge/%E5%B9%B3%E5%8F%B0-Android%20%C2%B7%20Windows-7C4DFF?style=flat-square)
![License](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPL--2.0-4A90D9?style=flat-square)
![Android](https://img.shields.io/badge/Android-Jetpack%20Compose%20%C2%B7%20Media3-3DDC84?style=flat-square)
![Windows](https://img.shields.io/badge/Windows-WinUI%203%20%C2%B7%20C%2B%2B-00A4EF?style=flat-square)

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

两端代码各自独立实现，但都遵从同一套语义。

## 双端实现

| 平台 | 技术栈 | 入口 |
|:---:|:---|:---|
| **Android** | Jetpack Compose · Media3 ExoPlayer · MediaSessionService · OkHttp | [`Android/`](./Android) |
| **Windows** | WinUI 3 · C++/WinRT · Windows App SDK | [`Windows/`](./Windows) |

两端代码相互独立，不要求功能或界面对齐；仅在播放状态、接口与缓存链路上保持行为级一致。

## 文档导航

| 路径 | 内容 |
|:---|:---|
| [`docs/README.md`](./docs/README.md) | 文档总入口与推荐阅读顺序 |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Android 端架构、核心模块与数据流（亦作 Windows 侧逻辑参考） |
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

<sub>以温柔的方式存在 · <a href="./LICENSE">GPL-2.0</a></sub>

</div>
