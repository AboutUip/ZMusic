# ZMusic Docs

文档目录用于承载可随仓库分发的技术资料，目标是：

- 新同学可快速上手
- 迭代信息可追踪
- API 说明可离线查阅

**平台关系（文档约定）**：`Android/` 与 `Windows/` **代码相互独立**，不要求功能或界面对齐。  
**项目核心**（播放体验、播放状态、可配置接口与可维护缓存）见根目录 `../README.md` 中「项目核心」一节。  
**Windows 可参考 Android**：实现网易云兼容 API 的调用方式、会话与解析、播放与缓存思路时，以 `../Android/.../data`、`playback` 与本文档 `ARCHITECTURE.md` 为对照，而非强制复刻 UI。

---

## 阅读顺序（推荐）

1. 根入门与核心约定：`../README.md`（含双端说明）
2. Android 架构与主链路（**亦作 Windows 侧逻辑参考**）：`ARCHITECTURE.md`
3. 接口契约离线全文：`raw/home.md`

---

## 文档索引

| 路径 | 说明 |
|---|---|
| `../LICENSE` | 项目许可证（GNU GPL v2.0） |
| `../Windows/` | Windows 客户端工程（WinUI 3 / C++/WinRT） |
| `ARCHITECTURE.md` | Android 端架构、核心模块与数据流（**可参考，非要求与 Windows 一致**） |
| `raw/home.md` | 网易云兼容 API 文档离线全文 |
| `netease/netease-api/INDEX.md` | API 章节索引（分篇入口） |
| `netease/netease-api/endpoints/*.md` | API 细分端点文档 |
| `netease/netease-api/SUMMARY.md` | 功能特性编号列表 |

---

## 维护约定

- 文档以“可执行信息”为主，避免空泛描述
- 代码目录变更时，同步更新路径引用
- 涉及环境配置（如服务地址）优先放在根 `README.md`
- 不在文档中写入私有服务地址、Cookie 或账号信息

---

上游参考：  
[NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)
