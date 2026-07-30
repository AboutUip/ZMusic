# ZMusic Docs

文档目录用于承载可随仓库分发的技术资料，目标是：

- 新同学可快速上手
- 迭代信息可追踪
- API 说明可离线查阅

**平台关系（文档约定）**：`Android/` 与 `Windows/` **代码相互独立**，不要求功能或界面对齐。  
**项目核心**（播放体验、播放状态、可配置接口与可维护缓存）见根目录 [`../README.md`](../README.md) 中「项目核心」一节。  
**互参**：实现网易云兼容 API、会话与解析、播放与缓存时，以各端 `data` / `playback` 与本文档架构篇为对照，而非强制复刻 UI。

---

## 阅读顺序（推荐）

1. 根入门与核心约定：[`../README.md`](../README.md)
2. Android 架构与主链路：[`ARCHITECTURE.md`](./ARCHITECTURE.md)
3. Windows 架构与已实现能力：[`WINDOWS.md`](./WINDOWS.md)
4. Windows 安装包分发：[`WINDOWS-DISTRIBUTION.md`](./WINDOWS-DISTRIBUTION.md)
5. 接口契约离线全文：[`raw/home.md`](./raw/home.md)

---

## 文档索引

| 路径 | 说明 |
|---|---|
| [`../LICENSE`](../LICENSE) | 项目许可证（GNU GPL v2.0） |
| [`../Android/`](../Android) | Android 客户端（Compose · Media3） |
| [`../Windows/`](../Windows) | Windows 客户端（WPF · .NET 9 · WPF-UI） |
| [`../Distribution/Windows/`](../Distribution/Windows) | Windows 安装包（Setup / MSI / Uninstall） |
| [`../Distribution/Android/`](../Distribution/Android) | Android 发行产物脚本（输出到 `artifacts/android/`） |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Android 端架构、核心模块与数据流 |
| [`WINDOWS.md`](./WINDOWS.md) | Windows 端架构、启动流、播放与目录 |
| [`WINDOWS-DISTRIBUTION.md`](./WINDOWS-DISTRIBUTION.md) | Windows 分发：Setup.exe + 静默 MSI / 卸载 / 签名 |
| [`raw/home.md`](./raw/home.md) | 网易云兼容 API 文档离线全文 |
| [`netease/netease-api/INDEX.md`](./netease/netease-api/INDEX.md) | API 章节索引（分篇入口） |
| [`netease/netease-api/endpoints/*.md`](./netease/netease-api/endpoints) | API 细分端点文档 |
| [`netease/netease-api/SUMMARY.md`](./netease/netease-api/SUMMARY.md) | 功能特性编号列表 |

---

## 维护约定

- 文档以「可执行信息」为主，避免空泛描述
- 代码目录或能力边界变更时，同步更新路径与「已实现」表
- 涉及环境配置（如服务地址）优先写在根 `README.md` 与各端架构文
- **不要**在文档中写入私有 Cookie、账号口令或未公开的密钥路径内容
- API 默认基址以各端代码 / 构建脚本为准；示例中的历史地址若已废弃，以当前默认 `http://120.27.244.170:3000` 为准

---

上游参考：  
[NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)
