# ZMusic Docs

仓库内技术资料的入口。各端代码相互独立，不要求功能或界面对齐。

## 阅读顺序

1. 根目录门户：[`../README.md`](../README.md)
2. Android 架构：[`ARCHITECTURE.md`](./ARCHITECTURE.md)
3. Android 应用内灵动岛：[`ANDROID-ISLAND-NOTICE.md`](./ANDROID-ISLAND-NOTICE.md)
4. Windows 架构：[`WINDOWS.md`](./WINDOWS.md)
5. Windows 安装包：[`WINDOWS-DISTRIBUTION.md`](./WINDOWS-DISTRIBUTION.md)
6. 插件规范（作者）：[`plugin-engine/`](./plugin-engine)
7. 插件引擎实现说明：[`plugin-engine/internal/`](./plugin-engine/internal)
8. 网易云兼容 API：[`netease-new/`](./netease-new)

## 索引

| 路径 | 说明 |
|---|---|
| [`../LICENSE`](../LICENSE) | GNU GPL v2.0 |
| [`../Android/`](../Android) | Android 客户端 |
| [`../Windows/`](../Windows) | Windows 客户端 |
| [`../Linux/`](../Linux) | Linux 占位 |
| [`../Distribution/Android/`](../Distribution/Android) | Android 发行脚本 |
| [`../Distribution/Windows/`](../Distribution/Windows) | Windows 安装包 |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Android 架构与主链路 |
| [`ANDROID-ISLAND-NOTICE.md`](./ANDROID-ISLAND-NOTICE.md) | 应用内灵动岛通知 |
| [`WINDOWS.md`](./WINDOWS.md) | Windows 架构 |
| [`WINDOWS-DISTRIBUTION.md`](./WINDOWS-DISTRIBUTION.md) | Windows 分发 |
| [`plugin-engine/`](./plugin-engine) | 插件作者规范（包格式、清单、Xuan API） |
| [`plugin-engine/internal/`](./plugin-engine/internal) | 客户端插件引擎实现说明 |
| [`netease-new/`](./netease-new) | 接口文档（Enhanced） |
| [`netease-new/INDEX.md`](./netease-new/INDEX.md) | 接口章节索引 |
| [`netease-new/SUMMARY.md`](./netease-new/SUMMARY.md) | 接口章节总表 |
| [`netease-new/API.md`](./netease-new/API.md) | 接口离线全文 |

## 约定

- 文档写可执行信息，避免空泛描述
- 代码目录或能力边界变更时，同步更新路径与「已实现」表
- 环境与基址写在各端配置与构建脚本中；**文档不写死服务地址**
- 不要写入 Cookie、账号口令或未公开的密钥路径

上游参考：[NeteaseCloudMusicApiEnhanced](https://github.com/neteasecloudmusicapienhanced/api-enhanced)
