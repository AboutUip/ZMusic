# ZMusic 插件规范

本文档面向 **插件作者**：说明如何构造 `.zpp` 包，以及入口脚本可使用的 **Xuan** API。

ZMusic 提供插件的包装、安装与运行。插件内容与行为由提供者负责；是否安装、是否启用由用户承担。ZMusic 不审核插件，也不对插件作安全保证。

| | |
|---|---|
| 包格式（`zpp`） | `1` |
| 引擎版本 | `0.0.2`（整数 `2`） |
| 当前宿主 API | 版本只读、运行状态注册、灵动岛通知、同步延迟、文本主题（见 [运行时](./RUNTIME.md)） |
| `capabilities` | 省略、`[]`，或仅含已知项（当前：`theme`） |

跨端同一套规范。客户端如何实现引擎，见 [internal/](./internal/)。

## 文档

| 文档 | 内容 |
|---|---|
| [PACKAGE.md](./PACKAGE.md) | `.zpp` 包结构、必选文件、允许的资源类型 |
| [MANIFEST.md](./MANIFEST.md) | `plugin.json` |
| [VERSIONING.md](./VERSIONING.md) | 引擎版本与插件版本的编码 |
| [RUNTIME.md](./RUNTIME.md) | `Xuan` 运行时 API |
| [THEME.md](./THEME.md) | 可选能力 `theme`：文本色 token 与 `Xuan.theme` |
| [SIGNING.md](./SIGNING.md) | 签名字段与未签名包 |
| [TOOLKIT.md](./TOOLKIT.md) | 官方打包命令 |

## 最短路径

1. 按 [PACKAGE.md](./PACKAGE.md) 准备文件。
2. 填写 [MANIFEST.md](./MANIFEST.md) 中的 `plugin.json`。
3. 在入口脚本中使用 [RUNTIME.md](./RUNTIME.md)：先 `Initializing`，再 `Running`。
4. 用 [TOOLKIT.md](./TOOLKIT.md) 打出 `.zpp`。
